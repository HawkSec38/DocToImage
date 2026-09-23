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
import java.io.InputStream

/**
 * Direct-to-Bitmap CSV / TSV Tabular Data Renderer.
 * Parses rows and columns directly into structured, zebra-striped grid Bitmaps.
 */
class CsvRenderer {

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val raw = inputStream.bufferedReader().use { it.readText() }
        return renderCsvString(raw, options)
    }

    fun renderCsvString(raw: String, options: RenderOptions): List<RenderedPage> {
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return emptyList()
        }

        // Detect delimiter (comma, tab, semicolon)
        val firstLine = lines.first()
        val delimiter = when {
            firstLine.contains("\t") -> '\t'
            firstLine.contains(";") -> ';'
            else -> ','
        }

        val rows = lines.map { parseCsvLine(it, delimiter) }
        val maxCols = rows.maxOfOrNull { it.size }?.coerceAtMost(10) ?: 1

        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val pageWidth = (1500 * (scale / 1.5f)).toInt()
        val pageHeight = (1100 * (scale / 1.5f)).toInt()
        val marginX = (50 * (scale / 1.5f))
        val marginY = (60 * (scale / 1.5f))
        val contentW = pageWidth - marginX * 2
        val colWidth = contentW / maxCols
        val rowHeight = 44f * (scale / 1.5f)
        val cellPad = 10f * (scale / 1.5f)

        val rowsPerPage = ((pageHeight - marginY * 2 - 80f) / rowHeight).toInt().coerceAtLeast(8)
        val totalPages = ((rows.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)

        val pages = mutableListOf<RenderedPage>()

        for (p in 0 until totalPages) {
            val startIdx = p * rowsPerPage
            val endIdx = (startIdx + rowsPerPage - 1).coerceAtMost(rows.size - 1)

            val pageBmp = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(pageBmp)

            val bg = when (options.paperTheme) {
                PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(20, 24, 33)
                PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
                PaperTheme.WHITE -> android.graphics.Color.WHITE
            }
            canvas.drawColor(bg)

            // Indigo accent ribbon
            val ribbonP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(69, 39, 160)
            }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 6f * (scale / 1.5f), ribbonP)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.DKGRAY else android.graphics.Color.rgb(220, 225, 235)
                strokeWidth = 1f * (scale / 1.5f)
                style = Paint.Style.STROKE
            }
            val hdrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(45, 52, 68) else android.graphics.Color.rgb(238, 242, 250)
            }
            val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(28, 34, 44) else android.graphics.Color.rgb(248, 250, 253)
            }

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12.5f * 1.33f * scale
            }

            var currentY = marginY
            for (r in startIdx..endIdx) {
                val row = rows[r]
                val isHeader = (r == 0)
                val rowTop = currentY
                val rowBottom = currentY + rowHeight

                if (isHeader) {
                    val hdrRect = RectF(marginX, rowTop, marginX + contentW, rowBottom)
                    canvas.drawRect(hdrRect, hdrBgPaint)
                } else if (r % 2 == 0) {
                    val strRect = RectF(marginX, rowTop, marginX + contentW, rowBottom)
                    canvas.drawRect(strRect, stripePaint)
                }

                for (c in 0 until maxCols) {
                    val cLeft = marginX + c * colWidth
                    val cRight = cLeft + colWidth
                    val rect = RectF(cLeft, rowTop, cRight, rowBottom)
                    canvas.drawRect(rect, borderPaint)

                    val valStr = row.getOrElse(c) { "" }
                    if (valStr.isNotEmpty()) {
                        textPaint.typeface = if (isHeader) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        textPaint.color = if (isHeader) {
                            if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.rgb(187, 134, 252) else android.graphics.Color.rgb(69, 39, 160)
                        } else {
                            if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(33, 33, 33)
                        }

                        val layout = StaticLayout.Builder.obtain(
                            valStr, 0, valStr.length, textPaint,
                            (colWidth - cellPad * 2).toInt().coerceAtLeast(10)
                        ).build()

                        val isNumeric = valStr.toDoubleOrNull() != null || valStr.startsWith("$")
                        val drawX = if (isNumeric && !isHeader) {
                            cRight - cellPad - textPaint.measureText(valStr).coerceAtMost(colWidth - cellPad * 2)
                        } else {
                            cLeft + cellPad
                        }

                        canvas.save()
                        canvas.translate(drawX, rowTop + (rowHeight - layout.height) / 2f)
                        layout.draw(canvas)
                        canvas.restore()
                    }
                }
                currentY += rowHeight
            }

            if (options.showPageNumbers) {
                val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.GRAY else android.graphics.Color.rgb(140, 145, 155)
                    textSize = 13f * scale
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("CSV Sheet - Page ${p + 1} of $totalPages", pageWidth / 2f, pageHeight - 25f, stampPaint)
            }

            pages.add(
                RenderedPage(
                    pageNumber = p + 1,
                    title = "Table Page ${p + 1}",
                    bitmap = pageBmp,
                    widthPx = pageWidth,
                    heightPx = pageHeight
                )
            )
        }

        return pages
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString().trim())
                current.clear()
            } else {
                current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
