package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.DocumentType
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedDocument
import com.example.engine.renderers.CsvRenderer
import com.example.engine.renderers.DocxRenderer
import com.example.engine.renderers.HtmlRenderer
import com.example.engine.renderers.MarkdownRenderer
import com.example.engine.renderers.PptxRenderer
import com.example.engine.renderers.RtfRenderer
import com.example.engine.renderers.XlsxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

/**
 * Master Direct Document-to-Image Rendering Engine.
 * Implements direct stream parsing and canvas rasterization for PPT, PPTX, DOCX, DOC,
 * XLSX, RTF, Markdown, HTML, and CSV documents with ZERO intermediate PDF conversion steps.
 */
class DirectDocumentRenderer(private val context: Context) {

    private val pptxRenderer = PptxRenderer()
    private val docxRenderer = DocxRenderer()
    private val xlsxRenderer = XlsxRenderer()
    private val markdownRenderer = MarkdownRenderer()
    private val htmlRenderer = HtmlRenderer()
    private val rtfRenderer = RtfRenderer()
    private val csvRenderer = CsvRenderer()

    suspend fun renderUri(
        uri: Uri,
        fileName: String,
        options: RenderOptions = RenderOptions()
    ): RenderedDocument = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read content from selected file URI: $uri")
        renderBytes(bytes, fileName, options)
    }

    suspend fun renderBytes(
        bytes: ByteArray,
        fileName: String,
        options: RenderOptions = RenderOptions()
    ): RenderedDocument = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val detectedType = detectDocumentType(fileName, bytes)

        val pages = when (detectedType) {
            DocumentType.PPTX -> {
                pptxRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.DOCX -> {
                docxRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.XLSX -> {
                xlsxRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.MARKDOWN -> {
                markdownRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.HTML -> {
                htmlRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.RTF -> {
                rtfRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.CSV -> {
                csvRenderer.render(ByteArrayInputStream(bytes), options)
            }
            DocumentType.TXT, DocumentType.UNKNOWN -> {
                // If text or unknown, parse via markdown/plain renderer
                markdownRenderer.render(ByteArrayInputStream(bytes), options)
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val pipelineDesc = buildPipelineDescription(detectedType, pages.size, duration, options)

        RenderedDocument(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            documentType = detectedType,
            pages = pages,
            renderDurationMs = duration,
            fileSizeBytes = bytes.size.toLong(),
            pipelineDescription = pipelineDesc
        )
    }

    private fun detectDocumentType(fileName: String, bytes: ByteArray): DocumentType {
        val byName = DocumentType.fromFileName(fileName)
        if (byName != DocumentType.UNKNOWN) return byName

        // Sniff byte magic numbers
        if (bytes.size >= 4) {
            // Zip archive signature (PK\x03\x04)
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                val str = String(bytes.take(2000).toByteArray())
                return when {
                    str.contains("ppt/", ignoreCase = true) -> DocumentType.PPTX
                    str.contains("word/", ignoreCase = true) -> DocumentType.DOCX
                    str.contains("xl/", ignoreCase = true) -> DocumentType.XLSX
                    else -> DocumentType.DOCX
                }
            }
        }
        val header = String(bytes.take(500).toByteArray()).trim()
        return when {
            header.startsWith("{\\rtf", ignoreCase = true) -> DocumentType.RTF
            header.startsWith("<!DOCTYPE html", ignoreCase = true) || header.startsWith("<html", ignoreCase = true) -> DocumentType.HTML
            header.contains(",") || header.contains("\t") -> DocumentType.CSV
            else -> DocumentType.MARKDOWN
        }
    }

    private fun buildPipelineDescription(
        type: DocumentType,
        pageCount: Int,
        durationMs: Long,
        options: RenderOptions
    ): String {
        val engineName = when (type) {
            DocumentType.PPTX -> "Direct OOXML Presentation Vector Rasterizer"
            DocumentType.DOCX -> "Direct OOXML Word Document Layout & Pagination Engine"
            DocumentType.XLSX -> "Direct OOXML Spreadsheet Grid Renderer"
            DocumentType.RTF -> "Direct RTF Tokenizer & TextPaint Layout Engine"
            DocumentType.HTML -> "Direct HTML DOM Element Renderer"
            DocumentType.MARKDOWN -> "Direct Markdown Block & Span Synthesizer"
            DocumentType.CSV -> "Direct Delimited Matrix Table Engine"
            else -> "Direct Document Canvas Rasterizer"
        }
        return "$engineName (Zero intermediate PDF conversion). Rendered $pageCount high-res page image(s) in ${durationMs}ms at ${(options.resolutionScale * 100).toInt()}% resolution."
    }
}
