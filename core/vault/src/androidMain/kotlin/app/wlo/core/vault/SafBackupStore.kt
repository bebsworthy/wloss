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
    private fun treeDocument(): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

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
                val name = cursor.getString(1)
                if (RotationPolicy.isBackupFile(name)) {
                    out +=
                        BackupFileRef(
                            name = name,
                            handle =
                                DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                                    .toString(),
                            sizeBytes = cursor.getLong(2),
                        )
                }
            }
            return out.sortedBy { it.name }
        }
    }

    override suspend fun write(
        name: String,
        bytes: ByteArray,
    ): BackupFileRef {
        val resolver = appContext.contentResolver
        val existing = list().firstOrNull { it.name == name }
        val written =
            VerifiedReplacementWriter(
                object : ReplacementBackend {
                    override suspend fun create(requestedName: String): ReplacementDocument {
                        val uri =
                            DocumentsContract.createDocument(
                                resolver,
                                treeDocument(),
                                CONTENT_TYPE,
                                requestedName,
                            ) ?: throw BackupStoreException("SAF refused to create $requestedName in $treeUri")
                        return ReplacementDocument(uri.toString(), queryDisplayName(uri) ?: requestedName)
                    }

                    override suspend fun write(
                        handle: String,
                        bytes: ByteArray,
                    ) {
                        resolver.openOutputStream(Uri.parse(handle), "w")?.use { stream ->
                            stream.write(bytes)
                            stream.flush()
                        } ?: throw BackupStoreException("SAF refused output stream for $name")
                    }

                    override suspend fun read(handle: String): ByteArray =
                        resolver.openInputStream(Uri.parse(handle))?.use { it.readBytes() }
                            ?: throw BackupStoreException("SAF refused verification stream for $name")

                    override suspend fun delete(handle: String): Boolean =
                        DocumentsContract.deleteDocument(
                            resolver,
                            Uri.parse(handle),
                        )

                    override suspend fun rename(
                        handle: String,
                        requestedName: String,
                    ): ReplacementDocument? =
                        DocumentsContract.renameDocument(resolver, Uri.parse(handle), requestedName)?.let { uri ->
                            ReplacementDocument(uri.toString(), queryDisplayName(uri) ?: requestedName)
                        }
                },
            ).replace(name, existing?.handle, bytes)
        return BackupFileRef(name = written.name, handle = written.handle, sizeBytes = bytes.size.toLong())
    }

    override suspend fun read(ref: BackupFileRef): ByteArray {
        val uri =
            ref.handle?.let(Uri::parse)
                ?: findHandle(ref.name)
                ?: throw BackupStoreException("missing backup file ${ref.name}")
        val resolver = appContext.contentResolver
        resolver.openInputStream(uri)?.use { stream -> return stream.readBytes() }
        throw BackupStoreException("SAF refused input stream for ${ref.name}")
    }

    override suspend fun delete(ref: BackupFileRef) {
        val uri = ref.handle?.let(Uri::parse) ?: findHandle(ref.name) ?: return
        runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
    }

    private suspend fun findHandle(name: String): Uri? = list().firstOrNull { it.name == name }?.handle?.let(Uri::parse)

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver
            .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private companion object {
        /** application/octet-stream keeps sync/cloud providers from re-typing it. */
        const val CONTENT_TYPE: String = "application/octet-stream"
    }
}
