package com.toolbox.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = ToolboxType.Eyebrow,
        color = LocalPalette.current.ink3
    )
}

@Composable
fun SearchBox(
    placeholder: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIcon(p.ink3)
        Spacer(Modifier.width(8.dp))
        Text(placeholder, style = ToolboxType.Body, color = p.ink3)
    }
}

@Composable
fun UnderlineSegments(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEachIndexed { index, label ->
                val active = index == selected
                Text(
                    text = label,
                    style = ToolboxType.Body,
                    color = if (active) p.ink else p.ink3,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onSelect(index) }
                        .drawBehind {
                            if (active) {
                                val underline = 2.dp.toPx()
                                drawRect(
                                    color = p.pine,
                                    topLeft = Offset(0f, size.height - underline),
                                    size = Size(size.width, underline)
                                )
                            }
                        }
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
fun PillSegments(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .padding(3.dp)
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) p.ink else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                    color = if (active) p.paper else p.ink2,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

enum class BannerTone { Lossless, Transcode, Blocked }

@Composable
fun PreflightBanner(
    tone: BannerTone,
    title: String,
    reason: String,
    cost: String? = null,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val background = if (tone == BannerTone.Lossless) p.pineSoft else p.claySoft
    val foreground = if (tone == BannerTone.Lossless) p.pine else p.clay

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(background)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(foreground)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ToolboxType.CardTitle, color = foreground)
            Spacer(Modifier.height(2.dp))
            Text(reason, style = ToolboxType.Caption, color = foreground.copy(alpha = 0.72f))
        }
        if (cost != null) {
            Text(cost, style = ToolboxType.Mono, color = foreground)
        }
    }
}

@Composable
fun ThinProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 2.dp,
    color: Color = LocalPalette.current.pine,
    track: Color = LocalPalette.current.hairline
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = LinearEasing),
        label = "thinProgress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun PrimaryCTA(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) p.pine else p.hairline)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = ToolboxType.CardTitle.copy(fontSize = 14.5.sp),
                color = if (enabled) p.onPine else p.ink3
            )
        }
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = ToolboxType.Mono, color = p.ink3)
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = ToolboxType.Body.copy(fontWeight = FontWeight.Medium), color = p.ink)
    }
}

@Composable
fun FilePathRow(
    value: String,
    action: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FolderIcon(p.ink2)
        Spacer(Modifier.width(8.dp))
        Text(value, style = ToolboxType.Body, color = p.ink)
        Spacer(Modifier.weight(1f))
        Text(action, style = ToolboxType.Caption, color = p.pine)
        Spacer(Modifier.width(5.dp))
        ChevronRightIcon(p.ink3)
    }
}