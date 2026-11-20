package com.toolbox.core.task.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.toolbox.core.model.ErrorCode
import com.toolbox.core.model.Job
import com.toolbox.core.model.JobError
import com.toolbox.core.model.JobStatus
import com.toolbox.core.model.Progress
import com.toolbox.core.model.SourceFile
import com.toolbox.core.model.Stage

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val toolId: String,
    val toolTitle: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val codecLabel: String,
    val outputName: String?,
    val sourceUri: String,
    val status: String,
    val stage: String,
    val fraction: Float,
    val bytesDone: Long,
    val bytesTotal: Long,
    val elapsedMs: Long,
    val lossless: Boolean,
    val remuxed: Boolean,
    val errorCode: String?,
    val errorTitle: String?,
    val createdAt: Long,
    val paramsEncoded: String = "",
    val outputUri: String = ""
) {
    fun toJob(): Job = Job(
        id = id,
        source = SourceFile(
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            codecLabel = codecLabel
        ),
        toolTitle = toolTitle,
        status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.QUEUED),
        progress = Progress(
            stage = runCatching { Stage.valueOf(stage) }.getOrDefault(Stage.PLANNING),
            fraction = fraction
        ),
        outputName = outputName,
        bytesTotal = bytesTotal,
        bytesDone = bytesDone,
        elapsedMs = elapsedMs,
        lossless = lossless,
        remuxed = remuxed,
        error = errorCode?.let { code ->
            JobError(
                code = runCatching { ErrorCode.valueOf(code) }.getOrDefault(ErrorCode.UNKNOWN),
                title = errorTitle ?: "处理失败",
                detail = errorTitle ?: ""
            )
        },
        toolId = toolId,
        sourceUri = sourceUri,
        params = decodeParams(paramsEncoded),
        outputUri = outputUri
    )

    companion object {
        fun from(job: Job, createdAt: Long): JobEntity = JobEntity(
            id = job.id,
            toolId = job.toolId,
            toolTitle = job.toolTitle,
            displayName = job.source.displayName,
            sizeBytes = job.source.sizeBytes,
            durationMs = job.source.durationMs,
            codecLabel = job.source.codecLabel,
            outputName = job.outputName,
            sourceUri = job.sourceUri,
            status = job.status.name,
            stage = job.progress.stage.name,
            fraction = job.progress.fraction,
            bytesDone = job.bytesDone,
            bytesTotal = job.bytesTotal,
            elapsedMs = job.elapsedMs,
            lossless = job.lossless,
            remuxed = job.remuxed,
            errorCode = job.error?.code?.name,
            errorTitle = job.error?.title,
            createdAt = createdAt,
            paramsEncoded = encodeParams(job.params),
            outputUri = job.outputUri
        )

        private fun encodeParams(params: Map<String, String>): String =
            params.entries.joinToString("&") { (k, v) -> "${escape(k)}=${escape(v)}" }

        private fun decodeParams(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            return raw.split("&").mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null
                else unescape(pair.substring(0, index)) to unescape(pair.substring(index + 1))
            }.toMap()
        }

        private fun escape(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")

        private fun unescape(value: String): String =
            runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    suspend fun all(): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun byId(id: String): JobEntity?

    @Upsert
    suspend fun upsert(job: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    /** 进程被杀时留下的 RUNNING 记录，恢复时要退回 QUEUED 重跑。 */
    @Query("UPDATE jobs SET status = 'QUEUED', fraction = 0 WHERE status = 'RUNNING'")
    suspend fun resetRunning()
}

@Database(entities = [JobEntity::class], version = 3, exportSchema = false)
abstract class ToolboxDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
}

/** v2 → v3：记录输出文件 Uri，供队列「打开」使用。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN outputUri TEXT NOT NULL DEFAULT ''")
    }
}
