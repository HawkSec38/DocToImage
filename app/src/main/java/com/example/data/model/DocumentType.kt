package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class DocumentType(
    val displayName: String,
    val primaryExtension: String,
    val supportedExtensions: List<String>,
    val badgeColor: Color,
    val description: String
) {
    PPTX(
        displayName = "PowerPoint (PPTX)",
        primaryExtension = "pptx",
        supportedExtensions = listOf("pptx", "ppt"),
        badgeColor = Color(0xFFE65100), // Vibrant Orange
        description = "Presentations, slides, vector shapes & text runs"
    ),
    DOCX(
        displayName = "Word Document (DOCX)",
        primaryExtension = "docx",
        supportedExtensions = listOf("docx", "doc"),
        badgeColor = Color(0xFF1565C0), // Deep Blue
        description = "Word docs, formatted paragraphs, headers & tables"
    ),
    XLSX(
        displayName = "Excel Spreadsheet (XLSX)",
        primaryExtension = "xlsx",
        supportedExtensions = listOf("xlsx", "xls"),
        badgeColor = Color(0xFF2E7D32), // Forest Green
        description = "Spreadsheets, cell grids, formulas & column layouts"
    ),
    RTF(
        displayName = "Rich Text Format (RTF)",
        primaryExtension = "rtf",
        supportedExtensions = listOf("rtf"),
        badgeColor = Color(0xFF6A1B9A), // Deep Purple
        description = "Styled rich text with font formatting & colors"
    ),
    MARKDOWN(
        displayName = "Markdown (MD)",
        primaryExtension = "md",
        supportedExtensions = listOf("md", "markdown"),
        badgeColor = Color(0xFF00838F), // Teal
        description = "Structured headings, code blocks, lists & tables"
    ),
    HTML(
        displayName = "Web Document (HTML)",
        primaryExtension = "html",
        supportedExtensions = listOf("html", "htm"),
        badgeColor = Color(0xFFD84315), // Rust Orange
        description = "HTML tags, styled text blocks & markup elements"
    ),
    CSV(
        displayName = "CSV / TSV Table",
        primaryExtension = "csv",
        supportedExtensions = listOf("csv", "tsv"),
        badgeColor = Color(0xFF4527A0), // Deep Indigo
        description = "Tabular data, delimiters & striped data grids"
    ),
    TXT(
        displayName = "Plain Text (TXT)",
        primaryExtension = "txt",
        supportedExtensions = listOf("txt", "log", "json", "xml"),
        badgeColor = Color(0xFF37474F), // Slate Gray
        description = "Monospace/proportional text with auto-pagination"
    ),
    UNKNOWN(
        displayName = "Generic Document",
        primaryExtension = "bin",
        supportedExtensions = emptyList(),
        badgeColor = Color(0xFF546E7A),
        description = "Direct stream analysis & rasterization"
    );

    companion object {
        fun fromFileName(fileName: String): DocumentType {
            val lower = fileName.lowercase().trim()
            val ext = lower.substringAfterLast('.', "")
            for (type in entries) {
                if (type.supportedExtensions.contains(ext)) {
                    return type
                }
            }
            return when {
                lower.endsWith(".pptx") || lower.endsWith(".ppt") -> PPTX
                lower.endsWith(".docx") || lower.endsWith(".doc") -> DOCX
                lower.endsWith(".xlsx") || lower.endsWith(".xls") -> XLSX
                lower.endsWith(".rtf") -> RTF
                lower.endsWith(".md") || lower.endsWith(".markdown") -> MARKDOWN
                lower.endsWith(".html") || lower.endsWith(".htm") -> HTML
                lower.endsWith(".csv") || lower.endsWith(".tsv") -> CSV
                lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".xml") -> TXT
                else -> UNKNOWN
            }
        }
    }
}
