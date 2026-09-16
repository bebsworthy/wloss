package app.wlo.feature.f06.weight.state

import app.wlo.core.data.DeletedWeighIn
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.DocumentCodec
import kotlinx.serialization.Serializable

/**
 * App-private, bounded recovery receipt for WLO-0094's single pending delete.
 * It is deliberately outside the measurement/event export: this transient
 * document is removed on expiry or successful restore and is not health-data
 * history in its own right.
 */
public class LogbookDeletionRecoveryStore(
    private val documents: JsonDocumentStore,
) {
    public suspend fun read(profileId: String): PendingLogbookDeletion? =
        documents.readText(key(profileId))?.let { encoded ->
            runCatching {
                DocumentCodec.json.decodeFromString(PendingLogbookDeletion.serializer(), encoded)
            }.getOrNull()
        }

    public suspend fun write(record: PendingLogbookDeletion) {
        documents.writeText(
            key(record.snapshot.event.profileId),
            DocumentCodec.json.encodeToString(PendingLogbookDeletion.serializer(), record),
        )
    }

    public suspend fun clear(profileId: String) {
        documents.remove(key(profileId))
    }

    private fun key(profileId: String): String = "weight/logbook-delete-recovery-v1/$profileId"
}

@Serializable
public data class PendingLogbookDeletion(
    public val schemaVersion: Int = 1,
    public val operationId: String,
    public val snapshot: DeletedWeighIn,
    public val remainingForegroundMillis: Long = LogbookViewModel.UNDO_WINDOW_MS,
)
