package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentType
import com.example.ui.DocToImageViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposeDocScreen(
    viewModel: DocToImageViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToViewer: () -> Unit
) {
    var selectedType by remember { mutableStateOf(DocumentType.MARKDOWN) }
    var docTitle by remember { mutableStateOf("Custom Document") }
    var contentText by remember {
        mutableStateOf(
            """
            # Custom Direct Document Render
            **Zero PDF Conversion** • Real-time Canvas Rasterization
            
            This document is rendered directly into **high-resolution Bitmaps** preserving original formatting:
            
            - **Header Typography:** Clear, scalable fonts with accent lines
            - **Spans & Lists:** Bullet items and numbered workflows
            - **Structured Tables:** Clean cell borders and background ribbons
            
            | Feature | Direct Image Engine | Legacy PDF Converter |
            |---------|---------------------|----------------------|
            | Memory  | Low (In-memory)     | High (Temp PDF files)|
            | Latency | < 30ms              | > 400ms              |
            | Privacy | 100% On-Device      | Often Cloud-Reliant  |
            
            ```kotlin
            fun renderDirectlyToImage(doc: Document) = doc.rasterize()
            ```
            """.trimIndent()
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Compose & Render", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("compose_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("compose_screen_column")
        ) {
            Text(
                text = "DOCUMENT FORMAT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Format Selection Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    DocumentType.MARKDOWN,
                    DocumentType.HTML,
                    DocumentType.CSV,
                    DocumentType.TXT
                ).forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.displayName) },
                        modifier = Modifier.testTag("select_format_${type.primaryExtension}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Document Title
            OutlinedTextField(
                value = docTitle,
                onValueChange = { docTitle = it },
                label = { Text("Document Title") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Insertion Toolbar
            Text(
                text = "QUICK INSERT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { contentText += "\n\n## Section Heading\n" },
                    label = { Text("Heading") },
                    leadingIcon = { Icon(Icons.Default.Title, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { contentText += "\n\n- Feature Bullet Point\n" },
                    label = { Text("Bullet") },
                    leadingIcon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = { contentText += "\n\n| Item | Qty | Cost |\n|---|---|---|\n| Product A | 10 | $150 |\n" },
                    label = { Text("Table") },
                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Document Content Editor
            OutlinedTextField(
                value = contentText,
                onValueChange = { contentText = it },
                label = { Text("Document Content (${selectedType.displayName})") },
                minLines = 10,
                maxLines = 18,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_content_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Render Button
            Button(
                onClick = {
                    viewModel.renderCustomText(docTitle, contentText, selectedType)
                    onNavigateToViewer()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("render_composed_doc_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Render Directly to Image", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
