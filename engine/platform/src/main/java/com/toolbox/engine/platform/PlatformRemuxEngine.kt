package com.toolbox.engine.platform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.toolbox.core.media.UriProber
import com.toolbox.core.model.ErrorCode
import com.toolbox.core.model.MediaFlag
import com.toolbox.core.model.Timecode
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
import java.nio.ByteBuffer

/**
 * 兜底：MediaExtractor → MediaMuxer 样本级透传。
 * 不解码、不重编码，但会重建容器索引，因此封面/章节/无缝播放信息会丢。
 * 适合处理 ftyp 无法原地改写的文件，以及"从视频里提取音轨"。
 */
class PlatformRemuxEngine(private val context: Context) : MediaEngine {

    override val id: String = "platform.muxer-remux"

    private val prober = UriProber(context)
    private val verifier = OutputVerifier(context)

    override suspend fun plan(request: JobRequest): PlanOutcome = withContext(Dispatchers.IO) {
        val probe = prober.probe(request.sourceUri, request.displayName)
            ?: return@withContext PlanOutcome.Unsupported(
                ErrorCode.SRC_UNREADABLE,
                "无法打开这个文件，可能已被移动或删除"
            )

        if (probe.media.flags.contains(MediaFlag.ENCRYPTED)) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.ENCRYPTED,
                "文件受 DRM 保护，无法转换"
            )
        }
        if (probe.media.tracks.none { it.type == TrackType.AUDIO }) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.UNSUPPORTED_CODEC,
                "文件里没有音频轨"
            )
        }

        val duration = probe.file.durationMs
        val clipStart = request.params["start"]?.let { Timecode.parseToMs(it) } ?: 0L
        val requestedEnd = request.params["end"]?.let { Timecode.parseToMs(it) }
        val clipEnd = (requestedEnd ?: duration).coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)

        if (duration > 0 && clipEnd - clipStart < 200L) {
            return@withContext PlanOutcome.Unsupported(
                ErrorCode.SRC_CORRUPTED,
                "裁剪区间太短，至少 0.2 秒"
            )
        }

        val hasClip = clipStart > 0L || (duration > 0 && clipEnd < duration - 50L)
        val baseName = request.params["name"]?.takeIf { it.isNotBlank() }
            ?: request.displayName.substringBeforeLast('.', request.displayName)
        val suffix = if (hasClip) "_clip" else ""
        val ratio = if (duration > 0 && hasClip) {
            (clipEnd - clipStart).toFloat() / duration
        } else {
            1f
        }

        PlanOutcome.Ready(
            EnginePlan(
                engineId = id,
                probe = probe,
                outputName = "$baseName$suffix.${request.tool.outputExtension}",
                estimatedBytes = (probe.file.sizeBytes * ratio).toLong(),
                lossless = true,
                clipStartMs = if (hasClip) clipStart else null,
                clipEndMs = if (hasClip) clipEnd else null
            )
        )
    }

    /** MediaMuxer 只会写 mp42；.m4a 输出再把主品牌改成 M4A ，Apple 生态才认。 */
    private fun retargetToM4a(fd: java.io.FileDescriptor) {
        val head = ByteArray(64)
        val read = Os.pread(fd, head, 0, head.size, 0L)
        if (read < 32) return
        if (String(head, 4, 4, Charsets.ISO_8859_1) != "ftyp") return

        val size = ((head[0].toInt() and 0xFF) shl 24) or
            ((head[1].toInt() and 0xFF) shl 16) or
            ((head[2].toInt() and 0xFF) shl 8) or
            (head[3].toInt() and 0xFF)
        if (size < 20 || size > read) return

        "M4A ".toByteArray(Charsets.ISO_8859_1).copyInto(head, 8)

        val brandCount = (size - 16) / 4
        var hasM4a = false
        for (i in 0 until brandCount) {
            val offset = 16 + i * 4
            if (String(head, offset, 4, Charsets.ISO_8859_1) == "M4A ") {
                hasM4a = true
                break
            }
        }
        if (!hasM4a && brandCount > 0) {
            "M4A ".toByteArray(Charsets.ISO_8859_1)
                .copyInto(head, 16 + (brandCount - 1) * 4)
        }
        Os.pwrite(fd, head, 0, read, 0L)
    }

    override suspend fun execute(
        plan: EnginePlan,
        request: JobRequest,
        ctx: ExecutionContext
    ): EngineResult = withContext(Dispatchers.IO) {
        val audioOnly = request.tool.outputExtension.equals("m4a", ignoreCase = true)
        val startedAt = SystemClock.elapsedRealtime()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(context, request.sourceUri, emptyMap<String, String>())

            val selected = (0 until extractor.trackCount).filter { index ->
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: ""
                when {
                    mime.startsWith("audio/") -> true
                    audioOnly -> false
                    mime.startsWith("video/") -> true
                    else -> false
                }
            }
            if (selected.isEmpty()) {
                return@withContext EngineResult.Failure(
                    ErrorCode.UNSUPPORTED_CODEC,
                    "没有可写入的轨道"
                )
            }

            val pfd = ctx.output.openFileDescriptor()
                ?: return@withContext EngineResult.Failure(
                    ErrorCode.NO_SPACE,
                    "无法打开输出文件"
                )

            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var started = false
            var samples = 0
            var lastPtsUs = 0L
            val clipStartUs = (plan.clipStartMs ?: 0L) * 1000
            val clipEndUs = (plan.clipEndMs ?: plan.probe.file.durationMs) * 1000
            val clipLengthUs = (clipEndUs - clipStartUs).coerceAtLeast(1L)

            try {
                selected.forEach { extractor.selectTrack(it) }
                if (clipStartUs > 0) {
                    extractor.seekTo(clipStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                }
                val trackMap = selected.associateWith { index ->
                    muxer.addTrack(extractor.getTrackFormat(index))
                }

                muxer.start()
                started = true

                val buffer = ByteBuffer.allocateDirect(512 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    if (ctx.isCancelled()) throw CancellationException("用户取消")

                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break

                    val ptsUs = extractor.sampleTime
                    if (ptsUs > clipEndUs) break
                    if (ptsUs < clipStartUs) {
                        extractor.advance()
                        continue
                    }

                    val trackIndex = extractor.sampleTrackIndex
                    val outTrack = trackMap[trackIndex] ?: break

                    val relativePts = (ptsUs - clipStartUs).coerceAtLeast(0L)
                    info.set(
                        0,
                        size,
                        relativePts,
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                    )
                    muxer.writeSampleData(outTrack, buffer, info)
                    samples++
                    lastPtsUs = maxOf(lastPtsUs, relativePts)
                    ctx.onProgress(
                        (relativePts.toFloat() / clipLengthUs).coerceIn(0f, 1f),
                        relativePts / 1000
                    )
                    extractor.advance()
                }

                if (samples == 0) {
                    return@withContext EngineResult.Failure(
                        ErrorCode.SRC_CORRUPTED,
                        "文件里没有可读取的样本"
                    )
                }

                // 显式声明最后一帧时长，避免末尾被截断
                info.set(0, 0, lastPtsUs + 21_333, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                runCatching { muxer.writeSampleData(trackMap.values.first(), ByteBuffer.allocate(0), info) }
            } finally {
                if (started) runCatching { muxer.stop() }
                runCatching { muxer.release() }
                runCatching { pfd.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext EngineResult.Failure(
                ErrorCode.PLATFORM_CODEC_FAIL,
                e.message ?: "平台封装器写入失败",
                e
            )
        } finally {
            runCatching { extractor.release() }
        }

        if (audioOnly) {
            runCatching {
                ctx.output.openFileDescriptor()?.use { pfd -> retargetToM4a(pfd.fileDescriptor) }
            }.onFailure { Log.w("PlatformRemux", "retarget to M4A brand failed", it) }
        }

        val clipStart = plan.clipStartMs
        val clipEnd = plan.clipEndMs
        val expectedMs = if (clipStart != null && clipEnd != null) {
            clipEnd - clipStart
        } else {
            plan.probe.file.durationMs
        }
        val verified = verifier.verify(
            uri = ctx.output.uri,
            path = ctx.output.path,
            expectedDurationMs = expectedMs
        )
        if (!verified) {
            return@withContext EngineResult.Failure(
                ErrorCode.VERIFY_FAILED,
                "输出文件校验未通过，已回滚"
            )
        }

        EngineResult.Success(
            outputName = plan.outputName,
            bytes = 0L,
            elapsedMs = SystemClock.elapsedRealtime() - startedAt,
            lossless = true,
            remuxed = true
        )
    }
}