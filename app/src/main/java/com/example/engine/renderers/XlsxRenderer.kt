package com.example.engine.renderers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
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
 * Direct-to-Bitmap Excel Spreadsheet (XLSX) Renderer.
 * Parses OOXML shared strings and sheet cell data directly to high-resolution spreadsheet
 * canvas Bitmaps with column ribbons, row headers, and styled data grids without PDF conversion.
 */
class XlsxRenderer {

    data class SheetCell(
        val colIndex: Int,
        val rowIndex: Int,
        val value: String
    )

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val zipBytes = inputStream.readBytes()
        var sharedStringsBytes: ByteArray? = null
        var sheetXmlBytes: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    sharedStringsBytes = zis.readBytes()
                } else if (entry.name == "xl/worksheets/sheet1.xml" || (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") && sheetXmlBytes == null)) {
                    sheetXmlBytes = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = parseSharedStrings(sharedStringsBytes)
        val cells = parseSheetCells(sheetXmlBytes, sharedStrings)

        if (cells.isEmpty()) {
            return listOf(renderEmptySheet(options))
        }

        return renderGridToPages(cells, options)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray?): List<String> {
        if (xmlBytes == null) return emptyList()
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType
            val currentString = StringBuilder()
            var inT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") {
                            inT = true
                        } else if (parser.name == "si") {
                            currentString.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) {
                            currentString.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") {
                            inT = false
                        } else if (parser.name == "si") {
                            strings.add(currentString.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return strings
    }

    private fun parseSheetCells(xmlBytes: ByteArray?, sharedStrings: List<String>): List<SheetCell> {
        if (xmlBytes == null) return emptyList()
        val cells = mutableListOf<SheetCell>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var eventType = parser.eventType

            var currentCellRef = ""
            var currentCellType = ""
            var currentVal = ""
            var inV = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "c") {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentVal = ""
                        } else if (parser.name == "v") {
                            inV = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) {
                            currentVal += parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "v") {
                            inV = false
                        } else if (parser.name == "c") {
                            val resolvedText = if (currentCellType == "s") {
                                val sIndex = currentVal.toIntOrNull() ?: -1
                                if (sIndex in sharedStrings.indices) sharedStrings[sIndex] else currentVal
                            } else {
                                currentVal
                            }
                            val (cIndex, rIndex) = parseCellRef(currentCellRef)
                            if (cIndex >= 0 && rIndex >= 0) {
                                cells.add(SheetCell(cIndex, rIndex, resolvedText))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return cells
    }

    private fun parseCellRef(ref: String): Pair<Int, Int> {
        var colStr = ""
        var rowStr = ""
        for (ch in ref) {
            if (ch.isLetter()) colStr += ch.uppercaseChar()
            else if (ch.isDigit()) rowStr += ch
        }
        val col = colNameToIndex(colStr)
        val row = (rowStr.toIntOrNull() ?: 1) - 1
        return Pair(col, row)
    }

    private fun colNameToIndex(name: String): Int {
        var index = 0
        for (c in name) {
            index = index * 26 + (c - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun colIndexToName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A' + rem))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun renderGridToPages(cells: List<SheetCell>, options: RenderOptions): List<RenderedPage> {
        val maxCol = cells.maxOfOrNull { it.colIndex }?.coerceAtMost(14) ?: 0
        val maxRow = cells.maxOfOrNull { it.rowIndex } ?: 0

        // Build grid lookup
        val grid = mutableMapOf<Pair<Int, Int>, String>()
        for (cell in cells) {
            grid[Pair(cell.colIndex, cell.rowIndex)] = cell.value
        }

        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val pageWidth = (1600 * (scale / 1.5f)).toInt()
        val pageHeight = (1200 * (scale / 1.5f)).toInt()
        val rowHeaderW = 60f * (scale / 1.5f)
        val colHeaderH = 44f * (scale / 1.5f)
        val cellPadding = 8f * (scale / 1.5f)
        val rowHeight = 42f * (scale / 1.5f)

        val totalCols = (maxCol + 1).coerceAtLeast(4)
        val availableW = pageWidth - rowHeaderW - 40f
        val colWidth = availableW / totalCols

        val rowsPerPage = ((pageHeight - colHeaderH - 120f) / rowHeight).toInt().coerceAtLeast(10)
        val totalPages = ((maxRow + 1) + rowsPerPage - 1) / rowsPerPage

        val pages = mutableListOf<RenderedPage>()

        for (p in 0 until totalPages.coerceAtLeast(1)) {
            val startRow = p * rowsPerPage
            val endRow = (startRow + rowsPerPage - 1).coerceAtMost(maxRow)

            val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Sheet Background
            val bg = when (options.paperTheme) {
                PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(20, 24, 33)
                PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
                PaperTheme.WHITE -> android.graphics.Color.WHITE
            }
            canvas.drawColor(bg)

            // Top Ribbon (Excel Green)
            val ribbonP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(46, 125, 50)
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f * (scale / 1.5f), ribbonP)

            val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(35, 42, 54) else android.graphics.Color.rgb(240, 243, 246)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(220, 224, 230)
                strokeWidth = 1f * (scale / 1.5f)
                style = Paint.Style.STROKE
            }
            val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(28, 34, 44) else android.graphics.Color.rgb(248, 250, 252)
            }

            val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.LTGRAY else android.graphics.Color.rgb(100, 110, 125)
                textSize = 13f * scale
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            val cellTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)
                textSize = 13f * scale
            }

            val topOffset = 30f

            // Draw Column Headers (A, B, C, D...)
            val colHeaderRect = RectF(20f + rowHeaderW, topOffset, 20f + rowHeaderW + colWidth * totalCols, topOffset + colHeaderH)
            canvas.drawRect(colHeaderRect, headerBgPaint)

            for (c in 0 until totalCols) {
                val cLeft = 20f + rowHeaderW + c * colWidth
                val cRight = cLeft + colWidth
                canvas.drawRect(RectF(cLeft, topOffset, cRight, topOffset + colHeaderH), borderPaint)
                val colLetter = colIndexToName(c)
                canvas.drawText(colLetter, cLeft + colWidth / 2f, topOffset + colHeaderH / 2f + 5f, headerTextPaint)
            }

            // Draw Rows & Cells
            var currentY = topOffset + colHeaderH
            for (r in startRow..endRow) {
                val rowTop = currentY
                val rowBottom = currentY + rowHeight

                // Row header number (1, 2, 3...)
                val rHeaderRect = RectF(20f, rowTop, 20f + rowHeaderW, rowBottom)
                canvas.drawRect(rHeaderRect, headerBgPaint)
                canvas.drawRect(rHeaderRect, borderPaint)
                canvas.drawText("${r + 1}", 20f + rowHeaderW / 2f, rowTop + rowHeight / 2f + 5f, headerTextPaint)

                // Row background stripe
                val isEven = (r % 2 == 0)
                if (isEven && r != 0) {
                    val fullRowRect = RectF(20f + rowHeaderW, rowTop, 20f + rowHeaderW + colWidth * totalCols, rowBottom)
                    canvas.drawRect(fullRowRect, stripePaint)
                }

                // If row 0 (data header), give special subtle highlight
                if (r == 0) {
                    val headRowRect = RectF(20f + rowHeaderW, rowTop, 20f + rowHeaderW + colWidth * totalCols, rowBottom)
                    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(40, 52, 68) else android.graphics.Color.rgb(232, 240, 254)
                    }
                    canvas.drawRect(headRowRect, headPaint)
                }

                for (c in 0 until totalCols) {
                    val cLeft = 20f + rowHeaderW + c * colWidth
                    val cRight = cLeft + colWidth
                    val cellRect = RectF(cLeft, rowTop, cRight, rowBottom)

                    canvas.drawRect(cellRect, borderPaint)

                    val value = grid[Pair(c, r)] ?: ""
                    if (value.isNotEmpty()) {
                        val isHeaderRow = (r == 0)
                        val isNumeric = value.trim().toDoubleOrNull() != null || value.trim().startsWith("$")

                        cellTextPaint.typeface = if (isHeaderRow) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        cellTextPaint.color = if (isHeaderRow) {
                            if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(128, 203, 196) else android.graphics.Color.rgb(27, 94, 32)
                        } else {
                            if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)
                        }

                        val layout = StaticLayout.Builder.obtain(
                            value, 0, value.length, cellTextPaint,
                            (colWidth - cellPadding * 2).toInt().coerceAtLeast(20)
                        ).build()

                        val drawX = if (isNumeric) {
                            cRight - cellPadding - cellTextPaint.measureText(value).coerceAtMost(colWidth - cellPadding * 2)
                        } else {
                            cLeft + cellPadding
                        }

                        canvas.save()
                        canvas.translate(drawX, rowTop + (rowHeight - layout.height) / 2f)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }
                currentY += rowHeight
            }

            // Page Stamp
            if (options.showPageNumbers) {
                val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.GRAY else android.graphics.Color.rgb(140, 145, 155)
                    textSize = 13f * scale
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("Sheet 1 - Page ${p + 1} of $totalPages", pageWidth / 2f, pageHeight - 30f, stampPaint)
            }

            pages.add(
                RenderedPage(
                    pageNumber = p + 1,
                    title = "Sheet Page ${p + 1}",
                    bitmap = bitmap,
                    widthPx = pageWidth,
                    heightPx = pageHeight
                )
            )
        }

        return pages
    }

    private fun renderEmptySheet(options: RenderOptions): RenderedPage {
        val w = 1200
        val h = 800
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Empty Spreadsheet", w / 2f, h / 2f, p)
        return RenderedPage(1, "Empty Sheet", bmp, w, h)
    }
}
