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
import android.text.style.UnderlineSpan
import com.example.data.model.PaperTheme
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedPage
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Direct-to-Bitmap Word Document (DOCX) Renderer.
 * Parses OOXML paragraphs, styles, headings, formatting runs, tables with cell shading/borders,
 * and page breaks directly into paginated Bitmaps without any intermediate PDF conversion.
 */
class DocxRenderer {

    sealed class DocElement {
        data class Paragraph(
            val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
            val isHeading: Boolean = false,
            val headingLevel: Int = 0,
            val isBullet: Boolean = false,
            val runs: List<DocRun> = emptyList()
        ) : DocElement()

        data class Table(
            val rows: List<TableRow> = emptyList()
        ) : DocElement()

        object PageBreak : DocElement()
    }

    data class TableRow(
        val cells: List<TableCell>
    )

    data class TableCell(
        val text: String,
        val isHeader: Boolean = false,
        val bgColor: Int? = null,
        val bold: Boolean = false
    )

    data class DocRun(
        val text: String,
        val fontSizePt: Float = 12f,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val color: Int = android.graphics.Color.BLACK
    )

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val zipBytes = inputStream.readBytes()
        var docXmlBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    docXmlBytes = zis.readBytes()
                    break
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (docXmlBytes == null) {
            // If not a valid zip docx, try parsing text directly as fallback
            return renderFallbackDoc(zipBytes, options)
        }

        val elements = parseDocxXml(docXmlBytes!!)
        return paginateAndRender(elements, options)
    }

    private fun parseDocxXml(xmlBytes: ByteArray): List<DocElement> {
        val elements = mutableListOf<DocElement>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var inTable = false
            var inRow = false
            var inCell = false
            var inParagraph = false
            var inRun = false

            val currentTableRows = mutableListOf<TableRow>()
            val currentRowCells = mutableListOf<TableCell>()
            val currentCellText = StringBuilder()
            var currentCellBg: Int? = null
            var currentCellHeader = false
            var currentCellBold = false

            var currentParagraphAlignment = Layout.Alignment.ALIGN_NORMAL
            var currentIsHeading = false
            var currentHeadingLevel = 0
            var currentIsBullet = false
            val currentRuns = mutableListOf<DocRun>()

            var currentText = ""
            var currentFontSizePt = 12f
            var currentIsBold = false
            var currentIsItalic = false
            var currentIsUnderline = false
            var currentRunColor = android.graphics.Color.BLACK

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                currentTableRows.clear()
                            }
                            "tr" -> {
                                inRow = true
                                currentRowCells.clear()
                            }
                            "tc" -> {
                                inCell = true
                                currentCellText.clear()
                                currentCellBg = null
                                currentCellHeader = currentTableRows.isEmpty()
                                currentCellBold = currentCellHeader
                            }
                            "shd" -> {
                                val fill = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fill")
                                    ?: parser.getAttributeValue(null, "w:fill")
                                if (fill != null && fill != "auto") {
                                    currentCellBg = parseHexColor(fill)
                                }
                            }
                            "p" -> {
                                inParagraph = true
                                currentParagraphAlignment = Layout.Alignment.ALIGN_NORMAL
                                currentIsHeading = false
                                currentHeadingLevel = 0
                                currentIsBullet = false
                                currentRuns.clear()
                            }
                            "pStyle" -> {
                                val styleVal = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    ?: parser.getAttributeValue(null, "w:val") ?: ""
                                if (styleVal.contains("Heading1", ignoreCase = true) || styleVal.contains("Title", ignoreCase = true)) {
                                    currentIsHeading = true
                                    currentHeadingLevel = 1
                                } else if (styleVal.contains("Heading2", ignoreCase = true)) {
                                    currentIsHeading = true
                                    currentHeadingLevel = 2
                                } else if (styleVal.contains("Heading3", ignoreCase = true)) {
                                    currentIsHeading = true
                                    currentHeadingLevel = 3
                                }
                            }
                            "jc" -> {
                                val jcVal = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    ?: parser.getAttributeValue(null, "w:val")
                                currentParagraphAlignment = when (jcVal) {
                                    "center" -> Layout.Alignment.ALIGN_CENTER
                                    "right" -> Layout.Alignment.ALIGN_OPPOSITE
                                    else -> Layout.Alignment.ALIGN_NORMAL
                                }
                            }
                            "numPr" -> currentIsBullet = true
                            "br" -> {
                                val type = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type")
                                    ?: parser.getAttributeValue(null, "w:type")
                                if (type == "page") {
                                    elements.add(DocElement.PageBreak)
                                }
                            }
                            "r" -> {
                                if (inParagraph) {
                                    inRun = true
                                    currentText = ""
                                    currentFontSizePt = if (currentIsHeading) (if (currentHeadingLevel == 1) 22f else 18f) else 12f
                                    currentIsBold = currentIsHeading
                                    currentIsItalic = false
                                    currentIsUnderline = false
                                    currentRunColor = if (currentIsHeading) android.graphics.Color.rgb(21, 101, 192) else android.graphics.Color.BLACK
                                }
                            }
                            "b" -> currentIsBold = true
                            "i" -> currentIsItalic = true
                            "u" -> currentIsUnderline = true
                            "sz" -> {
                                val szVal = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    ?: parser.getAttributeValue(null, "w:val")
                                szVal?.toFloatOrNull()?.let {
                                    currentFontSizePt = it / 2f // half points
                                }
                            }
                            "color" -> {
                                val colorVal = parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    ?: parser.getAttributeValue(null, "w:val")
                                if (colorVal != null && colorVal != "auto") {
                                    currentRunColor = parseHexColor(colorVal)
                                }
                            }
                            "t" -> {
                                if (inRun) {
                                    currentText = parser.nextText()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "r" -> {
                                if (inRun && currentText.isNotEmpty()) {
                                    currentRuns.add(
                                        DocRun(
                                            text = currentText,
                                            fontSizePt = currentFontSizePt,
                                            isBold = currentIsBold,
                                            isItalic = currentIsItalic,
                                            isUnderline = currentIsUnderline,
                                            color = currentRunColor
                                        )
                                    )
                                    if (inCell) {
                                        currentCellText.append(currentText)
                                        if (currentIsBold) currentCellBold = true
                                    }
                                }
                                inRun = false
                            }
                            "p" -> {
                                if (inParagraph) {
                                    if (!inCell) {
                                        elements.add(
                                            DocElement.Paragraph(
                                                alignment = currentParagraphAlignment,
                                                isHeading = currentIsHeading,
                                                headingLevel = currentHeadingLevel,
                                                isBullet = currentIsBullet,
                                                runs = currentRuns.toList()
                                            )
                                        )
                                    }
                                }
                                inParagraph = false
                            }
                            "tc" -> {
                                inCell = false
                                currentRowCells.add(
                                    TableCell(
                                        text = currentCellText.toString().trim(),
                                        isHeader = currentCellHeader,
                                        bgColor = currentCellBg,
                                        bold = currentCellBold
                                    )
                                )
                            }
                            "tr" -> {
                                inRow = false
                                currentTableRows.add(TableRow(currentRowCells.toList()))
                            }
                            "tbl" -> {
                                inTable = false
                                if (currentTableRows.isNotEmpty()) {
                                    elements.add(DocElement.Table(currentTableRows.toList()))
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return elements
    }

    private fun paginateAndRender(elements: List<DocElement>, options: RenderOptions): List<RenderedPage> {
        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val pageWidth = (1240 * (scale / 1.5f)).toInt()
        val pageHeight = (1754 * (scale / 1.5f)).toInt()
        val marginX = (90 * (scale / 1.5f))
        val marginY = (110 * (scale / 1.5f))
        val contentWidth = (pageWidth - marginX * 2).toInt()
        val usableHeight = pageHeight - marginY * 2

        val pages = mutableListOf<RenderedPage>()
        var currentPageNumber = 1
        var currentY = marginY

        // We prepare a page bitmap and canvas
        var currentBitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        var currentCanvas = Canvas(currentBitmap)
        drawPageBackground(currentCanvas, pageWidth, pageHeight, options)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun finishCurrentPage() {
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

        for (element in elements) {
            when (element) {
                is DocElement.PageBreak -> {
                    finishCurrentPage()
                }
                is DocElement.Paragraph -> {
                    if (element.runs.isEmpty()) {
                        // Empty line spacer
                        currentY += 18f * (scale / 1.5f)
                        if (currentY > pageHeight - marginY) {
                            finishCurrentPage()
                        }
                        continue
                    }

                    val spannable = SpannableStringBuilder()
                    if (element.isBullet) {
                        spannable.append("  •   ")
                    }

                    var dominantSize = 12f
                    var dominantBold = false
                    val defaultTextColor = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)

                    for (run in element.runs) {
                        val start = spannable.length
                        spannable.append(run.text)
                        val end = spannable.length

                        val style = when {
                            run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                            run.isBold -> Typeface.BOLD
                            run.isItalic -> Typeface.ITALIC
                            else -> Typeface.NORMAL
                        }
                        spannable.setSpan(StyleSpan(style), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (run.isUnderline) {
                            spannable.setSpan(UnderlineSpan(), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        val rColor = if (run.color != android.graphics.Color.BLACK) run.color else defaultTextColor
                        spannable.setSpan(ForegroundColorSpan(rColor), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val pixelSize = (run.fontSizePt * 1.33f * scale).toInt()
                        spannable.setSpan(AbsoluteSizeSpan(pixelSize), start, end, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)

                        dominantSize = run.fontSizePt
                        dominantBold = run.isBold
                    }

                    textPaint.textSize = dominantSize * 1.33f * scale
                    textPaint.typeface = if (dominantBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    textPaint.color = defaultTextColor

                    val layout = StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, contentWidth)
                        .setAlignment(element.alignment)
                        .setLineSpacing(6f * (scale / 1.5f), 1.15f)
                        .setIncludePad(false)
                        .build()

                    val blockHeight = layout.height + (if (element.isHeading) 24f else 14f) * (scale / 1.5f)

                    if (currentY + blockHeight > pageHeight - marginY) {
                        finishCurrentPage()
                    }

                    // If heading, draw subtle colored underline accent
                    if (element.isHeading && element.headingLevel == 1) {
                        val accentP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.rgb(21, 101, 192)
                            strokeWidth = 3f * (scale / 1.5f)
                        }
                        currentCanvas.drawLine(marginX, currentY + layout.height + 4f, marginX + 120f * (scale / 1.5f), currentY + layout.height + 4f, accentP)
                    }

                    currentCanvas.save()
                    currentCanvas.translate(marginX, currentY)
                    layout.draw(currentCanvas)
                    currentCanvas.restore()

                    currentY += blockHeight
                }
                is DocElement.Table -> {
                    val table = element
                    if (table.rows.isEmpty()) continue
                    val colCount = table.rows.maxOfOrNull { it.cells.size } ?: 1
                    val colWidth = contentWidth.toFloat() / colCount
                    val cellPadding = 12f * (scale / 1.5f)

                    val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(200, 205, 215)
                        strokeWidth = 1.5f * (scale / 1.5f)
                        style = Paint.Style.STROKE
                    }

                    for (row in table.rows) {
                        // Calculate row height
                        var maxCellTextHeight = 40f * (scale / 1.5f)
                        for (cell in row.cells) {
                            textPaint.textSize = (if (cell.isHeader) 13f else 12f) * 1.33f * scale
                            textPaint.typeface = if (cell.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            val cellLayout = StaticLayout.Builder.obtain(
                                cell.text, 0, cell.text.length, textPaint,
                                (colWidth - cellPadding * 2).toInt().coerceAtLeast(20)
                            ).build()
                            if (cellLayout.height + cellPadding * 2 > maxCellTextHeight) {
                                maxCellTextHeight = cellLayout.height + cellPadding * 2
                            }
                        }

                        if (currentY + maxCellTextHeight > pageHeight - marginY) {
                            finishCurrentPage()
                        }

                        // Render row cells
                        for (colIndex in row.cells.indices) {
                            val cell = row.cells[colIndex]
                            val cellLeft = marginX + colIndex * colWidth
                            val cellRight = cellLeft + colWidth
                            val cellTop = currentY
                            val cellBottom = currentY + maxCellTextHeight
                            val rect = RectF(cellLeft, cellTop, cellRight, cellBottom)

                            // Cell background
                            val cellBg = cell.bgColor ?: if (cell.isHeader) {
                                if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(40, 48, 62) else android.graphics.Color.rgb(238, 242, 248)
                            } else null

                            if (cellBg != null) {
                                tablePaint.color = cellBg
                                tablePaint.style = Paint.Style.FILL
                                currentCanvas.drawRect(rect, tablePaint)
                            }

                            // Cell border
                            currentCanvas.drawRect(rect, borderPaint)

                            // Cell Text
                            textPaint.textSize = (if (cell.isHeader) 13f else 12f) * 1.33f * scale
                            textPaint.typeface = if (cell.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                            textPaint.color = if (cell.isHeader && options.paperTheme != PaperTheme.DARK_SLATE) {
                                android.graphics.Color.rgb(21, 101, 192)
                            } else if (options.paperTheme == PaperTheme.DARK_SLATE) {
                                android.graphics.Color.WHITE
                            } else {
                                android.graphics.Color.rgb(33, 33, 33)
                            }

                            val cellLayout = StaticLayout.Builder.obtain(
                                cell.text, 0, cell.text.length, textPaint,
                                (colWidth - cellPadding * 2).toInt().coerceAtLeast(20)
                            ).build()

                            currentCanvas.save()
                            currentCanvas.translate(cellLeft + cellPadding, cellTop + cellPadding)
                            cellLayout.draw(currentCanvas)
                            currentCanvas.restore()
                        }

                        currentY += maxCellTextHeight
                    }
                    currentY += 16f * (scale / 1.5f)
                }
            }
        }

        // Finish final page
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

    private fun drawPageBackground(canvas: Canvas, w: Int, h: Int, options: RenderOptions) {
        val bg = when (options.paperTheme) {
            PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(24, 28, 36)
            PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
            PaperTheme.WHITE -> android.graphics.Color.WHITE
        }
        canvas.drawColor(bg)

        // Draw top brand ribbon
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(21, 101, 192)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), 6f * options.resolutionScale, ribbonPaint)
    }

    private fun drawPageStamp(canvas: Canvas, pageNumber: Int, w: Int, h: Int, options: RenderOptions) {
        val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.GRAY else android.graphics.Color.rgb(140, 145, 155)
            textSize = 13f * options.resolutionScale
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("- Page $pageNumber -", w / 2f, h - 35f, stampPaint)
    }

    private fun renderFallbackDoc(rawBytes: ByteArray, options: RenderOptions): List<RenderedPage> {
        val text = String(rawBytes).take(3000)
        val lines = text.lines()
        val elements = lines.map { line ->
            DocElement.Paragraph(
                runs = listOf(DocRun(line, fontSizePt = 12f))
            )
        }
        return paginateAndRender(elements, options)
    }

    private fun parseHexColor(hex: String): Int {
        return try {
            val clean = hex.removePrefix("#")
            when (clean.length) {
                6 -> android.graphics.Color.parseColor("#$clean")
                8 -> android.graphics.Color.parseColor("#$clean")
                else -> android.graphics.Color.BLACK
            }
        } catch (_: Exception) {
            android.graphics.Color.BLACK
        }
    }
}
