package com.example.engine.exporter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.model.RenderedDocument
import com.example.data.model.RenderedPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles exporting rendered document page Bitmaps directly to PNG/JPEG files,
 * generating FileProvider Uris, sharing via system sheets, and saving to gallery.
 */
class ImageExporter(private val context: Context) {

    enum class ExportFormat(val extension: String, val compressFormat: Bitmap.CompressFormat, val mimeType: String) {
        PNG("png", Bitmap.CompressFormat.PNG, "image/png"),
        JPEG("jpg", Bitmap.CompressFormat.JPEG, "image/jpeg")
    }

    suspend fun savePageToCache(
        page: RenderedPage,
        docName: String,
        format: ExportFormat = ExportFormat.PNG
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "rendered_docs").apply { mkdirs() }
        val cleanDocName = docName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(cacheDir, "${cleanDocName}_page_${page.pageNumber}.${format.extension}")
        FileOutputStream(file).use { out ->
            val quality = if (format == ExportFormat.JPEG) 95 else 100
            page.bitmap.compress(format.compressFormat, quality, out)
        }
        file
    }

    suspend fun saveDocumentPages(
        doc: RenderedDocument,
        format: ExportFormat = ExportFormat.PNG
    ): List<File> = withContext(Dispatchers.IO) {
        val savedFiles = mutableListOf<File>()
        for (page in doc.pages) {
            val file = savePageToCache(page, doc.fileName, format)
            savedFiles.add(file)
        }
        savedFiles
    }

    fun getFileUri(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun sharePageImage(page: RenderedPage, docName: String, format: ExportFormat = ExportFormat.PNG): Intent {
        val cacheDir = File(context.cacheDir, "rendered_docs").apply { mkdirs() }
        val cleanDocName = docName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(cacheDir, "${cleanDocName}_page_${page.pageNumber}.${format.extension}")
        FileOutputStream(file).use { out ->
            val quality = if (format == ExportFormat.JPEG) 95 else 100
            page.bitmap.compress(format.compressFormat, quality, out)
        }
        val uri = getFileUri(file)

        return Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$docName - Page ${page.pageNumber}")
            putExtra(Intent.EXTRA_TEXT, "Rendered directly from $docName using DocToImage (0 intermediate PDF steps).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareAllPages(files: List<File>, docName: String): Intent {
        val uris = ArrayList(files.map { getFileUri(it) })
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "$docName - All ${files.size} Rendered Pages")
            putExtra(Intent.EXTRA_TEXT, "Rendered directly from $docName (zero PDF conversion).")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyToClipboard(page: RenderedPage, docName: String) {
        val cacheDir = File(context.cacheDir, "rendered_docs").apply { mkdirs() }
        val cleanDocName = docName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val file = File(cacheDir, "${cleanDocName}_page_${page.pageNumber}.png")
        FileOutputStream(file).use { out ->
            page.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = getFileUri(file)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "$docName Page ${page.pageNumber}", uri)
        clipboard.setPrimaryClip(clip)
    }
}
