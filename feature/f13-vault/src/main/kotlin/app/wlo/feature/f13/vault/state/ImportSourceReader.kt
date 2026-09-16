package app.wlo.feature.f13.vault.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

public data class StagedImportSource(
    public val path: String,
    public val fileName: String,
    public val sizeBytes: Long,
)

public class ImportSourceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Bounded, app-private import staging. Raw source bytes never enter logs. */
public interface ImportSourceReader {
    public suspend fun stage(uri: String): StagedImportSource

    public suspend fun load(path: String): ByteArray

    public suspend fun discard(path: String)
}

public class AndroidImportSourceReader(
    context: Context,
) : ImportSourceReader {
    private val appContext = context.applicationContext
    private val stagingDirectory = File(appContext.cacheDir, "wlo-import-staging")

    @Suppress("TooGenericExceptionCaught")
    override suspend fun stage(uri: String): StagedImportSource =
        withContext(Dispatchers.IO) {
            cleanupExpired()
            val parsed = Uri.parse(uri)
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    parsed,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val (name, declaredSize) = queryMetadata(parsed)
            require(declaredSize == null || declaredSize <= MAX_BYTES) {
                "This file is larger than 20 MiB. Split it into smaller files and import them separately."
            }
            stagingDirectory.mkdirs()
            val staged = File(stagingDirectory, "${System.currentTimeMillis()}-${name.hashCode().toUInt()}.stage")
            try {
                appContext.contentResolver.openInputStream(parsed)?.use { input ->
                    FileOutputStream(staged).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_BYTES) { FILE_TOO_LARGE }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("The selected file could not be opened. Choose it again or select another file.")
                StagedImportSource(staged.absolutePath, name, staged.length())
            } catch (cancellation: CancellationException) {
                staged.delete()
                throw cancellation
            } catch (failure: Exception) {
                staged.delete()
                throw ImportSourceException(failure.message ?: "The selected file could not be read.", failure)
            }
        }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(path: String): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                val source = validatedStage(path)
                require(source.length() <= MAX_BYTES) { "The staged file exceeds the 20 MiB import limit." }
                source.readBytes()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                throw ImportSourceException(failure.message ?: "The staged file could not be read.", failure)
            }
        }

    override suspend fun discard(path: String) {
        withContext(Dispatchers.IO) { runCatching { validatedStage(path).delete() } }
    }

    private fun queryMetadata(uri: Uri): Pair<String, Long?> {
        var name = "import"
        var size: Long? = null
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return name.substringAfterLast('/').take(160) to size
    }

    private fun validatedStage(path: String): File {
        val root = stagingDirectory.canonicalFile
        val file = File(path).canonicalFile
        require(file.parentFile == root && file.isFile) {
            "The staged import is no longer available. Choose the file again."
        }
        return file
    }

    private fun cleanupExpired() {
        val cutoff = System.currentTimeMillis() - SESSION_MAX_AGE_MS
        stagingDirectory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
    }

    private companion object {
        const val MAX_BYTES: Long = 20L * 1024 * 1024
        const val SESSION_MAX_AGE_MS: Long = 7L * 24 * 60 * 60 * 1000
        const val FILE_TOO_LARGE: String =
            "This file is larger than 20 MiB. Split it into smaller files and import them separately."
    }
}
