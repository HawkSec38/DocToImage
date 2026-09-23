package com.example.engine.renderers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Html
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.model.PaperTheme
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedPage
import java.io.InputStream

/**
 * Direct-to-Bitmap HTML Document Renderer.
 * Parses HTML text, formatting tags, lists, headings and inline styles directly
 * to high-resolution page Bitmaps without converting to PDF or using WebView.
 */
class HtmlRenderer {

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val htmlContent = inputStream.bufferedReader().use { it.readText() }
        return renderHtml(htmlContent, options)
    }

    fun renderHtml(html: String, options: RenderOptions): List<RenderedPage> {
        val cleanHtml = cleanHtmlString(html)
        val spanned: Spanned = Html.fromHtml(cleanHtml, Html.FROM_HTML_MODE_COMPACT)

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

        val pages = mutableListOf<RenderedPage>()
        val usableHeight = (pageHeight - marginY * 2).toInt()
        val totalHeight = fullLayout.height
        val totalPages = ((totalHeight + usableHeight - 1) / usableHeight).coerceAtLeast(1)

        for (p in 0 until totalPages) {
            val pageBmp = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBmp)

            val bg = when (options.paperTheme) {
                PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(24, 28, 36)
                PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
                PaperTheme.WHITE -> android.graphics.Color.WHITE
            }
            canvas.drawColor(bg)

            // Rust orange brand accent ribbon
            val ribbonP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(216, 67, 21)
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f * (scale / 1.5f), ribbonP)

            val pageTopContentOffset = p * usableHeight

            canvas.save()
            // Clip to page content bounds
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

    private fun cleanHtmlString(raw: String): String {
        var str = raw
        // Strip script and head blocks
        str = str.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        str = str.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        return str
    }
}
