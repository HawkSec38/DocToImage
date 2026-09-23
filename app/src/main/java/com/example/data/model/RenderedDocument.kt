package com.example.data.model

import android.graphics.Bitmap

data class RenderedPage(
    val pageNumber: Int,
    val title: String,
    val bitmap: Bitmap,
    val widthPx: Int,
    val heightPx: Int
)

data class RenderedDocument(
    val id: String,
    val fileName: String,
    val documentType: DocumentType,
    val pages: List<RenderedPage>,
    val renderDurationMs: Long,
    val fileSizeBytes: Long,
    val pipelineDescription: String
)
