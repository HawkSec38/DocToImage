package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocToImageViewModel
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.FormatBadge
import com.example.ui.components.PipelineInfoDialog
import com.example.ui.components.ZoomableImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: DocToImageViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val doc = uiState.currentDocument

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (doc != null) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FormatBadge(documentType = doc.documentType)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = doc.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Page ${uiState.selectedPageIndex + 1} of ${doc.pages.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("Document Viewer")
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("viewer_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (doc != null) {
                        IconButton(
                            onClick = { viewModel.togglePipelineInfo(true) },
                            modifier = Modifier.testTag("viewer_pipeline_info_btn")
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = "Pipeline Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleExportSheet(true) },
                            modifier = Modifier.testTag("viewer_export_btn")
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Export & Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (doc != null && doc.pages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // Filmstrip Thumbnails
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(doc.pages) { index, page ->
                                val isSelected = (index == uiState.selectedPageIndex)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setPageIndex(index) }
                                        .testTag("page_thumb_$index")
                                ) {
                                    Image(
                                        bitmap = page.bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 68.dp, height = 48.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                Color.Black.copy(alpha = 0.65f),
                                                RoundedCornerShape(topStart = 4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val currentPage = doc.pages.getOrNull(uiState.selectedPageIndex)
                        if (currentPage != null) {
                            Text(
                                text = "${currentPage.widthPx} × ${currentPage.heightPx} px • Direct Canvas Raster (Zero PDF)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("viewer_screen_content")
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.loadingMessage.ifEmpty { "Rendering document directly to image..." },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (doc != null && doc.pages.isNotEmpty()) {
                val currentPage = doc.pages.getOrElse(uiState.selectedPageIndex) { doc.pages.first() }

                // Zoomable image rendering
                ZoomableImage(
                    bitmap = currentPage.bitmap,
                    contentDescription = "Rendered ${doc.fileName} Page ${currentPage.pageNumber}",
                    modifier = Modifier.fillMaxSize()
                )

                // Previous Page Arrow
                if (uiState.selectedPageIndex > 0) {
                    FilledIconButton(
                        onClick = { viewModel.setPageIndex(uiState.selectedPageIndex - 1) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .testTag("prev_page_btn")
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }
                }

                // Next Page Arrow
                if (uiState.selectedPageIndex < doc.pages.size - 1) {
                    FilledIconButton(
                        onClick = { viewModel.setPageIndex(uiState.selectedPageIndex + 1) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .testTag("next_page_btn")
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No document loaded to display.")
                }
            }
        }
    }

    // Pipeline Info Dialog
    if (uiState.isPipelineInfoOpen && doc != null) {
        PipelineInfoDialog(
            document = doc,
            onDismiss = { viewModel.togglePipelineInfo(false) }
        )
    }

    // Export Bottom Sheet
    if (uiState.isExportSheetOpen && doc != null && doc.pages.isNotEmpty()) {
        val currentPage = doc.pages.getOrElse(uiState.selectedPageIndex) { doc.pages.first() }
        ExportBottomSheet(
            document = doc,
            currentPage = currentPage,
            exporter = viewModel.repository.exporter,
            onDismiss = { viewModel.toggleExportSheet(false) }
        )
    }
}
