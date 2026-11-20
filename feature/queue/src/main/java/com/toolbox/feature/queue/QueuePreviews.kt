package com.toolbox.feature.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.toolbox.core.model.FakeData
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.ToolboxTheme

@Preview(name = "队列 · 亮色", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun QueuePreview() {
    ToolboxTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            QueueScreen(
                jobs = FakeData.initialJobs,
                onCancel = {},
                onRetry = {},
                onOpen = {},
                onCancelAll = {},
                onClearFinished = {}
            )
        }
    }
}