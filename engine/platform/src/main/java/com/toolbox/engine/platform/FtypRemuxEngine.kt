package com.toolbox.engine.platform

import android.content.Context
import android.os.SystemClock
import com.toolbox.core.media.FtypPatcher
import com.toolbox.core.media.UriProber
import com.toolbox.core.model.ErrorCode
import com.toolbox.core.model.MediaFlag
import com.toolbox.core.model.TrackType
import com.toolbox.engine.api.EnginePlan
import com.toolbox.engine.api.EngineResult
import com.toolbox.engine.api.ExecutionContext
import com.toolbox.engine.api.JobRequest
import com.toolbox.engine.api.MediaEngine
import com.toolbox.engine.api.PlanOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/**
 * L0 容器级：只改 ftyp 品牌，不动媒体数据。
 * 速度约等于磁盘拷贝，音质 100% 保留，封面/章节/无缝播放信息全部留存。
 */
class FtypRemuxEngine(private val context: Context) : MediaEngine {

    override val id: String = "platform.ftyp-remux"

    private val prober = UriProber(context)
    private val patcher = FtypPatcher()
    private val verifier = OutputVerifier(context)

    override suspend fun plan(request: JobRequest): PlanOutcome = withContext(Dispatchers.IO) {
        val probe = prober.probe(request.sourceUri, request.displayName)
            ?: return@withContext PlanOutcome.Unsupported(
                ErrorCode.SRC_UNREADABLE,
                "无法打开这个文件，可能已被移动或删除"
            )

        if (!request.tool.outputExtension.equals("mp4", ignoreCase = true)) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.UNSUPPORTED_CODEC,
                "无损品牌改写只适用于输出 MP4"
            )
        }
        if (request.params.containsKey("start") || request.params.containsKey("end")) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.UNSUPPORTED_CODEC,
                "需要裁剪，交给重封装引擎"
            )
        }
        if (probe.media.tracks.any { it.type == TrackType.VIDEO }) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.UNSUPPORTED_CODEC,
                "文件含视频轨，需要先提取音轨"
            )
        }

        if (!probe.canPatchFtyp) {
            val code = when {
                probe.media.flags.contains(MediaFlag.ENCRYPTED) -> ErrorCode.ENCRYPTED
                probe.blockReason?.contains("碎片化") == true -> ErrorCode.SRC_CORRUPTED
                else -> ErrorCode.SRC_CORRUPTED
            }
            return@withContext PlanOutcome.Unsupported(
                code,
                probe.blockReason ?: "这个文件的容器结构不支持无损转换"
            )
        }

        val baseName = request.params["name"]?.takeIf { it.isNotBlank() }
            ?: request.displayName.substringBeforeLast('.', request.displayName)
        PlanOutcome.Ready(
            EnginePlan(
                engineId = id,
                probe = probe,
                outputName = "$baseName.${request.tool.outputExtension}",
                estimatedBytes = probe.file.sizeBytes,
                lossless = true
            )
        )
    }

    override suspend fun execute(
        plan: EnginePlan,
        request: JobRequest,
        ctx: ExecutionContext
    ): EngineResult = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        val pfd = context.contentResolver.openFileDescriptor(request.sourceUri, "r")
            ?: return@withContext EngineResult.Failure(
                ErrorCode.SRC_UNREADABLE,
                "无法读取源文件"
            )

        try {
            pfd.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    ctx.output.openStream().use { output ->
                        patcher.patch(input.channel, output, plan.probe) { done ->
                            if (ctx.isCancelled()) throw CancellationException("用户取消")
                            val total = plan.probe.file.sizeBytes.coerceAtLeast(1L)
                            ctx.onProgress(
                                (done.toFloat() / total).coerceIn(0f, 1f),
                                done
                            )
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FtypPatcher.NotPatchableException) {
            return@withContext EngineResult.Failure(
                ErrorCode.SRC_CORRUPTED,
                e.message ?: "容器结构不支持原地改写",
                e
            )
        } catch (e: Exception) {
            return@withContext EngineResult.Failure(
                ErrorCode.UNKNOWN,
                e.message ?: "写入输出文件失败",
                e
            )
        }

        val verified = verifier.verify(
            uri = ctx.output.uri,
            path = ctx.output.path,
            expectedDurationMs = plan.probe.file.durationMs
        )
        if (!verified) {
            return@withContext EngineResult.Failure(
                ErrorCode.VERIFY_FAILED,
                "输出文件校验未通过，已回滚"
            )
        }

        EngineResult.Success(
            outputName = plan.outputName,
            bytes = plan.probe.file.sizeBytes,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            lossless = true
        )
    }
}