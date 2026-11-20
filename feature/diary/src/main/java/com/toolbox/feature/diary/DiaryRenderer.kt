package com.toolbox.feature.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.util.concurrent.ConcurrentHashMap

enum class DiaryFont(val label: String, val asset: String) {
    WENKAI("文楷·日常", "fonts/LXGWWenKaiScreen.ttf"),
    MASHANZHENG("马善政·毛笔楷", "fonts/MaShanZheng.ttf"),
    KUAILES("站酷快乐体", "fonts/ZCOOLKuaiLe.ttf"),
    LONGCANG("龙藏·钢笔行", "fonts/LongCang.ttf"),
    ZHIMANG("志莽·行书", "fonts/ZhiMangXing.ttf"),
    NOTO("思源宋体", "fonts/NotoSerifSC.ttf")
}

object DiaryFonts {

    private val cache = ConcurrentHashMap<DiaryFont, Typeface>()

    fun typeface(context: Context, font: DiaryFont): Typeface {
        cache[font]?.let { return it }
        val base = Typeface.createFromAsset(context.assets, font.asset)
        cache[font] = base
        return base
    }
}

enum class DiaryRatio(val label: String, val w: Int, val h: Int) {
    WIDE("3:2", 3, 2),
    LANDSCAPE("4:3", 4, 3),
    SQUARE("1:1", 1, 1),
    PORTRAIT("3:4", 3, 4),
    TALL("2:3", 2, 3),
    STORY("9:16", 9, 16)
}

/** 与原项目一致：留白按画布百分比计算，图片 contain 不裁切。 */
data class DiaryLayout(
    val width: Int,
    val height: Int,
    val margin: Float,
    val imgWidth: Float,
    val imgTop: Float,
    val maxImgHeight: Float,
    val fontSize: Float,
    val lineHeight: Float,
    val textTop: Float,
    val dateBaseline: Float,
    val dateFontSize: Float,
    val maxLines: Int,
    val maxChars: Int
)

object DiaryRenderer {

    private const val W = 1200

    fun layoutFor(
        ratio: DiaryRatio,
        marginX: Float,
        topGap: Float,
        photoAspect: Float?
    ): DiaryLayout {
        val height = (W.toFloat() * ratio.h / ratio.w).toInt()
        val margin = (W * marginX).toInt().toFloat()
        val imgWidth = W - margin * 2
        val imgTop = (height * topGap).toInt().toFloat()
        val maxImgHeight = maxOf(
            (height * 0.15f).toInt().toFloat(),
            minOf(
                (height * 0.56f).toInt().toFloat(),
                height - imgTop - (height * 0.27f).toInt()
            )
        )

        val drawHeight = if (photoAspect != null && photoAspect > 0f) {
            minOf(imgWidth / photoAspect, maxImgHeight)
        } else {
            minOf((height * 0.42f).toInt().toFloat(), maxImgHeight)
        }

        val fontSize = (W * 0.036f).toInt().toFloat()
        val lineHeight = (fontSize * 1.8f).toInt().toFloat()
        val textTop = imgTop + drawHeight + (height * 0.10f).toInt()
        val dateBaseline = (height - (height * 0.075f).toInt()).toFloat()
        val dateFontSize = (W * 0.026f).toInt().toFloat()
        val dateTop = dateBaseline - dateFontSize - 12
        val maxLines = maxOf(1, (((dateTop - 10 - textTop) / lineHeight).toInt()) + 1)
        val charsPerLine = maxOf(8, (imgWidth / fontSize).toInt())

        return DiaryLayout(
            width = W,
            height = height,
            margin = margin,
            imgWidth = imgWidth,
            imgTop = imgTop,
            maxImgHeight = maxImgHeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            textTop = textTop,
            dateBaseline = dateBaseline,
            dateFontSize = dateFontSize,
            maxLines = maxLines,
            maxChars = maxLines * charsPerLine
        )
    }

    fun maxChars(ratio: DiaryRatio, marginX: Float, topGap: Float, photo: Bitmap?): Int =
        layoutFor(ratio, marginX, topGap, photo?.let { it.width.toFloat() / it.height }).maxChars

    fun render(
        width: Int,
        ratio: DiaryRatio,
        photo: Bitmap?,
        sentence: String,
        dateLabel: String,
        background: Int,
        foreground: Int,
        marginX: Float,
        topGap: Float,
        fontFace: Typeface
    ): Bitmap {
        val height = (width.toFloat() * ratio.h / ratio.w).toInt()
        val scaleFactor = width.toFloat() / W
        val layout = layoutFor(ratio, marginX, topGap, photo?.let { it.width.toFloat() / it.height })

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        canvas.scale(scaleFactor, scaleFactor)

        var drawHeight = 0f

        if (photo != null) {
            val scale = minOf(
                layout.imgWidth / photo.width,
                layout.maxImgHeight / photo.height
            )
            val drawWidth = (photo.width * scale).toInt().toFloat()
            drawHeight = (photo.height * scale).toInt().toFloat()
            val left = layout.margin + ((layout.imgWidth - drawWidth) / 2f).toInt()
            canvas.drawBitmap(
                photo,
                null,
                RectF(left, layout.imgTop, left + drawWidth, layout.imgTop + drawHeight),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            )
        } else {
            drawHeight = minOf((layout.height * 0.42f).toInt().toFloat(), layout.maxImgHeight)
            val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(90, 120, 110, 95)
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            canvas.drawRect(
                layout.margin,
                layout.imgTop,
                layout.margin + layout.imgWidth,
                layout.imgTop + drawHeight,
                dashed
            )
            val hint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 36f
                color = Color.argb(128, 120, 110, 95)
                textAlign = Paint.Align.CENTER
                typeface = fontFace
            }
            canvas.drawText(
                "选 一 张 今 天 的 照 片",
                W / 2f,
                layout.imgTop + drawHeight / 2f + 12f,
                hint
            )
        }

        if (sentence.isNotBlank()) {
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = fontFace
                textSize = layout.fontSize
                color = foreground
            }
            val textWidth = layout.imgWidth.toInt()
            val textLayout = StaticLayout.Builder
                .obtain(sentence, 0, sentence.length, textPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.8f)
                .setMaxLines(layout.maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(layout.margin, layout.textTop - layout.fontSize)
            textLayout.draw(canvas)
            canvas.restore()
        }

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = fontFace
            textSize = layout.dateFontSize
            color = foreground
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            dateLabel,
            W - layout.margin,
            layout.dateBaseline,
            datePaint
        )

        return bitmap
    }
}