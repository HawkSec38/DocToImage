package com.example.engine.renderers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.example.data.model.PaperTheme
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedPage
import java.io.InputStream

/**
 * Direct-to-Bitmap Rich Text Format (RTF) Renderer.
 * Tokenizes RTF control words (\b, \i, \ul, \fs, \par) directly into styled Spannable
 * layouts and draws directly to Bitmaps without intermediate PDF conversion.
 */
class RtfRenderer {

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val raw = inputStream.bufferedReader().use { it.readText() }
        val spanned = parseRtfToSpannable(raw, options)

        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val pageWidth = (1240 * (scale / 1.5f)).toInt()
        val pageHeight = (1754 * (scale / 1.5f)).toInt()
        val marginX = (90 * (scale / 1.5f))
        val marginY = (110 * (scale / 1.5f))
        val contentWidth = (pageWidth - marginX * 2).toInt()

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f * 1.33f * scale
            color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)
        }

        val fullLayout = StaticLayout.Builder.obtain(
            spanned, 0, spanned.length, textPaint, contentWidth
        ).setLineSpacing(6f * (scale / 1.5f), 1.15f).setIncludePad(false).build()

        val usableHeight = (pageHeight - marginY * 2).toInt()
        val totalPages = ((fullLayout.height + usableHeight - 1) / usableHeight).coerceAtLeast(1)
        val pages = mutableListOf<RenderedPage>()

        for (p in 0 until totalPages) {
            val pageBmp = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBmp)

            val bg = when (options.paperTheme) {
                PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(24, 28, 36)
                PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
                PaperTheme.WHITE -> android.graphics.Color.WHITE
            }
            canvas.drawColor(bg)

            // Purple brand accent ribbon
            val ribbonP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(106, 27, 154)
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f * (scale / 1.5f), ribbonP)

            val pageTopContentOffset = p * usableHeight
            canvas.save()
            canvas.clipRect(marginX, marginY, marginX + contentWidth, marginY + usableHeight)
            canvas.translate(marginX, marginY - pageTopContentOffset)
            fullLayout.draw(canvas)
            canvas.restore()

            if (options.showPageNumbers) {
                val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.GRAY else android.graphics.Color.rgb(140, 145, 155)
                    textSize = 13f * scale
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("- Page ${p + 1} of $totalPages -", pageWidth / 2f, pageHeight - 35f, stampPaint)
            }

            pages.add(
                RenderedPage(
                    pageNumber = p + 1,
                    title = "Page ${p + 1}",
                    bitmap = pageBmp,
                    widthPx = pageWidth,
                    heightPx = pageHeight
                )
            )
        }

        return pages
    }

    private fun parseRtfToSpannable(rtf: String, options: RenderOptions): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        var i = 0
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var currentFontSize = 13f

        while (i < rtf.length) {
            val c = rtf[i]
            if (c == '\\') {
                i++
                val word = StringBuilder()
                while (i < rtf.length && (rtf[i].isLetter() || rtf[i].isDigit() || rtf[i] == '-')) {
                    word.append(rtf[i])
                    i++
                }
                if (i < rtf.length && rtf[i] == ' ') {
                    i++ // skip delimiter space
                }
                val cmd = word.toString()
                when {
                    cmd == "b" -> isBold = true
                    cmd == "b0" -> isBold = false
                    cmd == "i" -> isItalic = true
                    cmd == "i0" -> isItalic = false
                    cmd == "ul" -> isUnderline = true
                    cmd == "ulnone" -> isUnderline = false
                    cmd == "par" -> ssb.append("\n")
                    cmd.startsWith("fs") -> {
                        val pts = cmd.removePrefix("fs").toFloatOrNull()
                        if (pts != null) {
                            currentFontSize = pts / 2f
                        }
                    }
                }
            } else if (c == '{' || c == '}') {
                i++ // skip group brackets
            } else {
                val start = ssb.length
                ssb.append(c)
                val end = ssb.length

                val style = when {
                    isBold && isItalic -> Typeface.BOLD_ITALIC
                    isBold -> Typeface.BOLD
                    isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                if (style != Typeface.NORMAL) {
                    ssb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (isUnderline) {
                    ssb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val px = (currentFontSize * 1.33f * options.resolutionScale).toInt()
                ssb.setSpan(AbsoluteSizeSpan(px), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                i++
            }
        }
        return ssb
    }
}
