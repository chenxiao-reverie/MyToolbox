package com.toolbox.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.core.model.Category
import com.toolbox.core.model.Job
import com.toolbox.core.model.ToolSpec
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.SearchBox
import com.toolbox.core.ui.SectionLabel
import com.toolbox.core.ui.ThinProgress
import com.toolbox.core.ui.ToolCard
import com.toolbox.core.ui.ToolboxType
import com.toolbox.core.ui.UnderlineSegments
import com.toolbox.core.ui.formatPercent

@Composable
fun HomeScreen(
    tools: List<ToolSpec>,
    running: Job?,
    onToolClick: (ToolSpec) -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    var selected by remember { mutableIntStateOf(0) }

    val tabs = listOf("全部", "容器", "音频", "视频", "图片")
    val categories = listOf(null, Category.CONTAINER, Category.AUDIO, Category.VIDEO, Category.IMAGE)
    val filtered = remember(tools, selected) {
        val category = categories[selected]
        if (category == null) tools else tools.filter { it.category == category }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        SectionLabel("TOOLBOX")
        Spacer(Modifier.height(5.dp))
        Text("今天要处理什么", style = ToolboxType.Title, color = p.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "本地处理 · 文件不出手机",
            style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
            color = p.ink3
        )

        Spacer(Modifier.height(12.dp))
        SearchBox("搜索工具或格式")

        Spacer(Modifier.height(13.dp))
        UnderlineSegments(tabs, selected) { selected = it }

        Spacer(Modifier.height(12.dp))

        filtered.chunked(2).forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowTools.forEach { tool ->
                    ToolCard(
                        spec = tool,
                        onClick = { onToolClick(tool) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTools.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(9.dp))
        }

        if (running != null) {
            Spacer(Modifier.height(4.dp))
            RunningStrip(running, onOpenQueue)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunningStrip(job: Job, onOpenQueue: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .clickable(onClick = onOpenQueue)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "进行中",
                style = ToolboxType.Eyebrow.copy(fontSize = 10.5.sp, letterSpacing = 1.4.sp),
                color = p.ink3
            )
            Spacer(Modifier.weight(1f))
            Text("1 个任务", style = ToolboxType.Caption.copy(fontSize = 10.5.sp), color = p.ink3)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = job.source.displayName,
                style = ToolboxType.Body.copy(fontWeight = FontWeight.Medium),
                color = p.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatPercent(job.progress.fraction), style = ToolboxType.Mono, color = p.ink2)
        }
        Spacer(Modifier.height(7.dp))
        ThinProgress(job.progress.fraction)
    }
}