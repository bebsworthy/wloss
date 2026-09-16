package app.wlo.buildlogic.arch

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * D10 / WLO-0058 — pure validation for WLO's fail-closed Android backup policy.
 *
 * `allowBackup=false` alone is insufficient on Android 12+ because some device
 * manufacturers still perform device-to-device transfer. Both generations of
 * extraction rules therefore exclude every Android backup storage domain.
 */
public object BackupPolicyValidator {
    private const val ANDROID_NS: String = "http://schemas.android.com/apk/res/android"

    public val STORAGE_DOMAINS: Set<String> =
        setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )

    public fun mergedManifestViolations(xml: String): List<String> =
        parse("merged manifest", xml) { root ->
            val application = root.getElementsByTagName("application").item(0) as? Element
                ?: return@parse listOf("has no <application> element")
            buildList {
                expectAndroidAttribute(application, "allowBackup", "false", this)
                expectAndroidAttribute(application, "fullBackupContent", "@xml/backup_rules", this)
                expectAndroidAttribute(application, "dataExtractionRules", "@xml/data_extraction_rules", this)
                if (!xml.contains("android.permission.INTERNET")) {
                    add("does not declare android.permission.INTERNET (D4/R-S13)")
                }
            }
        }

    public fun legacyRulesViolations(xml: String): List<String> =
        parse("legacy backup rules", xml) { root ->
            if (root.tagName != "full-backup-content") {
                listOf("root must be <full-backup-content>, was <${root.tagName}>")
            } else {
                exclusionViolations(root, "full-backup-content")
            }
        }

    public fun extractionRulesViolations(xml: String): List<String> =
        parse("data extraction rules", xml) { root ->
            if (root.tagName != "data-extraction-rules") {
                return@parse listOf("root must be <data-extraction-rules>, was <${root.tagName}>")
            }
            buildList {
                for (sectionName in listOf("cloud-backup", "device-transfer")) {
                    val sections = root.directChildren(sectionName)
                    if (sections.size != 1) {
                        add("must contain exactly one <$sectionName> section")
                    } else {
                        addAll(exclusionViolations(sections.single(), sectionName))
                    }
                }
            }
        }

    private fun exclusionViolations(section: Element, label: String): List<String> {
        val includes = section.directChildren("include")
        val excludedDomains =
            section.directChildren("exclude")
                .filter { it.getAttribute("path") == "." }
                .map { it.getAttribute("domain") }
                .toSet()
        return buildList {
            if (includes.isNotEmpty()) add("<$label> must not contain <include> rules")
            val missing = STORAGE_DOMAINS - excludedDomains
            if (missing.isNotEmpty()) {
                add("<$label> does not exclude path '.' for: ${missing.sorted().joinToString()}")
            }
        }
    }

    private fun expectAndroidAttribute(
        element: Element,
        name: String,
        expected: String,
        violations: MutableList<String>,
    ) {
        val actual = element.getAttributeNS(ANDROID_NS, name)
        if (actual != expected) {
            violations += "application android:$name must be '$expected', was '${actual.ifEmpty { "<missing>" }}'"
        }
    }

    private fun Element.directChildren(tagName: String): List<Element> =
        buildList {
            val nodes = childNodes
            for (index in 0 until nodes.length) {
                val child = nodes.item(index)
                if (child is Element && child.tagName == tagName) add(child)
            }
        }

    private inline fun parse(
        label: String,
        xml: String,
        validate: (Element) -> List<String>,
    ): List<String> =
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
            validate(root)
        } catch (error: Exception) {
            listOf("$label is not valid, safely parseable XML: ${error.message}")
        }
}
