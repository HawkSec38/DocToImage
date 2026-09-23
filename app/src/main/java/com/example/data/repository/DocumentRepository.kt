package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.database.DocumentHistoryDao
import com.example.data.database.DocumentHistoryEntity
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedDocument
import com.example.engine.DirectDocumentRenderer
import com.example.engine.exporter.ImageExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(
    private val context: Context,
    private val historyDao: DocumentHistoryDao
) {
    private val renderer = DirectDocumentRenderer(context)
    val exporter = ImageExporter(context)

    val history: Flow<List<DocumentHistoryEntity>> = historyDao.getAllHistory()

    suspend fun renderUri(
        uri: Uri,
        fileName: String,
        options: RenderOptions,
        sourceType: String = "USER_FILE"
    ): RenderedDocument = withContext(Dispatchers.IO) {
        val doc = renderer.renderUri(uri, fileName, options)
        saveHistoryEntry(doc, uri.toString(), sourceType)
        doc
    }

    suspend fun renderBytes(
        bytes: ByteArray,
        fileName: String,
        options: RenderOptions,
        sourceType: String = "SAMPLE"
    ): RenderedDocument = withContext(Dispatchers.IO) {
        val doc = renderer.renderBytes(bytes, fileName, options)
        saveHistoryEntry(doc, null, sourceType)
        doc
    }

    private suspend fun saveHistoryEntry(doc: RenderedDocument, uriStr: String?, sourceType: String) {
        val thumbPath = if (doc.pages.isNotEmpty()) {
            val thumb = doc.pages.first().bitmap
            saveThumbBitmap(thumb, doc.fileName)
        } else null

        val entity = DocumentHistoryEntity(
            title = doc.fileName,
            format = doc.documentType.primaryExtension.uppercase(),
            fileSizeBytes = doc.fileSizeBytes,
            pageCount = doc.pages.size,
            renderedAt = System.currentTimeMillis(),
            cachedThumbPath = thumbPath,
            sourceUri = uriStr,
            sourceType = sourceType
        )
        historyDao.insert(entity)
    }

    private fun saveThumbBitmap(bmp: Bitmap, docName: String): String {
        val thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val thumbFile = File(thumbsDir, "thumb_${System.currentTimeMillis()}_${docName.take(10).replace(' ', '_')}.jpg")
        val scaled = Bitmap.createScaledBitmap(bmp, 280, (280f * bmp.height / bmp.width).toInt().coerceAtLeast(100), true)
        FileOutputStream(thumbFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        return thumbFile.absolutePath
    }

    suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearAll()
}
