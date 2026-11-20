package com.toolbox.feature.diary

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

private const val MAX_SENTENCE = 60
private const val EXPORT_WIDTH = 1200
private const val PREVIEW_WIDTH = 600
private const val PHOTO_DIR = "Pictures/图片日记"

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

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var sentence by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var ratio by remember { mutableStateOf(DiaryRatio.PORTRAIT) }
    var theme by remember { mutableStateOf(DiaryTheme.PAPER) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
        if (initialPhotoUri != null && photoUri == null) {
            photoUri = initialPhotoUri
        }
    }

    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        photo = withContext(Dispatchers.IO) { loadPhoto(context, uri, 1600) }
        if (photo == null) error = "这张照片读不出来，换一张试试"
    }

    LaunchedEffect(photo, sentence, dateMillis, ratio, theme) {
        delay(90)
        preview = withContext(Dispatchers.Default) {
            DiaryRenderer.render(
                width = PREVIEW_WIDTH,
                ratio = ratio,
                theme = theme,
                photo = photo,
                sentence = sentence.trim(),
                dateLine = dateLine(dateMillis)
            )
        }
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
                        .heightIn(max = 360.dp)
                        .aspectRatio(
                            ratio.w.toFloat() / ratio.h,
                            matchHeightConstraintsFirst = true
                        )
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
            Label("画布比例")
            Spacer(Modifier.height(6.dp))
            PillSegments(
                items = DiaryRatio.entries.map { it.label },
                selected = DiaryRatio.entries.indexOf(ratio),
                onSelect = { ratio = DiaryRatio.entries[it]; savedName = null }
            )

            Spacer(Modifier.height(14.dp))
            Label("配色")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DiaryTheme.entries.forEach { entry ->
                    ThemeChip(
                        theme = entry,
                        selected = entry == theme,
                        onClick = { theme = entry; savedName = null }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("一句话")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${sentence.length} / $MAX_SENTENCE",
                    style = ToolboxType.Mono,
                    color = p.ink3
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
                        if (it.length <= MAX_SENTENCE) {
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
                Text(dateLine(dateMillis), style = ToolboxType.Body, color = p.ink)
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
                hint = "$EXPORT_WIDTH × ${DiaryRenderer.heightFor(EXPORT_WIDTH, ratio)} · PNG",
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
                                    theme = theme,
                                    photo = photo,
                                    sentence = sentence.trim(),
                                    dateLine = dateLine(dateMillis)
                                )
                            }
                            val name = "日记_" +
                                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                                ".png"
                            val handle = withContext(Dispatchers.IO) {
                                val finalName = outputStore.resolveAvailableName(
                                    name, "image/png", PHOTO_DIR
                                )
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
private fun ThemeChip(
    theme: DiaryTheme,
    selected: Boolean,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(theme.background))
                .border(1.dp, p.hairline, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = theme.label,
            style = ToolboxType.Caption.copy(fontSize = 10.5.sp),
            color = if (selected) p.ink else p.ink2
        )
    }
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

private fun dateLine(millis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return "${year}年${month}月${day}日 · ${season(month)}"
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
    }.onFailure { Log.e("Diary", "load photo failed", it) }.getOrNull()
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