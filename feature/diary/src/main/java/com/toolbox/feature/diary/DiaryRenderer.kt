package com.toolbox.feature.diary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

enum class DiaryTheme(
    val label: String,
    val background: Int,
    val ink: Int,
    val subtle: Int
) {
    PAPER("纸白", 0xFFF7F5EF.toInt(), 0xFF17170F.toInt(), 0xFF8A887C.toInt()),
    INK("墨黑", 0xFF14140F.toInt(), 0xFFF2F1E9.toInt(), 0xFFA7A499.toInt()),
    DUSK("暮色", 0xFF2E2622.toInt(), 0xFFF0E7DB.toInt(), 0xFFBAAEA0.toInt())
}

enum class DiaryRatio(val label: String, val w: Int, val h: Int) {
    WIDE("3:2", 3, 2),
    LANDSCAPE("4:3", 4, 3),
    SQUARE("1:1", 1, 1),
    PORTRAIT("3:4", 3, 4),
    TALL("2:3", 2, 3),
    STORY("9:16", 9, 16)
}

/**
 * 卡片直绘：照片 contain 不裁切，一句话居中自动换行，日期带字距。
 * 预览与导出走同一个函数，只是 width 不同，所以所见即所得。
 */
object DiaryRenderer {

    fun heightFor(width: Int, ratio: DiaryRatio): Int =
        (width.toFloat() * ratio.h / ratio.w).toInt()

    fun render(
        width: Int,
        ratio: DiaryRatio,
        theme: DiaryTheme,
        photo: Bitmap?,
        sentence: String,
        dateLine: String
    ): Bitmap {
        val height = heightFor(width, ratio)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(theme.background)

        val marginX = width * 0.075f
        val marginY = height * 0.06f
        val photoHeight = height * 0.60f
        val photoArea = RectF(marginX, marginY, width - marginX, marginY + photoHeight)

        if (photo != null) {
            val scale = minOf(
                photoArea.width() / photo.width,
                photoArea.height() / photo.height
            )
            val drawWidth = photo.width * scale
            val drawHeight = photo.height * scale
            val left = photoArea.centerX() - drawWidth / 2f
            val top = photoArea.centerY() - drawHeight / 2f
            canvas.drawBitmap(
                photo,
                null,
                RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = width * 0.0025f
                color = theme.subtle
            }
            canvas.drawRect(photoArea, placeholder)
            val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = width * 0.03f
                color = theme.subtle
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("选择一张照片", photoArea.centerX(), photoArea.centerY(), hint)
        }

        val textTop = photoArea.bottom + height * 0.055f
        if (sentence.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.SERIF
                textSize = width * 0.045f
                color = theme.ink
            }
            val textWidth = (width - marginX * 2f).toInt()
            val layout = StaticLayout.Builder
                .obtain(sentence, 0, sentence.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.45f)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(marginX, textTop)
            layout.draw(canvas)
            canvas.restore()
        }

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.SERIF
            textSize = width * 0.026f
            color = theme.subtle
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.14f
        }
        canvas.drawText(dateLine, width / 2f, height - marginY * 0.9f, datePaint)

        return bitmap
    }
}