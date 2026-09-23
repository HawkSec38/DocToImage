package com.example.engine.renderers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
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
 * Direct-to-Bitmap PowerPoint (PPTX) Renderer.
 * Parses OOXML slide shapes, coordinates, text runs, bullet lists, background fills,
 * and embedded media directly to high-resolution Android Bitmaps without intermediate PDF conversion.
 */
class PptxRenderer {

    data class SlideShape(
        val xRatio: Float,
        val yRatio: Float,
        val wRatio: Float,
        val hRatio: Float,
        val fillColor: Int? = null,
        val strokeColor: Int? = null,
        val strokeWidth: Float = 0f,
        val cornerRadius: Float = 0f,
        val paragraphs: List<SlideParagraph> = emptyList(),
        val imageBytes: ByteArray? = null
    )

    data class SlideParagraph(
        val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        val isBullet: Boolean = false,
        val bulletChar: String = "•",
        val runs: List<SlideRun> = emptyList()
    )

    data class SlideRun(
        val text: String,
        val fontSizePt: Float = 14f,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val color: Int = android.graphics.Color.BLACK
    )

    data class SlideData(
        val slideNumber: Int,
        val title: String,
        val backgroundColor: Int? = null,
        val bgGradientColors: Pair<Int, Int>? = null,
        val shapes: List<SlideShape> = emptyList()
    )

    fun render(inputStream: InputStream, options: RenderOptions): List<RenderedPage> {
        val zipBytes = inputStream.readBytes()
        val slideEntries = mutableMapOf<String, ByteArray>()
        val mediaEntries = mutableMapOf<String, ByteArray>()
        val relEntries = mutableMapOf<String, ByteArray>()

        // 1. Read zip package
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val bytes = zis.readBytes()
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    slideEntries[name] = bytes
                } else if (name.startsWith("ppt/media/")) {
                    mediaEntries[name] = bytes
                } else if (name.startsWith("ppt/slides/_rels/") && name.endsWith(".rels")) {
                    relEntries[name] = bytes
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Sort slides slide1.xml, slide2.xml ...
        val sortedSlideNames = slideEntries.keys.sortedWith(Comparator { a, b ->
            val numA = a.filter { it.isDigit() }.toIntOrNull() ?: 0
            val numB = b.filter { it.isDigit() }.toIntOrNull() ?: 0
            numA.compareTo(numB)
        })

        val renderedPages = mutableListOf<RenderedPage>()
        val scale = options.resolutionScale.coerceIn(0.75f, 3.0f)
        val targetWidth = (1920 * (scale / 1.5f)).toInt()
        val targetHeight = (1080 * (scale / 1.5f)).toInt()

        if (sortedSlideNames.isEmpty()) {
            // Fallback: If no standard slides detected, render a fallback slide
            val page = renderSingleSlide(
                slideData = SlideData(
                    slideNumber = 1,
                    title = "Presentation Document",
                    shapes = listOf(
                        SlideShape(
                            xRatio = 0.1f, yRatio = 0.2f, wRatio = 0.8f, hRatio = 0.6f,
                            fillColor = android.graphics.Color.WHITE,
                            paragraphs = listOf(
                                SlideParagraph(
                                    runs = listOf(SlideRun("PowerPoint Document Preview", fontSizePt = 24f, isBold = true))
                                )
                            )
                        )
                    )
                ),
                width = targetWidth,
                height = targetHeight,
                options = options
            )
            return listOf(page)
        }

        sortedSlideNames.forEachIndexed { index, slideName ->
            val slideBytes = slideEntries[slideName] ?: return@forEachIndexed
            val relsName = "ppt/slides/_rels/" + slideName.substringAfterLast("/") + ".rels"
            val relsBytes = relEntries[relsName]
            val slideData = parseSlideXml(index + 1, slideBytes, relsBytes, mediaEntries)
            val renderedPage = renderSingleSlide(slideData, targetWidth, targetHeight, options)
            renderedPages.add(renderedPage)
        }

        return renderedPages
    }

    private fun parseSlideXml(
        slideNumber: Int,
        xmlBytes: ByteArray,
        relsBytes: ByteArray?,
        mediaEntries: Map<String, ByteArray>
    ): SlideData {
        val relsMap = parseRels(relsBytes)
        val shapes = mutableListOf<SlideShape>()
        var slideTitle = "Slide $slideNumber"
        var bgColor: Int? = null
        var bgGradient: Pair<Int, Int>? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

            var eventType = parser.eventType
            var inShape = false
            var inTxBody = false
            var inParagraph = false
            var inRun = false
            var inBg = false

            // Shape coordinate accumulators (EMUs standard slide is 9144000 x 5143500)
            val defaultSlideW = 9144000f
            val defaultSlideH = 5143500f

            var offX = 0f
            var offY = 0f
            var extW = 0f
            var extH = 0f
            var shapeFillColor: Int? = null
            var shapeStrokeColor: Int? = null
            var shapeStrokeWidth = 0f
            var shapeCornerRadius = 0f
            var shapeImageBytes: ByteArray? = null

            val currentParagraphs = mutableListOf<SlideParagraph>()
            var currentRuns = mutableListOf<SlideRun>()
            var currentAlignment = Layout.Alignment.ALIGN_NORMAL
            var isCurrentBullet = false
            var currentBulletChar = "•"

            var currentText = ""
            var currentFontSizePt = 16f
            var isCurrentBold = false
            var isCurrentItalic = false
            var currentRunColor = android.graphics.Color.BLACK

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "bg" -> inBg = true
                            "sp", "grpSp", "pic" -> {
                                inShape = true
                                offX = 0f
                                offY = 0f
                                extW = 0f
                                extH = 0f
                                shapeFillColor = null
                                shapeStrokeColor = null
                                shapeStrokeWidth = 0f
                                shapeCornerRadius = 0f
                                shapeImageBytes = null
                                currentParagraphs.clear()
                            }
                            "off" -> {
                                val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
                                val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
                                offX = x
                                offY = y
                            }
                            "ext" -> {
                                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f
                                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f
                                extW = cx
                                extH = cy
                            }
                            "prstGeom" -> {
                                val prst = parser.getAttributeValue(null, "prst") ?: ""
                                if (prst.contains("roundRect", ignoreCase = true)) {
                                    shapeCornerRadius = 24f
                                }
                            }
                            "srgbClr" -> {
                                val hex = parser.getAttributeValue(null, "val")
                                if (hex != null) {
                                    val parsedColor = parseHexColor(hex)
                                    if (inBg) {
                                        bgColor = parsedColor
                                    } else if (inShape && !inTxBody) {
                                        shapeFillColor = parsedColor
                                    } else if (inRun) {
                                        currentRunColor = parsedColor
                                    }
                                }
                            }
                            "blip" -> {
                                val embedId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                                    ?: parser.getAttributeValue(null, "r:embed")
                                if (embedId != null && relsMap.containsKey(embedId)) {
                                    val target = relsMap[embedId] ?: ""
                                    val cleanPath = "ppt/" + target.removePrefix("../").removePrefix("/")
                                    shapeImageBytes = mediaEntries[cleanPath]
                                }
                            }
                            "txBody" -> inTxBody = true
                            "p" -> {
                                if (inTxBody) {
                                    inParagraph = true
                                    currentRuns = mutableListOf()
                                    currentAlignment = Layout.Alignment.ALIGN_NORMAL
                                    isCurrentBullet = false
                                }
                            }
                            "pPr" -> {
                                val algn = parser.getAttributeValue(null, "algn")
                                currentAlignment = when (algn) {
                                    "ctr" -> Layout.Alignment.ALIGN_CENTER
                                    "r" -> Layout.Alignment.ALIGN_OPPOSITE
                                    else -> Layout.Alignment.ALIGN_NORMAL
                                }
                            }
                            "buChar" -> {
                                isCurrentBullet = true
                                currentBulletChar = parser.getAttributeValue(null, "char") ?: "•"
                            }
                            "r" -> {
                                if (inParagraph) {
                                    inRun = true
                                    currentText = ""
                                    currentFontSizePt = 16f
                                    isCurrentBold = false
                                    isCurrentItalic = false
                                    currentRunColor = android.graphics.Color.BLACK
                                }
                            }
                            "rPr" -> {
                                val sz = parser.getAttributeValue(null, "sz")?.toFloatOrNull()
                                if (sz != null) {
                                    currentFontSizePt = sz / 100f // 100ths of pt
                                }
                                val b = parser.getAttributeValue(null, "b")
                                if (b == "1" || b == "true") isCurrentBold = true
                                val i = parser.getAttributeValue(null, "i")
                                if (i == "1" || i == "true") isCurrentItalic = true
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
                            "bg" -> inBg = false
                            "r" -> {
                                if (inRun && currentText.isNotEmpty()) {
                                    currentRuns.add(
                                        SlideRun(
                                            text = currentText,
                                            fontSizePt = currentFontSizePt,
                                            isBold = isCurrentBold,
                                            isItalic = isCurrentItalic,
                                            color = currentRunColor
                                        )
                                    )
                                    // Extract title heuristics from first bold run or first paragraph
                                    if (slideTitle == "Slide $slideNumber" && (isCurrentBold || currentFontSizePt >= 22f)) {
                                        slideTitle = currentText.take(40)
                                    }
                                }
                                inRun = false
                            }
                            "p" -> {
                                if (inParagraph) {
                                    currentParagraphs.add(
                                        SlideParagraph(
                                            alignment = currentAlignment,
                                            isBullet = isCurrentBullet,
                                            bulletChar = currentBulletChar,
                                            runs = currentRuns.toList()
                                        )
                                    )
                                }
                                inParagraph = false
                            }
                            "txBody" -> inTxBody = false
                            "sp", "grpSp", "pic" -> {
                                if (inShape) {
                                    val xR = if (defaultSlideW > 0) offX / defaultSlideW else 0f
                                    val yR = if (defaultSlideH > 0) offY / defaultSlideH else 0f
                                    val wR = if (defaultSlideW > 0) (extW / defaultSlideW).coerceAtLeast(0.05f) else 0.5f
                                    val hR = if (defaultSlideH > 0) (extH / defaultSlideH).coerceAtLeast(0.05f) else 0.2f

                                    shapes.add(
                                        SlideShape(
                                            xRatio = xR.coerceIn(0f, 1f),
                                            yRatio = yR.coerceIn(0f, 1f),
                                            wRatio = wR.coerceIn(0.01f, 1f),
                                            hRatio = hR.coerceIn(0.01f, 1f),
                                            fillColor = shapeFillColor,
                                            strokeColor = shapeStrokeColor,
                                            strokeWidth = shapeStrokeWidth,
                                            cornerRadius = shapeCornerRadius,
                                            paragraphs = currentParagraphs.toList(),
                                            imageBytes = shapeImageBytes
                                        )
                                    )
                                }
                                inShape = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return SlideData(
            slideNumber = slideNumber,
            title = slideTitle,
            backgroundColor = bgColor,
            bgGradientColors = bgGradient,
            shapes = shapes
        )
    }

    private fun parseRels(relsBytes: ByteArray?): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val rels = mutableMapOf<String, String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(relsBytes), "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        rels[id] = target
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}
        return rels
    }

    private fun renderSingleSlide(
        slideData: SlideData,
        width: Int,
        height: Int,
        options: RenderOptions
    ): RenderedPage {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw Slide Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (slideData.bgGradientColors != null) {
            val (c1, c2) = slideData.bgGradientColors
            bgPaint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), c1, c2, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        } else if (slideData.backgroundColor != null) {
            bgPaint.color = slideData.backgroundColor
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        } else {
            // Default theme background
            val bg = when (options.paperTheme) {
                PaperTheme.DARK_SLATE -> android.graphics.Color.rgb(20, 24, 33)
                PaperTheme.WARM_SEPIA -> android.graphics.Color.rgb(250, 246, 238)
                PaperTheme.WHITE -> android.graphics.Color.WHITE
            }
            bgPaint.color = bg
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // 2. Draw Decorative Top Accent Bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(230, 81, 0) // PowerPoint Orange Accent
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 6f * options.resolutionScale, accentPaint)

        // 3. Render Shapes
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

        for (shape in slideData.shapes) {
            val left = shape.xRatio * width
            val top = shape.yRatio * height
            val w = shape.wRatio * width
            val h = shape.hRatio * height
            val rectF = RectF(left, top, left + w, top + h)

            // Shape background
            if (shape.fillColor != null) {
                shapePaint.color = shape.fillColor
                shapePaint.style = Paint.Style.FILL
                if (shape.cornerRadius > 0) {
                    canvas.drawRoundRect(rectF, shape.cornerRadius, shape.cornerRadius, shapePaint)
                } else {
                    canvas.drawRect(rectF, shapePaint)
                }
            }

            // Shape stroke
            if (shape.strokeColor != null && shape.strokeWidth > 0) {
                shapePaint.color = shape.strokeColor
                shapePaint.style = Paint.Style.STROKE
                shapePaint.strokeWidth = shape.strokeWidth
                if (shape.cornerRadius > 0) {
                    canvas.drawRoundRect(rectF, shape.cornerRadius, shape.cornerRadius, shapePaint)
                } else {
                    canvas.drawRect(rectF, shapePaint)
                }
            }

            // Embedded Image
            if (shape.imageBytes != null) {
                try {
                    val imgBitmap = BitmapFactory.decodeByteArray(shape.imageBytes, 0, shape.imageBytes.size)
                    if (imgBitmap != null) {
                        val src = Rect(0, 0, imgBitmap.width, imgBitmap.height)
                        canvas.drawBitmap(imgBitmap, src, rectF, Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                } catch (_: Exception) {}
            }

            // Render Text Paragraphs inside shape
            var currentY = top + 16f
            val textLeft = left + 16f
            val availableW = (w - 32f).coerceAtLeast(50f).toInt()

            for (p in shape.paragraphs) {
                if (p.runs.isEmpty()) continue

                val spanBuilder = android.text.SpannableStringBuilder()
                if (p.isBullet) {
                    spanBuilder.append("${p.bulletChar}  ")
                }

                var dominantSize = 16f
                var dominantBold = false
                var dominantColor = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.WHITE else android.graphics.Color.rgb(30, 30, 30)

                for (run in p.runs) {
                    val start = spanBuilder.length
                    spanBuilder.append(run.text)
                    val end = spanBuilder.length

                    val style = when {
                        run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                        run.isBold -> Typeface.BOLD
                        run.isItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    spanBuilder.setSpan(android.text.style.StyleSpan(style), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val runColor = if (run.color != android.graphics.Color.BLACK) run.color else dominantColor
                    spanBuilder.setSpan(android.text.style.ForegroundColorSpan(runColor), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val pixelSize = (run.fontSizePt * 1.33f * options.resolutionScale).toInt()
                    spanBuilder.setSpan(android.text.style.AbsoluteSizeSpan(pixelSize), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    dominantSize = run.fontSizePt
                    dominantBold = run.isBold
                }

                textPaint.textSize = dominantSize * 1.33f * options.resolutionScale
                textPaint.typeface = if (dominantBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textPaint.color = dominantColor

                val staticLayout = StaticLayout.Builder.obtain(spanBuilder, 0, spanBuilder.length, textPaint, availableW)
                    .setAlignment(p.alignment)
                    .setIncludePad(false)
                    .build()

                canvas.save()
                canvas.translate(textLeft, currentY)
                staticLayout.draw(canvas)
                canvas.restore()

                currentY += staticLayout.height + 12f
            }
        }

        // 4. Slide Footer / Number Stamp
        if (options.showPageNumbers) {
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (options.paperTheme == PaperTheme.DARK_SLATE) android.graphics.Color.LTGRAY else android.graphics.Color.GRAY
                textSize = 14f * options.resolutionScale
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("Slide ${slideData.slideNumber}", width - 40f, height - 30f, footerPaint)
        }

        return RenderedPage(
            pageNumber = slideData.slideNumber,
            title = slideData.title,
            bitmap = bitmap,
            widthPx = width,
            heightPx = height
        )
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
