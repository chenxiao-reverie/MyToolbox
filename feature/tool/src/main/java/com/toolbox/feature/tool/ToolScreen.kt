package com.toolbox.feature.tool

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.core.model.ParamSpec
import com.toolbox.core.model.SourceFile
import com.toolbox.core.model.Timecode
import com.toolbox.core.model.ToolSpec
import com.toolbox.core.ui.BannerTone
import com.toolbox.core.ui.FilePathRow
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.PillSegments
import com.toolbox.core.ui.PreflightBanner
import com.toolbox.core.ui.PrimaryCTA
import com.toolbox.core.ui.SourceFileCard
import com.toolbox.core.ui.ToolboxTopBar
import com.toolbox.core.ui.ToolboxType
import com.toolbox.core.ui.formatBytes

data class PreflightUi(
    val tone: BannerTone,
    val title: String,
    val reason: String,
    val cost: String? = null
)

@Composable
fun ToolScreen(
    tool: ToolSpec,
    file: SourceFile?,
    probing: Boolean,
    preflight: PreflightUi?,
    error: String?,
    renameHint: String? = null,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onStart: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val values = remember(tool.id) {
        mutableStateMapOf<String, String>().apply {
            tool.params.forEach { param -> put(param.key, defaultOf(param)) }
        }
    }

    LaunchedEffect(file?.displayName) {
        val picked = file ?: return@LaunchedEffect
        if (tool.params.any { it.key == "name" && it is ParamSpec.Text }) {
            val name = picked.displayName
            values["name"] = name.substringBeforeLast('.', name)
        }
        // 裁剪类工具：结束时间默认填满整段
        if (tool.params.any { it.key == "end" } && picked.durationMs > 0) {
            values["end"] = Timecode.format(picked.durationMs)
        }
        if (tool.params.any { it.key == "start" } && values["start"].isNullOrBlank()) {
            values["start"] = "00:00"
        }
    }

    val ready = file != null && preflight?.tone != BannerTone.Blocked && !tool.comingSoon

    Column(modifier.fillMaxSize()) {
        ToolboxTopBar(title = tool.title, onBack = onBack, onMore = {})

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            when {
                file != null -> SourceFileCard(file)
                probing -> ReadingCard()
                else -> PickFileCard(error = error, onPick = onPick)
            }

            if (preflight != null) {
                Spacer(Modifier.height(10.dp))
                PreflightBanner(
                    tone = preflight.tone,
                    title = preflight.title,
                    reason = preflight.reason,
                    cost = preflight.cost
                )
            }

            tool.params.forEach { param ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = param.title,
                    style = ToolboxType.Caption.copy(
                        fontSize = 10.5.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = p.ink3
                )
                Spacer(Modifier.height(6.dp))
                when (param) {
                    is ParamSpec.Text -> InputField(
                        value = values[param.key].orEmpty(),
                        suffix = param.suffix,
                        onValueChange = { values[param.key] = it }
                    )

                    is ParamSpec.Choice -> {
                        val index = param.options
                            .indexOfFirst { it.value == values[param.key] }
                            .coerceAtLeast(0)
                        PillSegments(
                            items = param.options.map { it.label },
                            selected = index,
                            onSelect = { values[param.key] = param.options[it].value }
                        )
                    }

                    is ParamSpec.Toggle -> {
                        val checked = values[param.key] == "1"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (checked) "已开启" else "已关闭",
                                style = ToolboxType.Body,
                                color = p.ink2,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { values[param.key] = if (it) "1" else "0" },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = p.onPine,
                                    checkedTrackColor = p.pine,
                                    uncheckedThumbColor = p.ink3,
                                    uncheckedTrackColor = p.hairline
                                )
                            )
                        }
                    }
                }
            }

            if (renameHint != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = renameHint,
                    style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                    color = p.clay
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "保存到",
                style = ToolboxType.Caption.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = p.ink3
            )
            Spacer(Modifier.height(6.dp))
            FilePathRow(value = "下载 / 工具箱", action = "更改")

            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            if (file == null) {
                PrimaryCTA(
                    text = "选择文件",
                    hint = null,
                    enabled = !tool.comingSoon,
                    onClick = onPick
                )
            } else {
                PrimaryCTA(
                    text = if (tool.comingSoon) "即将上线" else "开始转换",
                    hint = if (tool.comingSoon) null
                    else "预计 ${formatBytes(file.sizeBytes)} · 无需联网",
                    enabled = ready,
                    onClick = { onStart(values.toMap()) }
                )
            }
        }
    }
}

@Composable
private fun PickFileCard(error: String?, onPick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(onClick = onPick)
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.pineSoft),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(16.dp)) {
                val s = size.minDimension
                val sw = 1.6.dp.toPx()
                drawLine(p.pine, Offset(s * 0.5f, s * 0.18f), Offset(s * 0.5f, s * 0.82f), sw, StrokeCap.Round)
                drawLine(p.pine, Offset(s * 0.18f, s * 0.5f), Offset(s * 0.82f, s * 0.5f), sw, StrokeCap.Round)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("选择文件", style = ToolboxType.CardTitle, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            text = error ?: "支持 m4a / mp4 / m4v",
            style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
            color = if (error != null) p.clay else p.ink3,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReadingCard() {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .padding(horizontal = 14.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("正在读取文件…", style = ToolboxType.Body, color = p.ink2)
    }
}

@Composable
private fun InputField(
    value: String,
    suffix: String?,
    onValueChange: (String) -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = ToolboxType.Body.copy(color = p.ink),
            cursorBrush = SolidColor(p.pine),
            modifier = Modifier.weight(1f)
        )
        if (suffix != null) {
            Spacer(Modifier.width(4.dp))
            Text(suffix, style = ToolboxType.Mono, color = p.ink3)
        }
    }
}

private fun defaultOf(param: ParamSpec): String = when (param) {
    is ParamSpec.Text -> param.default
    is ParamSpec.Choice -> param.options.getOrElse(param.defaultIndex) { param.options.first() }.value
    is ParamSpec.Toggle -> if (param.default) "1" else "0"
}