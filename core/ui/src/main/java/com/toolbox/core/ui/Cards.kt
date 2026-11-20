package com.toolbox.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.core.model.Job
import com.toolbox.core.model.JobStatus
import com.toolbox.core.model.SourceFile
import com.toolbox.core.model.Stage
import com.toolbox.core.model.ToolSpec
import java.util.Locale

@Composable
fun ToolCard(
    spec: ToolSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "toolCardPress"
    )
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (pressed) p.paper else p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(p.pineSoft),
            contentAlignment = Alignment.Center
        ) {
            ToolGlyph(spec.glyph, p.pine)
        }

        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.title,
                style = ToolboxType.CardTitle,
                color = p.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (spec.comingSoon) {
                Spacer(Modifier.width(5.dp))
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(p.pine)
                )
                Spacer(Modifier.width(3.dp))
                Text("即将", style = ToolboxType.Caption.copy(fontSize = 9.5.sp), color = p.pine)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = spec.summary,
            style = ToolboxType.Caption,
            color = p.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val demoWave = listOf(
    0.18f, 0.32f, 0.45f, 0.28f, 0.58f, 0.40f, 0.66f, 0.36f,
    0.22f, 0.52f, 0.74f, 0.41f, 0.30f, 0.62f, 0.45f, 0.26f,
    0.48f, 0.70f, 0.35f, 0.22f, 0.40f, 0.58f, 0.31f, 0.18f,
    0.44f, 0.66f, 0.36f, 0.26f, 0.52f, 0.40f, 0.22f, 0.48f,
    0.62f, 0.30f, 0.40f, 0.26f, 0.54f, 0.36f, 0.18f, 0.44f
)

@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    levels: List<Float> = demoWave,
    played: Float = 0.34f,
    color: Color = LocalPalette.current.pine,
    height: Dp = 22.dp
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (levels.isEmpty()) return@Canvas
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
        val playedCount = (levels.size * played).toInt()

        levels.forEachIndexed { index, level ->
            val h = (size.height * level).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = color.copy(alpha = if (index < playedCount) 0.85f else 0.22f),
                topLeft = Offset(index * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
fun SourceFileCard(
    file: SourceFile,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.pineSoft),
                contentAlignment = Alignment.Center
            ) {
                ToolGlyph(specGlyphFor(file.displayName), p.pine)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = ToolboxType.CardTitle,
                    color = p.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${formatDuration(file.durationMs)} · ${formatBytes(file.sizeBytes)} · ${file.codecLabel}",
                    style = ToolboxType.Caption.copy(fontSize = 10.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = p.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Waveform()
    }
}

private fun specGlyphFor(name: String): com.toolbox.core.model.Glyph =
    if (name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true)) {
        com.toolbox.core.model.Glyph.MUSIC
    } else {
        com.toolbox.core.model.Glyph.FILM
    }

@Composable
fun JobCard(
    job: Job,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val failed = job.status == JobStatus.FAILED
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, if (failed) p.clay.copy(alpha = 0.35f) else p.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        when (job.status) {
            JobStatus.RUNNING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.source.displayName,
                        style = ToolboxType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = p.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatPercent(job.progress.fraction), style = ToolboxType.Mono, color = p.pine)
                }
                Spacer(Modifier.height(9.dp))
                ThinProgress(job.progress.fraction, height = 3.dp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stageLabel(job), style = ToolboxType.Caption.copy(fontSize = 10.5.sp), color = p.ink3)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "取消",
                        style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                        color = p.clay,
                        modifier = Modifier.clickable(onClick = onCancel)
                    )
                }
            }

            JobStatus.DONE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(1.dp, p.pine, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CheckIcon(p.pine)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = job.source.displayName,
                            style = ToolboxType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = p.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "完成 · ${elapsedLabel(job)} · ${when {
                                job.remuxed -> "重封装"
                                job.lossless -> "无损"
                                else -> "已转码"
                            }}",
                            style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                            color = p.ink3
                        )
                        val output = job.outputName
                        if (output != null && output != job.source.displayName) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "→ $output",
                                style = ToolboxType.Caption.copy(
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = p.ink2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        "打开",
                        style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                        color = p.pine,
                        modifier = Modifier.clickable(onClick = onOpen)
                    )
                }
            }

            JobStatus.FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.source.displayName,
                        style = ToolboxType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = p.clay,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("失败", style = ToolboxType.Mono, color = p.clay)
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.error?.title ?: "处理失败",
                        style = ToolboxType.Caption.copy(fontSize = 11.sp),
                        color = p.ink2,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "重试",
                        style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                        color = p.pine,
                        modifier = Modifier.clickable(onClick = onRetry)
                    )
                }
            }

            JobStatus.QUEUED, JobStatus.CANCELLED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = job.source.displayName,
                        style = ToolboxType.Body,
                        color = p.ink2,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (job.status == JobStatus.QUEUED) "已暂停" else "已取消",
                        style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                        color = p.ink3
                    )
                }
            }
        }
    }
}

private fun stageLabel(job: Job): String = when (job.progress.stage) {
    Stage.COPYING -> "正在写入 · ${formatBytes(job.bytesDone)} / ${formatBytes(job.bytesTotal)}"
    Stage.TRANSCODING -> "转码中 · 已完成 ${formatPercent(job.progress.fraction)}"
    Stage.MUXING -> "正在封装"
    Stage.VERIFYING -> "正在校验"
    Stage.PROBING -> "正在读取"
    Stage.PLANNING -> "正在分析"
}

private fun elapsedLabel(job: Job): String =
    String.format(Locale.US, "%.1f 秒", job.elapsedMs / 1000f)