package com.toolbox.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toolbox.core.model.FakeData
import com.toolbox.core.model.JobStatus
import com.toolbox.core.model.MediaFlag
import com.toolbox.core.model.ProbeResult
import com.toolbox.core.model.ToolKind
import com.toolbox.core.model.ToolSpec
import com.toolbox.core.model.TrackType
import com.toolbox.core.model.ToolCatalog
import com.toolbox.core.ui.BannerTone
import com.toolbox.core.ui.BottomNavItem
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.NavKind
import com.toolbox.core.ui.ToolboxBottomBar
import com.toolbox.core.ui.ToolboxType
import com.toolbox.feature.diary.DiaryScreen
import com.toolbox.feature.home.HomeScreen
import com.toolbox.feature.queue.QueueScreen
import com.toolbox.feature.tool.PreflightUi
import com.toolbox.feature.tool.ToolScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val TOOL = "tool/{toolId}"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val DIARY = "diary"

    fun tool(toolId: String) = "tool/$toolId"
}

private val navItems = listOf(
    BottomNavItem(Routes.HOME, "工具", NavKind.GRID),
    BottomNavItem(Routes.QUEUE, "任务", NavKind.TASKS),
    BottomNavItem(Routes.SETTINGS, "设置", NavKind.SETTINGS)
)

@Composable
fun ToolboxApp(sharedImageUri: Uri? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val jobs by AppGraph.jobs.jobs.collectAsState()

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            navController.navigate(Routes.DIARY)
        }
    }

    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.QUEUE ||
        currentRoute == Routes.SETTINGS

    Scaffold(
        containerColor = LocalPalette.current.paper,
        bottomBar = {
            if (showBottomBar) {
                ToolboxBottomBar(
                    currentRoute = currentRoute,
                    items = navItems,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    tools = ToolCatalog.tools,
                    running = jobs.firstOrNull { it.status == JobStatus.RUNNING },
                    onToolClick = { tool ->
                        if (tool.kind == ToolKind.CREATE) {
                            navController.navigate(Routes.DIARY)
                        } else {
                            navController.navigate(Routes.tool(tool.id.value))
                        }
                    },
                    onOpenQueue = {
                        navController.navigate(Routes.QUEUE) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(
                route = Routes.TOOL,
                arguments = listOf(navArgument("toolId") { type = NavType.StringType })
            ) { entry ->
                val toolId = entry.arguments?.getString("toolId")
                val tool = ToolCatalog.tools.firstOrNull { it.id.value == toolId }
                val context = LocalContext.current

                var sourceUri by remember { mutableStateOf<Uri?>(null) }
                var probe by remember { mutableStateOf<ProbeResult?>(null) }
                var probing by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var renameHint by remember { mutableStateOf<String?>(null) }

                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        sourceUri = uri
                        probe = null
                        error = null
                        probing = true
                    }
                }

                LaunchedEffect(sourceUri) {
                    val uri = sourceUri ?: return@LaunchedEffect
                    probing = true
                    val result = withContext(Dispatchers.IO) { AppGraph.prober.probe(uri) }
                    probe = result
                    probing = false
                    renameHint = null
                    if (result == null) {
                        error = "无法读取这个文件，可能已被移动或删除"
                    } else if (tool != null) {
                        renameHint = withContext(Dispatchers.IO) { conflictHint(tool, result) }
                    }
                }

                if (tool == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ToolScreen(
                        tool = tool,
                        file = probe?.file,
                        probing = probing,
                        preflight = preflightFor(tool, probe),
                        error = error,
                        renameHint = renameHint,
                        onBack = { navController.popBackStack() },
                        onPick = { picker.launch(arrayOf("audio/*", "video/*")) },
                        onStart = { params ->
                            val currentProbe = probe
                            val currentUri = sourceUri
                            if (currentProbe != null && currentUri != null) {
                                AppGraph.jobs.start(tool, currentUri, currentProbe.file, params)
                                navController.navigate(Routes.QUEUE) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }

            composable(Routes.QUEUE) {
                val context = LocalContext.current
                QueueScreen(
                    jobs = jobs,
                    onCancel = AppGraph.jobs::cancel,
                    onRetry = AppGraph.jobs::retry,
                    onOpen = { job -> openOutput(context, job) },
                    onCancelAll = AppGraph.jobs::cancelAll,
                    onClearFinished = AppGraph.jobs::clearFinished
                )
            }

            composable(Routes.DIARY) {
                DiaryScreen(
                    outputStore = AppGraph.outputStore,
                    initialPhotoUri = sharedImageUri,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

private fun preflightFor(tool: ToolSpec, probe: ProbeResult?): PreflightUi? {
    if (tool.comingSoon) {
        return PreflightUi(
            tone = BannerTone.Blocked,
            title = "即将上线",
            reason = "该工具仍在开发中，先看看别的",
            cost = null
        )
    }
    val result = probe ?: return null

    if (result.media.flags.contains(MediaFlag.ENCRYPTED)) {
        return PreflightUi(BannerTone.Blocked, "无法处理", "文件受 DRM 保护", null)
    }
    if (result.media.tracks.none { it.type == TrackType.AUDIO }) {
        return PreflightUi(BannerTone.Blocked, "无法处理", "文件里没有音频轨", null)
    }

    val isTrim = tool.params.any { it.key == "start" || it.key == "end" }
    if (isTrim) {
        return PreflightUi(
            tone = BannerTone.Lossless,
            title = "可无损裁剪",
            reason = "只保留选中片段，样本原样搬运",
            cost = estimate(result.file.sizeBytes)
        )
    }

    val outputMp4 = tool.outputExtension.equals("mp4", ignoreCase = true)
    val hasVideo = result.media.tracks.any { it.type == TrackType.VIDEO }
    val patchable = outputMp4 && !hasVideo && result.canPatchFtyp

    return if (patchable) {
        PreflightUi(
            tone = BannerTone.Lossless,
            title = "可无损转换",
            reason = "不重新编码，音质完全保留",
            cost = estimate(result.file.sizeBytes)
        )
    } else {
        PreflightUi(
            tone = BannerTone.Lossless,
            title = "可重封装",
            reason = "音质不变；封面、章节等元数据会丢失",
            cost = estimate(result.file.sizeBytes)
        )
    }
}

private fun conflictHint(tool: ToolSpec, probe: ProbeResult): String? {
    val base = probe.file.displayName.substringBeforeLast('.', probe.file.displayName)
    val natural = "$base.${tool.outputExtension}"
    val resolved = runCatching {
        AppGraph.outputStore.resolveAvailableName(natural, mimeForExtension(tool.outputExtension))
    }.getOrDefault(natural)
    return if (resolved != natural) "已存在同名文件，将保存为 $resolved" else null
}

private fun mimeForExtension(extension: String): String = when (extension.lowercase()) {
    "mp4", "m4v", "mov" -> "video/mp4"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
}

private fun openOutput(context: android.content.Context, job: com.toolbox.core.model.Job) {
    val uri = job.outputUri
    if (!uri.startsWith("content://")) return
    val mime = mimeForExtension(job.outputName?.substringAfterLast('.', "") ?: "")
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun estimate(bytes: Long): String {
    val perSecond = 90L * 1024 * 1024
    val seconds = (bytes / perSecond).coerceAtLeast(0L) + 1
    return "≈ $seconds 秒"
}

@Composable
private fun SettingsScreen() {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = "设置",
            style = ToolboxType.Title,
            color = p.ink,
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp)
        )

        SettingRow("默认输出目录", "下载 / 工具箱")
        SettingRow("同时处理", "1 个")
        SettingRow("完成后", "保留原文件")
        SettingRow("版本", "0.2.0 · P1")

        Spacer(Modifier.height(24.dp))
        Text(
            text = "所有处理都在本机完成，不会上传任何文件。",
            style = ToolboxType.Caption.copy(fontSize = 10.sp),
            color = p.ink3
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    val p = LocalPalette.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = ToolboxType.Body, color = p.ink, modifier = Modifier.weight(1f))
            Text(value, style = ToolboxType.Caption.copy(fontSize = 10.5.sp), color = p.ink3)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline)
        )
    }
}