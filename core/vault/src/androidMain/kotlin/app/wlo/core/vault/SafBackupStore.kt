package app.wlo.core.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * The SAF destination (F13 §3: "Android SAF folder chosen once"). Uses
 * DocumentsContract directly — no DocumentFile dependency; the tree uri is
 * the persisted `content://…/tree/…` string from
 * ACTION_OPEN_DOCUMENT_TREE + takePersistableUriPermission (PART B owns the
 * picker UI; the pipeline only consumes the string).
 *
 * MediaStore never learns about backup files: they live in the USER-chosen
 * tree (typically Documents/), written through the documents provider — no
 * media location is ever registered (same invariant as the vault partitions).
 */
public class SafBackupStore(
    context: Context,
    treeUriString: String,
) : BackupStore {
    private val appContext = context.applicationContext
    private val treeUri: Uri = Uri.parse(treeUriString)

    /** The tree's root document (children of the user-chosen folder). */
    private fun treeDocument(): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    override suspend fun list(): List<BackupFileRef> {
        val resolver = appContext.contentResolver
        // The CHILD-DOCUMENTS uri of the tree root — querying the parent
        // document uri itself returns the folder, not its contents.
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val children =
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            ) ?: return emptyList()
        children.use { cursor ->
            val out = mutableListOf<BackupFileRef>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (!RotationPolicy.isBackupFile(name)) continue
                out +=
                    BackupFileRef(
                        name = name,
                        handle = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0)).toString(),
                        sizeBytes = cursor.getLong(2),
                    )
            }
            return out.sortedBy { it.name }
        }
    }

    override suspend fun write(
        name: String,
        bytes: ByteArray,
    ): BackupFileRef {
        val resolver = appContext.contentResolver
        // Same-name supersede (a second backup today replaces the first's
        // rotation slot deterministically instead of provider " (1)" dupes).
        list().firstOrNull { it.name == name }?.let { existing ->
            existing.handle?.let { handle ->
                runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(handle)) }
            }
        }
        val docUri =
            DocumentsContract.createDocument(
                resolver,
                treeDocument(),
                CONTENT_TYPE,
                name,
            ) ?: throw BackupStoreException("SAF refused to create $name in $treeUri")
        resolver.openOutputStream(docUri, "w")?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw BackupStoreException("SAF refused output stream for $name")
        return BackupFileRef(name = name, handle = docUri.toString(), sizeBytes = bytes.size.toLong())
    }

    override suspend fun read(ref: BackupFileRef): ByteArray {
        val uri = ref.handle?.let(Uri::parse) ?: findHandle(ref.name) ?: throw BackupStoreException("missing backup file ${ref.name}")
        val resolver = appContext.contentResolver
        resolver.openInputStream(uri)?.use { stream -> return stream.readBytes() }
        throw BackupStoreException("SAF refused input stream for ${ref.name}")
    }

    override suspend fun delete(ref: BackupFileRef) {
        val uri = ref.handle?.let(Uri::parse) ?: findHandle(ref.name) ?: return
        runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
    }

    private suspend fun findHandle(name: String): Uri? = list().firstOrNull { it.name == name }?.handle?.let(Uri::parse)

    private companion object {
        /** application/octet-stream keeps sync/cloud providers from re-typing it. */
        const val CONTENT_TYPE: String = "application/octet-stream"
    }
}
