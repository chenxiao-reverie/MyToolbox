package com.toolbox.feature.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolbox.core.model.Job
import com.toolbox.core.model.JobStatus
import com.toolbox.core.ui.JobCard
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.ToolboxType

@Composable
fun QueueScreen(
    jobs: List<Job>,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpen: (Job) -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val running = jobs.count { it.status == JobStatus.RUNNING }
    val done = jobs.count { it.status == JobStatus.DONE }
    val failed = jobs.count { it.status == JobStatus.FAILED }
    val queued = jobs.count { it.status == JobStatus.QUEUED }
    val finished = jobs.count {
        it.status == JobStatus.DONE || it.status == JobStatus.FAILED || it.status == JobStatus.CANCELLED
    }
    val headerAction = when {
        running + queued > 0 -> "全部取消" to onCancelAll
        finished > 0 -> "清除已完成" to onClearFinished
        else -> null
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("任务", style = ToolboxType.Title, color = p.ink, modifier = Modifier.weight(1f))
            if (headerAction != null) {
                Text(
                    text = headerAction.first,
                    style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                    color = p.pine,
                    modifier = Modifier
                        .clickable(onClick = headerAction.second)
                        .padding(4.dp)
                )
            }
        }

        Row(
            Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Stat("$running 进行中")
            Stat("$done 已完成")
            Stat("$failed 失败")
        }

        Spacer(Modifier.height(10.dp))

        if (jobs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(80.dp))
                Text("还没有任务", style = ToolboxType.CardTitle, color = p.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "从「工具」里挑一个开始",
                    style = ToolboxType.Caption.copy(fontSize = 11.5.sp),
                    color = p.ink3
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    JobCard(
                        job = job,
                        onCancel = { onCancel(job.id) },
                        onRetry = { onRetry(job.id) },
                        onOpen = { onOpen(job) }
                    )
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "划掉应用也会继续处理",
                        style = ToolboxType.Caption.copy(fontSize = 10.5.sp, letterSpacing = 0.3.sp),
                        color = p.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(text: String) {
    Text(
        text = text,
        style = ToolboxType.Caption.copy(
            fontSize = 10.5.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        ),
        color = LocalPalette.current.ink3
    )
}