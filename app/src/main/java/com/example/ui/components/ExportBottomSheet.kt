package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RenderedDocument
import com.example.data.model.RenderedPage
import com.example.engine.exporter.ImageExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    document: RenderedDocument,
    currentPage: RenderedPage,
    exporter: ImageExporter,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("export_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Export Rendered Images",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${document.fileName} • Page ${currentPage.pageNumber} of ${document.pages.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "CURRENT PAGE (PAGE ${currentPage.pageNumber})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Save PNG
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val file = exporter.savePageToCache(currentPage, document.fileName, ImageExporter.ExportFormat.PNG)
                        Toast.makeText(context, "Saved PNG: ${file.name}", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_current_png_btn")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Current Page as PNG (Lossless)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save JPEG
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val file = exporter.savePageToCache(currentPage, document.fileName, ImageExporter.ExportFormat.JPEG)
                        Toast.makeText(context, "Saved JPEG: ${file.name}", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_current_jpeg_btn")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Current Page as JPEG (Compressed)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                // Share
                OutlinedButton(
                    onClick = {
                        val shareIntent = exporter.sharePageImage(currentPage, document.fileName)
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Page Image"))
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_current_page_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Copy to Clipboard
                OutlinedButton(
                    onClick = {
                        exporter.copyToClipboard(currentPage, document.fileName)
                        Toast.makeText(context, "Copied page image to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_to_clipboard_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }

            if (document.pages.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ALL PAGES (${document.pages.size} IMAGES)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            val files = exporter.saveDocumentPages(document, ImageExporter.ExportFormat.PNG)
                            Toast.makeText(context, "Saved all ${files.size} pages to storage", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_all_pages_btn")
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save All ${document.pages.size} Pages as Images")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val files = exporter.saveDocumentPages(document, ImageExporter.ExportFormat.PNG)
                            val shareIntent = exporter.shareAllPages(files, document.fileName)
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share All Page Images"))
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_all_pages_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share All ${document.pages.size} Pages")
                }
            }
        }
    }
}
