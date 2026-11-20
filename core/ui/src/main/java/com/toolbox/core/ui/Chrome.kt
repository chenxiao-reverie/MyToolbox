package com.toolbox.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ToolboxTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null
) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BackIcon(p.ink)
        }
        Text(
            text = title,
            style = ToolboxType.CardTitle.copy(fontSize = 14.5.sp),
            color = p.ink,
            modifier = Modifier.align(Alignment.Center)
        )
        if (onMore != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onMore)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                MoreIcon(p.ink2)
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val kind: NavKind
)

@Composable
fun ToolboxBottomBar(
    currentRoute: String?,
    items: List<BottomNavItem>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .background(p.paper)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val active = currentRoute == item.route
                val tint = if (active) p.pine else p.ink3
                Column(
                    Modifier
                        .clickable { onSelect(item.route) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NavGlyph(item.kind, tint)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
fun ToolboxScaffoldTopInset(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding()) { content() }
}