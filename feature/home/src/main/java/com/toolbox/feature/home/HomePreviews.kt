package com.toolbox.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.toolbox.core.model.FakeData
import com.toolbox.core.model.ToolCatalog
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.ToolboxTheme

@Preview(name = "首页 · 亮色", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun HomeLightPreview() {
    ToolboxTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            HomeScreen(
                tools = ToolCatalog.tools,
                running = FakeData.initialJobs.first(),
                onToolClick = {},
                onOpenQueue = {}
            )
        }
    }
}

@Preview(name = "首页 · 暗色", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun HomeDarkPreview() {
    ToolboxTheme(darkTheme = true) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            HomeScreen(
                tools = ToolCatalog.tools,
                running = null,
                onToolClick = {},
                onOpenQueue = {}
            )
        }
    }
}