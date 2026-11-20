package com.toolbox.core.task

import android.net.Uri
import android.util.Log
import com.toolbox.core.model.Category
import com.toolbox.core.model.ErrorCode
import com.toolbox.core.model.Job
import com.toolbox.core.model.JobError
import com.toolbox.core.model.JobStatus
import com.toolbox.core.model.Progress
import com.toolbox.core.model.SourceFile
import com.toolbox.core.model.Stage
import com.toolbox.core.model.ToolSpec
import com.toolbox.core.storage.OutputStore
import com.toolbox.core.task.db.JobDao
import com.toolbox.core.task.db.JobEntity
import com.toolbox.engine.api.EngineResult
import com.toolbox.engine.api.ExecutionContext
import com.toolbox.engine.api.JobRequest
import com.toolbox.engine.api.MediaEngine
import com.toolbox.engine.api.PlanOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 任务仓库：串行执行 + Room 落盘 + 前台服务保活。
 * 进程被杀后，restore() 把 RUNNING 退回 QUEUED 重跑，UI 只依赖 jobs 这个 StateFlow。
 */
class JobRepository(
    private val engines: List<MediaEngine>,
    private val outputStore: OutputStore,
    private val dao: JobDao,
    private val toolResolver: (String) -> ToolSpec?,
    private val onWorkEnqueued: () -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val runLock = Mutex()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val lastPercent = ConcurrentHashMap<String, Int>()
    private val createdAt = ConcurrentHashMap<String, Long>()
    private val sequence = AtomicInteger(1)

    /** 单消费者写库，避免并发 upsert 把新状态覆盖成旧状态。 */
    private val persistQueue = Channel<Job>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in persistQueue) {
                runCatching {
                    dao.upsert(JobEntity.from(job, createdAt[job.id] ?: System.currentTimeMillis()))
                }
            }
        }
    }

    fun restore() {
        scope.launch {
            runCatching {
                dao.resetRunning()
                val entities = dao.all()
                entities.forEach { createdAt[it.id] = it.createdAt }
                if (entities.isNotEmpty()) {
                    _jobs.value = entities.map { it.toJob() }
                    val maxId = entities.mapNotNull { it.id.removePrefix("job-").toIntOrNull() }
                        .maxOrNull() ?: 0
                    sequence.set(maxId + 1)
                }
            }
            if (_jobs.value.any { it.status == JobStatus.QUEUED }) {
                onWorkEnqueued()
                resumePending()
            }
        }
    }

    fun start(
        tool: ToolSpec,
        uri: Uri,
        source: SourceFile,
        params: Map<String, String> = emptyMap()
    ): String {
        val id = "job-${sequence.getAndIncrement()}"
        val job = Job(
            id = id,
            source = source,
            toolTitle = tool.title,
            status = JobStatus.QUEUED,
            progress = Progress(Stage.PLANNING, 0f),
            bytesTotal = source.sizeBytes,
            bytesDone = 0L,
            lossless = tool.category == Category.CONTAINER,
            toolId = tool.id.value,
            sourceUri = uri.toString(),
            params = params
        )
        createdAt[id] = System.currentTimeMillis()
        _jobs.update { listOf(job) + it }
        persist(job)
        onWorkEnqueued()
        scope.launch { run(id) }
        return id
    }

    fun resumePending() {
        _jobs.value
            .filter { it.status == JobStatus.QUEUED }
            .forEach { job -> scope.launch { run(job.id) } }
    }

    fun cancel(id: String) {
        cancelled += id
        update(id) { job ->
            if (job.status == JobStatus.RUNNING || job.status == JobStatus.QUEUED) {
                job.copy(status = JobStatus.CANCELLED, progress = job.progress.copy(fraction = 0f))
            } else {
                job
            }
        }
    }

    fun cancelAll() {
        _jobs.value
            .filter { it.status == JobStatus.RUNNING || it.status == JobStatus.QUEUED }
            .forEach { cancel(it.id) }
    }

    fun retry(id: String) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.status != JobStatus.FAILED && job.status != JobStatus.CANCELLED) return
        cancelled -= id
        lastPercent -= id
        update(id) {
            it.copy(
                status = JobStatus.QUEUED,
                progress = Progress(Stage.PLANNING, 0f),
                bytesDone = 0L,
                error = null,
                elapsedMs = 0L
            )
        }
        onWorkEnqueued()
        scope.launch { run(id) }
    }

    fun clearFinished() {
        val finished = _jobs.value.filter {
            it.status == JobStatus.DONE || it.status == JobStatus.FAILED || it.status == JobStatus.CANCELLED
        }
        _jobs.update { list -> list.filterNot { job -> finished.any { it.id == job.id } } }
        scope.launch {
            finished.forEach { runCatching { dao.delete(it.id) } }
        }
    }

    private suspend fun run(id: String) {
        val queued = _jobs.value.firstOrNull { it.id == id } ?: return
        if (queued.status != JobStatus.QUEUED) return

        val tool = toolResolver(queued.toolId)
        if (tool == null) {
            fail(id, ErrorCode.ENGINE_UNAVAILABLE, "找不到对应的工具")
            return
        }
        val uri = runCatching { Uri.parse(queued.sourceUri) }.getOrNull()
        if (uri == null || queued.sourceUri.isBlank()) {
            fail(id, ErrorCode.SRC_UNREADABLE, "源文件地址失效，请重新选择")
            return
        }

        runLock.withLock {
            val current = _jobs.value.firstOrNull { it.id == id } ?: return
            if (current.status != JobStatus.QUEUED) return

            Log.d("JobRepo", "running $id (${tool.id.value})")
            update(id) { it.copy(status = JobStatus.RUNNING, progress = Progress(Stage.PLANNING, 0.01f)) }
            execute(id, tool, uri)
        }
    }

    private suspend fun execute(id: String, tool: ToolSpec, uri: Uri) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        val request = JobRequest(tool, uri, job.source.displayName, job.source.sizeBytes, job.params)

        var lastReason: String? = null
        var lastCode = ErrorCode.ENGINE_UNAVAILABLE

        for (engine in engines) {
            if (id in cancelled) {
                markCancelled(id)
                return
            }

            when (val outcome = engine.plan(request)) {
                is PlanOutcome.Unsupported -> {
                    lastReason = outcome.reason
                    lastCode = outcome.code
                }

                is PlanOutcome.Ready -> {
                    val plan = outcome.plan
                    update(id) { it.copy(progress = Progress(Stage.COPYING, 0.02f)) }

                    val mime = mimeFor(tool)
                    val finalName = runCatching {
                        outputStore.resolveAvailableName(plan.outputName, mime)
                    }.getOrDefault(plan.outputName)
                    val handle = runCatching {
                        outputStore.create(finalName, mime)
                    }.getOrNull()

                    if (handle == null) {
                        fail(id, ErrorCode.NO_SPACE, "无法创建输出文件，存储空间可能不足")
                        return
                    }

                    try {
                        val result = engine.execute(
                            plan = plan,
                            request = request,
                            ctx = ExecutionContext(
                                output = handle,
                                onProgress = { fraction, bytes -> reportProgress(id, fraction, bytes) },
                                isCancelled = { id in cancelled }
                            )
                        )

                        when (result) {
                            is EngineResult.Success -> {
                                handle.commit()
                                update(id) {
                                    it.copy(
                                        status = JobStatus.DONE,
                                        progress = Progress(Stage.VERIFYING, 1f),
                                        bytesDone = it.bytesTotal,
                                        elapsedMs = result.elapsedMs,
                                        outputName = handle.displayName,
                                        outputUri = handle.uri?.toString()
                                            ?: handle.path
                                            ?: "",
                                        lossless = result.lossless,
                                        remuxed = result.remuxed
                                    )
                                }
                            }

                            is EngineResult.Failure -> {
                                handle.abort()
                                if (id in cancelled) markCancelled(id) else fail(id, result.code, result.detail)
                            }
                        }
                    } catch (e: CancellationException) {
                        handle.abort()
                        markCancelled(id)
                        throw e
                    } catch (e: Exception) {
                        handle.abort()
                        fail(id, ErrorCode.UNKNOWN, e.message ?: "处理失败")
                    }
                    return
                }
            }
        }

        fail(id, lastCode, lastReason ?: "没有可用的转换引擎")
    }

    private fun reportProgress(id: String, fraction: Float, bytes: Long) {
        val percent = (fraction * 100).toInt()
        if (lastPercent.put(id, percent) == percent) return
        update(id) { job ->
            if (job.status != JobStatus.RUNNING) {
                job
            } else {
                job.copy(
                    progress = job.progress.copy(stage = Stage.COPYING, fraction = fraction),
                    bytesDone = bytes
                )
            }
        }
    }

    private fun fail(id: String, code: ErrorCode, detail: String) {
        update(id) { job ->
            job.copy(
                status = JobStatus.FAILED,
                error = JobError(code = code, title = detail, detail = detail)
            )
        }
    }

    private fun markCancelled(id: String) {
        update(id) { job ->
            if (job.status == JobStatus.CANCELLED) job
            else job.copy(status = JobStatus.CANCELLED, progress = job.progress.copy(fraction = 0f))
        }
    }

    private fun update(id: String, block: (Job) -> Job) {
        var updated: Job? = null
        _jobs.update { list ->
            list.map { job ->
                if (job.id == id) block(job).also { updated = it } else job
            }
        }
        updated?.let { persist(it) }
    }

    private fun persist(job: Job) {
        persistQueue.trySend(job)
    }

    private fun mimeFor(tool: ToolSpec): String = when (tool.outputExtension.lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        else -> "application/octet-stream"
    }
}