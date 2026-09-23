package com.example.engine.renderers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.example.data.model.PaperTheme
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedPage
import java.io.InputStream

/**
 * Direct-to-Bitmap Markdown (MD) Renderer.
 * Parses headers (#, ##, ###), bold/italic spans, code blocks (```), blockquotes (>),
 * bullet lists (-), and tables (|) directly into paginated document Bitmaps.
 */
class MarkdownRenderer {

    sealed class MdBlock {
        data class Heading(val level: Int, val text: String) : MdBlock()
        data class Paragraph(val text: String) : MdBlock()
        data class BulletItem(val text: String) : MdBlock()
        data class NumberedItem(val number: String, val text: String) : MdBlock()
        data class CodeBlock(val language: String, val code: String) : MdBlock()
        data class Blockquote(val text: String) : MdBlock()
        data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
        object HorizontalRule : MdBlock()
    }

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val content = inputStream.bufferedReader().use { it.readText() }
        val blocks = parseMarkdown(content)
        return paginateAndRender(blocks, options)
    }

    fun renderText(content: String, options: RenderOptions): List<RenderedPage> {
        val blocks = parseMarkdown(content)
        return paginateAndRender(blocks, options)
    }

    private fun parseMarkdown(raw: String): List<MdBlock> {
        val lines = raw.lines()
        val blocks = mutableListOf<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Code block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                if (i < lines.size) i++ // skip ending ```
                blocks.add(MdBlock.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                continue
            }

            // Heading
            if (trimmed.startsWith("#")) {
                var level = 0
                while (level < trimmed.length && trimmed[level] == '#') {
                    level++
                }
                val headingText = trimmed.substring(level).trim()
                blocks.add(MdBlock.Heading(level.coerceIn(1, 4), headingText))
                i++
                continue
            }

            // Horizontal rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                blocks.add(MdBlock.HorizontalRule)
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                blocks.add(MdBlock.Blockquote(quoteText))
                i++
                continue
            }

            // Bullet list
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                blocks.add(MdBlock.BulletItem(trimmed.substring(2).trim()))
                i++
                continue
            }

            // Numbered list
            val numMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
            if (numMatch != null) {
                blocks.add(MdBlock.NumberedItem(numMatch.groupValues[1], numMatch.groupValues[2]))
                i++
                continue
            }

            // Markdown Table
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    tableLines.add(lines[i].trim())
                    i++
                }
                if (tableLines.size >= 2) {
                    val headerCells = tableLines[0].split("|").filter { it.isNotBlank() }.map { it.trim() }
                    val dataRows = mutableListOf<List<String>>()
                    for (rowIdx in 2 until tableLines.size) { // skip separator row (|---|---|)
                        val cells = tableLines[rowIdx].split("|").filter { it.isNotBlank() }.map { it.trim() }
                        if (cells.isNotEmpty()) dataRows.add(cells)
                    }
                    blocks.add(MdBlock.Table(headerCells, dataRows))
                    continue
                }
            }

            // Default Paragraph
            blocks.add(MdBlock.Paragraph(trimmed))
            i++
        }

        return blocks
    }

    private fun paginateAndRender(blocks: List<MdBlock>, options: RenderOptions): List<RenderedPage> {
        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val pageWidth = (1240 * (scale / 1.5f)).toInt()
        val pageHeight = (1754 * (scale / 1.5f)).toInt()
        val marginX = (90 * (scale / 1.5f))
        val marginY = (110 * (scale / 1.5f))
        val contentWidth = (pageWidth - marginX * 2).toInt()

        val pages = mutableListOf<RenderedPage>()
        var currentPageNumber = 1
        var currentY = marginY

        var currentBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        var currentCanvas = Canvas(currentBitmap)
        drawPageBackground(currentCanvas, pageWidth, pageHeight, options)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun finishPage() {
            if (options.showPageNumbers) {
                drawPageStamp(currentCanvas, currentPageNumber, pageWidth, pageHeight, options)
            }
            pages.add(
                RenderedPage(
                    pageNumber = currentPageNumber,
                    title = "Page $currentPageNumber",
                    bitmap = currentBitmap,
                    widthPx = pageWidth,
                    heightPx = pageHeight
                )
            )
            currentPageNumber++
            currentBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            currentCanvas = Canvas(currentBitmap)
            drawPageBackground(currentCanvas, pageWidth, pageHeight, options)
            currentY = marginY
        }

        val defaultTextColor = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)

        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> {
                    val fontSizePt = when (block.level) {
                        1 -> 24f
                        2 -> 19f
                        3 -> 16f
                        else -> 14f
                    }
                    val headingColor = when (block.level) {
                        1 -> android.graphics.Color.rgb(0, 131, 143) // Teal Accent
                        2 -> if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(128, 203, 196) else android.graphics.Color.rgb(0, 96, 100)
                        else -> defaultTextColor
                    }

                    textPaint.textSize = fontSizePt * 1.33f * scale
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    textPaint.color = headingColor

                    val layout = StaticLayout.Builder.obtain(
                        block.text, 0, block.text.length, textPaint, contentWidth
                    ).setIncludePad(false).build()

                    val blockHeight = layout.height + 28f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginX, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    if (block.level == 1) {
                        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.rgb(0, 131, 143)
                            strokeWidth = 3f * (scale / 1.5f)
                        }
                        currentCanvas.drawLine(marginX, currentY + layout.height + 6f, marginX + 100f * (scale / 1.5f), currentY + layout.height + 6f, barPaint)
                    }

                    currentY += blockHeight
                }
                is MdBlock.Paragraph -> {
                    val spannable = parseInlineFormatting(block.text, defaultTextColor, 13f * 1.33f * scale)
                    textPaint.textSize = 13f * 1.33f * scale
                    textPaint.typeface = Typeface.DEFAULT
                    textPaint.color = defaultTextColor

                    val layout = StaticLayout.Builder.obtain(
                        spannable, 0, spannable.length, textPaint, contentWidth
                    ).setLineSpacing(6f * (scale / 1.5f), 1.15f).setIncludePad(false).build()

                    val blockHeight = layout.height + 14f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginX, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is MdBlock.BulletItem -> {
                    val spannable = parseInlineFormatting("•  " + block.text, defaultTextColor, 13f * 1.33f * scale)
                    textPaint.textSize = 13f * 1.33f * scale
                    textPaint.typeface = Typeface.DEFAULT

                    val layout = StaticLayout.Builder.obtain(
                        spannable, 0, spannable.length, textPaint, (contentWidth - 24f).toInt()
                    ).setLineSpacing(4f * (scale / 1.5f), 1.15f).build()

                    val blockHeight = layout.height + 8f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginX + 20f, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is MdBlock.NumberedItem -> {
                    val spannable = parseInlineFormatting("${block.number}.  ${block.text}", defaultTextColor, 13f * 1.33f * scale)
                    textPaint.textSize = 13f * 1.33f * scale

                    val layout = StaticLayout.Builder.obtain(
                        spannable, 0, spannable.length, textPaint, (contentWidth - 24f).toInt()
                    ).setLineSpacing(4f * (scale / 1.5f), 1.15f).build()

                    val blockHeight = layout.height + 8f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginX + 20f, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is MdBlock.CodeBlock -> {
                    val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.MONOSPACE
                        textSize = 11.5f * 1.33f * scale
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(129, 212, 250) else android.graphics.Color.rgb(26, 35, 126)
                    }

                    val codeLayout = StaticLayout.Builder.obtain(
                        block.code, 0, block.code.length, codePaint, (contentWidth - 40f).toInt()
                    ).setIncludePad(false).build()

                    val blockHeight = codeLayout.height + 36f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    // Background Box for Code
                    shapePaint.color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(35, 42, 54) else android.graphics.Color.rgb(243, 245, 248)
                    shapePaint.style = Paint.Style.FILL
                    val boxRect = RectF(marginX, currentY, marginX + contentWidth, currentY + blockHeight - 10f)
                    currentCanvas.drawRoundRect(boxRect, 12f, 12f, shapePaint)

                    // Left indicator stripe
                    shapePaint.color = android.graphics.Color.rgb(0, 131, 143)
                    currentCanvas.drawRoundRect(RectF(marginX, currentY, marginX + 6f, currentY + blockHeight - 10f), 4f, 4f, shapePaint)

                    // Draw Code text
                    currentCanvas.save()
                    currentCanvas.translate(marginX + 20f, currentY + 14f)
                    codeLayout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is MdBlock.Blockquote -> {
                    val spannable = parseInlineFormatting(block.text, if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY, 13f * 1.33f * scale)
                    textPaint.textSize = 13f * 1.33f * scale

                    val layout = StaticLayout.Builder.obtain(
                        spannable, 0, spannable.length, textPaint, (contentWidth - 40f).toInt()
                    ).build()

                    val blockHeight = layout.height + 16f * (scale / 1.5f)
                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    // Draw quote bar
                    shapePaint.color = android.graphics.Color.rgb(180, 185, 195)
                    shapePaint.strokeWidth = 4f * (scale / 1.5f)
                    shapePaint.style = Paint.Style.STROKE
                    currentCanvas.drawLine(marginX + 8f, currentY, marginX + 8f, currentY + layout.height, shapePaint)

                    currentCanvas.save()
                    currentCanvas.translate(marginX + 28f, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is MdBlock.HorizontalRule -> {
                    if (currentY + 20f > pageHeight - marginY) {
                        finishPage()
                    }
                    val hrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(220, 224, 230)
                        strokeWidth = 1.5f * (scale / 1.5f)
                    }
                    currentCanvas.drawLine(marginX, currentY + 10f, marginX + contentWidth, currentY + 10f, hrPaint)
                    currentY += 24f * (scale / 1.5f)
                }
                is MdBlock.Table -> {
                    val colCount = block.headers.size.coerceAtLeast(1)
                    val colW = contentWidth.toFloat() / colCount
                    val cellPad = 10f * (scale / 1.5f)

                    val tableBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(210, 215, 225)
                        strokeWidth = 1f * (scale / 1.5f)
                        style = Paint.Style.STROKE
                    }
                    val tableHdrBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(40, 50, 65) else android.graphics.Color.rgb(235, 240, 245)
                    }

                    // Header Row
                    val rowHeight = 38f * (scale / 1.5f)
                    if (currentY + rowHeight > pageHeight - marginY) {
                        finishPage()
                    }

                    for (c in 0 until colCount) {
                        val cLeft = marginX + c * colW
                        val rect = RectF(cLeft, currentY, cLeft + colW, currentY + rowHeight)
                        currentCanvas.drawRect(rect, tableHdrBg)
                        currentCanvas.drawRect(rect, tableBorderPaint)
                        textPaint.textSize = 12f * 1.33f * scale
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                        textPaint.color = defaultTextColor
                        val hText = block.headers.getOrElse(c) { "" }
                        currentCanvas.drawText(hText, cLeft + cellPad, currentY + rowHeight / 2f + 5f, textPaint)
                    }
                    currentY += rowHeight

                    // Data Rows
                    for (row in block.rows) {
                        if (currentY + rowHeight > pageHeight - marginY) {
                            finishPage()
                        }
                        for (c in 0 until colCount) {
                            val cLeft = marginX + c * colW
                            val rect = RectF(cLeft, currentY, cLeft + colW, currentY + rowHeight)
                            currentCanvas.drawRect(rect, tableBorderPaint)
                            textPaint.textSize = 12f * 1.33f * scale
                            textPaint.typeface = Typeface.DEFAULT
                            textPaint.color = defaultTextColor
                            val cText = row.getOrElse(c) { "" }
                            currentCanvas.drawText(cText, cLeft + cellPad, currentY + rowHeight / 2f + 5f, textPaint)
                        }
                        currentY += rowHeight
                    }
                    currentY += 16f * (scale / 1.5f)
                }
            }
        }

        // Finish last page
        if (options.showPageNumbers) {
            drawPageStamp(currentCanvas, currentPageNumber, pageWidth, pageHeight, options)
        }
        pages.add(
            RenderedPage(
                pageNumber = currentPageNumber,
                title = "Page $currentPageNumber",
                bitmap = currentBitmap,
                widthPx = pageWidth,
                heightPx = pageHeight
            )
        )

        return pages
    }

    private fun parseInlineFormatting(text: String, defaultColor: Int, pixelSize: Float): SpannableStringBuilder {
        val ssb = SpannableStringBuilder()
        // Simple inline parser for **bold**, *italic*, `code`
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    val boldText = text.substring(i + 2, end)
                    val start = ssb.length
                    ssb.append(boldText)
                    ssb.setSpan(StyleSpan(Typeface.BOLD), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 2
                    continue
                }
            }
            if (text.startsWith("`", i)) {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    val codeText = text.substring(i + 1, end)
                    val start = ssb.length
                    ssb.append(codeText)
                    ssb.setSpan(TypefaceSpan("monospace"), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    ssb.setSpan(ForegroundColorSpan(android.graphics.Color.rgb(0, 131, 143)), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1
                    continue
                }
            }
            if (text.startsWith("*", i)) {
                val end = text.indexOf("*", i + 1)
                if (end != -1) {
                    val italicText = text.substring(i + 1, end)
                    val start = ssb.length
                    ssb.append(italicText)
                    ssb.setSpan(StyleSpan(Typeface.ITALIC), start, ssb.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end + 1
                    continue
                }
            }
            ssb.append(text[i])
            i++
        }
        return ssb
    }

    private fun drawPageBackground(canvas: Canvas, w: Int, h: Int, options: RenderOptions) {
        val bg = when (options.paperTheme) {
            PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(24, 28, 36)
            PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
            PaperTheme.WHITE -> android.graphics.Color.WHITE
        }
        canvas.drawColor(bg)

        // Teal Brand ribbon
        val ribbonP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(0, 131, 143)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), 6f * (options.resolutionScale / 1.5f), ribbonP)
    }

    private fun drawPageStamp(canvas: Canvas, pageNumber: Int, w: Int, h: Int, options: RenderOptions) {
        val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.GRAY else android.graphics.Color.rgb(140, 145, 155)
            textSize = 13f * options.resolutionScale
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("- Page $pageNumber -", w / 2f, h - 35f, stampPaint)
    }
}
