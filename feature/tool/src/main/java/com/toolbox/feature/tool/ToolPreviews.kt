package com.toolbox.feature.tool

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.toolbox.core.model.FakeData
import com.toolbox.core.model.ToolCatalog
import com.toolbox.core.ui.BannerTone
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.ToolboxTheme

@Preview(name = "工具页 · 未选文件", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun ToolEmptyPreview() {
    ToolboxTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            ToolScreen(
                tool = ToolCatalog.tools.first(),
                file = null,
                probing = false,
                preflight = null,
                error = null,
                onBack = {},
                onPick = {},
                onStart = {}
            )
        }
    }
}

@Preview(name = "工具页 · 可无损", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun ToolReadyPreview() {
    ToolboxTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            ToolScreen(
                tool = ToolCatalog.tools.first(),
                file = FakeData.sampleAudio,
                probing = false,
                preflight = PreflightUi(
                    tone = BannerTone.Lossless,
                    title = "可无损转换",
                    reason = "不重新编码，音质完全保留",
                    cost = "≈ 1 秒"
                ),
                error = null,
                onBack = {},
                onPick = {},
                onStart = {}
            )
        }
    }
}

@Preview(name = "工具页 · 不支持", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun ToolBlockedPreview() {
    ToolboxTheme(darkTheme = false) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.paper)) {
            ToolScreen(
                tool = ToolCatalog.tools.first(),
                file = FakeData.sampleAudio,
                probing = false,
                preflight = PreflightUi(
                    tone = BannerTone.Blocked,
                    title = "暂不支持",
                    reason = "碎片化 MP4，需要先拼接成普通 MP4"
                ),
                error = null,
                onBack = {},
                onPick = {},
                onStart = {}
            )
        }
    }
}