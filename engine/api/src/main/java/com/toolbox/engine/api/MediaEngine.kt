package com.toolbox.engine.api

import android.net.Uri
import com.toolbox.core.model.ErrorCode
import com.toolbox.core.model.ProbeResult
import com.toolbox.core.model.ToolSpec
import com.toolbox.core.storage.OutputHandle

data class JobRequest(
    val tool: ToolSpec,
    val sourceUri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val params: Map<String, String> = emptyMap()
)

data class EnginePlan(
    val engineId: String,
    val probe: ProbeResult,
    val outputName: String,
    val estimatedBytes: Long,
    val lossless: Boolean,
    val clipStartMs: Long? = null,
    val clipEndMs: Long? = null
)

sealed interface PlanOutcome {
    data class Ready(val plan: EnginePlan) : PlanOutcome
    data class Unsupported(val code: ErrorCode, val reason: String) : PlanOutcome
}

class ExecutionContext(
    val output: OutputHandle,
    val onProgress: (fraction: Float, bytesDone: Long) -> Unit,
    val isCancelled: () -> Boolean
)

sealed interface EngineResult {
    data class Success(
        val outputName: String,
        val bytes: Long,
        val elapsedMs: Long,
        val lossless: Boolean,
        val remuxed: Boolean = false
    ) : EngineResult

    data class Failure(
        val code: ErrorCode,
        val detail: String,
        val cause: Throwable? = null
    ) : EngineResult
}

/**
 * plan() 只做决策（读 box 头 + moov），execute() 才真正写盘。
 * 两者分开，才能在下单前告诉用户"能不能无损、要多久、多大"。
 */
interface MediaEngine {
    val id: String

    suspend fun plan(request: JobRequest): PlanOutcome

    suspend fun execute(
        plan: EnginePlan,
        request: JobRequest,
        ctx: ExecutionContext
    ): EngineResult
}