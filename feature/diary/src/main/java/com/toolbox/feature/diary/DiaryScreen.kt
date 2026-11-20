package com.toolbox.feature.diary

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.toolbox.core.storage.OutputStore
import com.toolbox.core.ui.LocalPalette
import com.toolbox.core.ui.PillSegments
import com.toolbox.core.ui.PrimaryCTA
import com.toolbox.core.ui.ToolboxTopBar
import com.toolbox.core.ui.ToolboxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val EXPORT_WIDTH = 1200
private const val PREVIEW_WIDTH = 560
private const val PHOTO_DIR = "Pictures/图片日记"
private const val PREFS = "diary"

private data class MarginPreset(val name: String, val marginX: Int, val topGap: Int)

private val BUILT_IN_PRESETS = listOf(
    MarginPreset("紧凑", 4, 6),
    MarginPreset("标准", 8, 10),
    MarginPreset("疏朗", 14, 16)
)

private val BG_SWATCHES = listOf(
    "#FFFFFF" to "纯白",
    "#F7F3EC" to "米白",
    "#F6E7E0" to "浅粉",
    "#E7EFE4" to "浅绿",
    "#E4EAF2" to "浅蓝",
    "#1F2A24" to "墨绿",
    "#111111" to "纯黑"
)

private val FG_SWATCHES = listOf(
    "#2B2B2B" to "墨黑",
    "#5A5248" to "棕灰",
    "#B4593C" to "赭红",
    "#3C5A6E" to "黛蓝",
    "#F2EFE8" to "米白"
)

@Composable
fun DiaryScreen(
    outputStore: OutputStore,
    onBack: () -> Unit,
    initialPhotoUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var font by remember { mutableStateOf(DiaryFont.entries.firstOrNull { it.name == prefs.getString("font", "") } ?: DiaryFont.WENKAI) }
    var ratio by remember { mutableStateOf(DiaryRatio.entries.firstOrNull { it.name == prefs.getString("ratio", "") } ?: DiaryRatio.PORTRAIT) }
    var background by remember { mutableStateOf(Color.parseColor(prefs.getString("bg", "#F7F3EC") ?: "#F7F3EC")) }
    var foreground by remember { mutableStateOf(Color.parseColor(prefs.getString("fg", "#2B2B2B") ?: "#2B2B2B")) }
    var marginX by remember { mutableStateOf(prefs.getInt("marginX", 8)) }
    var topGap by remember { mutableStateOf(prefs.getInt("topGap", 10)) }
    var locked by remember { mutableStateOf(prefs.getBoolean("locked", false)) }
    var customPresets by remember { mutableStateOf(loadPresets(prefs)) }
    var fgTouched by remember { mutableStateOf(prefs.getBoolean("fgTouched", false)) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var sentence by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var showColorPickerForBg by remember { mutableStateOf(false) }
    var showColorPickerForFg by remember { mutableStateOf(false) }
    var showPresetNameDialog by remember { mutableStateOf(false) }

    val typefaces = remember { mutableStateMapOf<DiaryFont, Typeface>() }
    LaunchedEffect(Unit) {
        DiaryFont.entries.forEach { entry ->
            typefaces[entry] = withContext(Dispatchers.IO) {
                DiaryFonts.typeface(context, entry)
            }
        }
    }

    val typeface = typefaces[font] ?: Typeface.SERIF

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            photoUri = uri
            savedName = null
            error = null
        }
    }

    LaunchedEffect(initialPhotoUri) {
        if (initialPhotoUri != null && photoUri == null) photoUri = initialPhotoUri
    }

    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        photo = withContext(Dispatchers.IO) { loadPhoto(context, uri, 1600) }
        if (photo == null) error = "这张照片读不出来，换一张试试"
    }

    val budget = remember(ratio, marginX, topGap, photo) {
        DiaryRenderer.maxChars(ratio, marginX / 100f, topGap / 100f, photo)
    }

    LaunchedEffect(photo, sentence, dateMillis, ratio, background, foreground, marginX, topGap, typeface) {
        delay(90)
        preview = withContext(Dispatchers.Default) {
            DiaryRenderer.render(
                width = PREVIEW_WIDTH,
                ratio = ratio,
                photo = photo,
                sentence = sentence.trim(),
                dateLabel = dateLabel(dateMillis),
                background = background,
                foreground = foreground,
                marginX = marginX / 100f,
                topGap = topGap / 100f,
                fontFace = typeface
            )
        }
    }

    LaunchedEffect(font, ratio, background, foreground, marginX, topGap, locked, fgTouched) {
        prefs.edit()
            .putString("font", font.name)
            .putString("ratio", ratio.name)
            .putString("bg", hex(background))
            .putString("fg", hex(foreground))
            .putInt("marginX", marginX)
            .putInt("topGap", topGap)
            .putBoolean("locked", locked)
            .putBoolean("fgTouched", fgTouched)
            .apply()
    }

    Column(modifier.fillMaxSize()) {
        ToolboxTopBar(title = "图片日记", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            val cardShape = RoundedCornerShape(16.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .heightIn(max = 340.dp)
                        .aspectRatio(ratio.w.toFloat() / ratio.h, matchHeightConstraintsFirst = true)
                        .clip(cardShape)
                        .border(1.dp, p.hairline, cardShape)
                        .clickable { picker.launch(arrayOf("image/*")) }
                ) {
                    val bitmap = preview
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "日记卡片预览",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(p.pineSoft))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (photoUri == null) "轻点卡片选择照片" else "轻点卡片更换照片",
                style = ToolboxType.Caption.copy(fontSize = 10.sp),
                color = p.ink3
            )

            Spacer(Modifier.height(16.dp))
            Label("字体")
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiaryFont.entries.forEach { entry ->
                    Chip(
                        text = entry.label,
                        selected = entry == font,
                        onClick = { font = entry; savedName = null }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Label("画布比例")
            Spacer(Modifier.height(6.dp))
            PillSegments(
                items = DiaryRatio.entries.map { it.label },
                selected = DiaryRatio.entries.indexOf(ratio),
                onSelect = { ratio = DiaryRatio.entries[it]; savedName = null }
            )

            Spacer(Modifier.height(14.dp))
            Label("背景颜色")
            Spacer(Modifier.height(8.dp))
            SwatchRow(
                colors = BG_SWATCHES,
                selected = hex(background),
                onSelect = { hexValue ->
                    background = Color.parseColor(hexValue)
                    savedName = null
                    if (!fgTouched) foreground = autoForeground(background)
                },
                onCustom = { showColorPickerForBg = true }
            )

            Spacer(Modifier.height(14.dp))
            Label("文字颜色")
            Spacer(Modifier.height(8.dp))
            SwatchRow(
                colors = FG_SWATCHES,
                selected = hex(foreground),
                onSelect = { hexValue ->
                    foreground = Color.parseColor(hexValue)
                    fgTouched = true
                    savedName = null
                },
                onCustom = { showColorPickerForFg = true }
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("图片左右留白")
                Spacer(Modifier.weight(1f))
                Text("$marginX%", style = ToolboxType.Mono, color = p.pine)
            }
            Slider(
                value = marginX.toFloat(),
                onValueChange = { marginX = it.toInt(); savedName = null },
                valueRange = 0f..30f,
                enabled = !locked,
                colors = SliderDefaults.colors(
                    thumbColor = p.pine,
                    activeTrackColor = p.pine,
                    inactiveTrackColor = p.hairline
                )
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("图片上方留白")
                Spacer(Modifier.weight(1f))
                Text("$topGap%", style = ToolboxType.Mono, color = p.pine)
            }
            Slider(
                value = topGap.toFloat(),
                onValueChange = { topGap = it.toInt(); savedName = null },
                valueRange = 0f..30f,
                enabled = !locked,
                colors = SliderDefaults.colors(
                    thumbColor = p.pine,
                    activeTrackColor = p.pine,
                    inactiveTrackColor = p.hairline
                )
            )

            Text(
                text = if (locked) "留白已锁定 · 点我解锁" else "锁定留白，防误触",
                style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                color = if (locked) p.pine else p.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { locked = !locked }
                    .padding(vertical = 6.dp)
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("留白预设")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "存为预设",
                    style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                    color = p.pine,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPresetNameDialog = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (BUILT_IN_PRESETS + customPresets).forEach { preset ->
                    val active = preset.marginX == marginX && preset.topGap == topGap
                    Chip(
                        text = "${preset.name} ${preset.marginX}/${preset.topGap}",
                        selected = active,
                        onClick = {
                            marginX = preset.marginX
                            topGap = preset.topGap
                            savedName = null
                        },
                        onLongClick = if (preset in BUILT_IN_PRESETS) null else {
                            {
                                customPresets = customPresets - preset
                                savePresets(prefs, customPresets)
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("一句话")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${sentence.length} / $budget",
                    style = ToolboxType.Mono,
                    color = if (sentence.length >= budget) p.clay else p.ink3
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surface)
                    .border(1.dp, p.hairline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (sentence.isEmpty()) {
                    Text("今天想记住什么？", style = ToolboxType.Body, color = p.ink3)
                }
                BasicTextField(
                    value = sentence,
                    onValueChange = {
                        if (it.length <= budget) {
                            sentence = it
                            savedName = null
                        }
                    },
                    textStyle = ToolboxType.Body.copy(color = p.ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(p.pine),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(14.dp))
            Label("日期")
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surface)
                    .border(1.dp, p.hairline, RoundedCornerShape(12.dp))
                    .clickable { showDatePicker(context, dateMillis) { dateMillis = it; savedName = null } }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateLabel(dateMillis), style = ToolboxType.Body, color = p.ink)
                Spacer(Modifier.weight(1f))
                Text("更改", style = ToolboxType.Caption.copy(fontSize = 10.5.sp), color = p.pine)
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, style = ToolboxType.Caption.copy(fontSize = 10.5.sp), color = p.clay)
            }

            Spacer(Modifier.height(20.dp))
        }

        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            PrimaryCTA(
                text = if (saving) "正在保存…" else "保存到相册",
                hint = "$EXPORT_WIDTH × ${DiaryRenderer.layoutFor(ratio, marginX / 100f, topGap / 100f, photo?.let { it.width.toFloat() / it.height }).height} · PNG",
                enabled = !saving && photo != null,
                onClick = {
                    if (photo == null || saving) return@PrimaryCTA
                    scope.launch {
                        saving = true
                        error = null
                        savedName = null
                        runCatching {
                            val rendered = withContext(Dispatchers.Default) {
                                DiaryRenderer.render(
                                    width = EXPORT_WIDTH,
                                    ratio = ratio,
                                    photo = photo,
                                    sentence = sentence.trim(),
                                    dateLabel = dateLabel(dateMillis),
                                    background = background,
                                    foreground = foreground,
                                    marginX = marginX / 100f,
                                    topGap = topGap / 100f,
                                    fontFace = typeface
                                )
                            }
                            val name = "日记_" +
                                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
                            val handle = withContext(Dispatchers.IO) {
                                val finalName = outputStore.resolveAvailableName(name, "image/png", PHOTO_DIR)
                                outputStore.create(finalName, "image/png", PHOTO_DIR)
                            }
                            val written = withContext(Dispatchers.IO) {
                                runCatching {
                                    handle.openStream().use { stream ->
                                        rendered.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    }
                                }.getOrDefault(false)
                            }
                            if (written) {
                                handle.commit()
                                savedName = handle.displayName
                            } else {
                                handle.abort()
                                error = "保存失败，请检查存储空间"
                            }
                        }.onFailure { error = it.message ?: "保存失败" }
                        saving = false
                    }
                }
            )
            if (savedName != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已保存到相册 · $savedName",
                    style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
                    color = p.pine,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showColorPickerForBg) {
        ColorPickerDialog(
            initial = background,
            title = "背景颜色",
            onDismiss = { showColorPickerForBg = false },
            onPick = {
                background = it
                savedName = null
                if (!fgTouched) foreground = autoForeground(it)
                showColorPickerForBg = false
            }
        )
    }

    if (showColorPickerForFg) {
        ColorPickerDialog(
            initial = foreground,
            title = "文字颜色",
            onDismiss = { showColorPickerForFg = false },
            onPick = {
                foreground = it
                fgTouched = true
                savedName = null
                showColorPickerForFg = false
            }
        )
    }

    if (showPresetNameDialog) {
        PresetNameDialog(
            onDismiss = { showPresetNameDialog = false },
            onConfirm = { name ->
                customPresets = customPresets + MarginPreset(name, marginX, topGap)
                savePresets(prefs, customPresets)
                showPresetNameDialog = false
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) p.pine else p.hairline,
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
            color = if (selected) p.ink else p.ink2
        )
    }
}

@Composable
private fun SwatchRow(
    colors: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit
) {
    val p = LocalPalette.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { (hexValue, label) ->
            val active = hexValue.equals(selected, ignoreCase = true)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ComposeColor(android.graphics.Color.parseColor(hexValue)))
                    .border(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) p.pine else p.hairline,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hexValue) }
            )
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(p.surface)
                .border(1.dp, p.hairline, CircleShape)
                .clickable(onClick = onCustom),
            contentAlignment = Alignment.Center
        ) {
            Text("+", style = ToolboxType.Body, color = p.ink2)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = ToolboxType.Caption.copy(
            fontSize = 10.5.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold
        ),
        color = LocalPalette.current.ink3
    )
}

@Composable
private fun ColorPickerDialog(
    initial: Int,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    val hsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    val color = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ComposeColor(color))
                        .border(1.dp, LocalPalette.current.hairline, RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text("色相", style = ToolboxType.Caption, color = LocalPalette.current.ink3)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("饱和度", style = ToolboxType.Caption, color = LocalPalette.current.ink3)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("明度", style = ToolboxType.Caption, color = LocalPalette.current.ink3)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color) }) { Text("使用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PresetNameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存留白预设") },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 10) name = it },
                singleLine = true,
                textStyle = ToolboxType.Body.copy(color = LocalPalette.current.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalPalette.current.surface)
                    .border(1.dp, LocalPalette.current.hairline, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun autoForeground(background: Int): Int {
    val luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0
    return if (luminance < 0.5) Color.parseColor("#F2EFE8") else Color.parseColor("#2B2B2B")
}

private fun hex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)

private fun loadPresets(prefs: android.content.SharedPreferences): List<MarginPreset> {
    val raw = prefs.getString("presets", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split(";").mapNotNull { entry ->
        val parts = entry.split(",")
        if (parts.size == 3) {
            val mx = parts[1].toIntOrNull()
            val tg = parts[2].toIntOrNull()
            if (mx != null && tg != null) MarginPreset(parts[0], mx, tg) else null
        } else null
    }
}

private fun savePresets(prefs: android.content.SharedPreferences, presets: List<MarginPreset>) {
    val raw = presets.joinToString(";") { "${it.name},${it.marginX},${it.topGap}" }
    prefs.edit().putString("presets", raw).apply()
}

private fun showDatePicker(context: Context, initial: Long, onPicked: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            val picked = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
            }
            onPicked(picked.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun dateLabel(millis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return String.format(
        Locale.US,
        "%04d-%02d-%02d（%s）",
        year, month, day, season(month)
    )
}

private fun season(month: Int): String = when (month) {
    3, 4, 5 -> "春"
    6, 7, 8 -> "夏"
    9, 10, 11 -> "秋"
    else -> "冬"
}

private fun loadPhoto(context: Context, uri: Uri, maxWidth: Int): Bitmap? {
    return runCatching {
        if (uri.scheme == "file") {
            return@runCatching uri.path?.let { decodeFromFile(it, maxWidth) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (info.size.width > maxWidth) {
                    val scale = maxWidth.toFloat() / info.size.width
                    decoder.setTargetSize(maxWidth, (info.size.height * scale).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > maxWidth * 2 && sample < 16) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val orientation = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            rotate(decoded, orientation)
        }
    }.getOrNull()
}

private fun decodeFromFile(path: String, maxWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxWidth * 2 && sample < 16) sample *= 2
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return rotate(decoded, orientation)
}

private fun rotate(source: Bitmap, orientation: Int): Bitmap {
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> return source
    }
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}