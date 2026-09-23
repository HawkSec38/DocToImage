package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.DocumentHistoryEntity
import com.example.data.model.DocumentType
import com.example.data.model.RenderOptions
import com.example.data.model.RenderedDocument
import com.example.data.model.RenderedPage
import com.example.data.repository.DocumentRepository
import com.example.engine.exporter.ImageExporter
import com.example.engine.samples.SampleDocumentGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DocUiState(
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val currentDocument: RenderedDocument? = null,
    val selectedPageIndex: Int = 0,
    val renderOptions: RenderOptions = RenderOptions(),
    val userMessage: String? = null,
    val isPipelineInfoOpen: Boolean = false,
    val isExportSheetOpen: Boolean = false,
    val lastExportedFiles: List<File> = emptyList()
)

class DocToImageViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = DocumentRepository(application, db.documentHistoryDao())

    val historyList: StateFlow<List<DocumentHistoryEntity>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(DocUiState())
    val uiState: StateFlow<DocUiState> = _uiState.asStateFlow()

    // Cache of the last loaded raw document source for instant re-rendering when settings change
    private var lastRawBytes: ByteArray? = null
    private var lastFileName: String? = null
    private var lastSourceUri: Uri? = null

    init {
        // Automatically load the first sample document so user immediately sees direct-to-image rendering in action
        loadDefaultSample()
    }

    private fun loadDefaultSample() {
        val sample = SampleDocumentGenerator.getSampleDocuments().firstOrNull() ?: return
        loadSample(sample)
    }

    fun loadSample(sample: SampleDocumentGenerator.SampleDocItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Rasterizing ${sample.fileName} directly to high-res images (0 PDF steps)..."
                )
            }
            try {
                val bytes = sample.contentBytesProvider()
                lastRawBytes = bytes
                lastFileName = sample.fileName
                lastSourceUri = null

                val doc = repository.renderBytes(bytes, sample.fileName, _uiState.value.renderOptions, "SAMPLE")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDocument = doc,
                        selectedPageIndex = 0,
                        userMessage = "Rendered ${doc.pages.size} image(s) in ${doc.renderDurationMs}ms without PDF conversion!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Rendering failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun renderUserFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Analyzing document structure and rasterizing directly to Bitmaps..."
                )
            }
            try {
                lastSourceUri = uri
                lastFileName = fileName
                lastRawBytes = null

                val doc = repository.renderUri(uri, fileName, _uiState.value.renderOptions, "USER_FILE")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDocument = doc,
                        selectedPageIndex = 0,
                        userMessage = "Successfully rendered ${doc.pages.size} page image(s) in ${doc.renderDurationMs}ms!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Could not render file: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun renderCustomText(title: String, content: String, type: DocumentType) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Synthesizing styled layout and rasterizing to Canvas Bitmaps..."
                )
            }
            try {
                val fullFileName = if (title.endsWith(".${type.primaryExtension}")) title else "$title.${type.primaryExtension}"
                val bytes = content.toByteArray()
                lastRawBytes = bytes
                lastFileName = fullFileName
                lastSourceUri = null

                val doc = repository.renderBytes(bytes, fullFileName, _uiState.value.renderOptions, "COMPOSED")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentDocument = doc,
                        selectedPageIndex = 0,
                        userMessage = "Directly rendered ${doc.pages.size} image(s)!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Error rendering text: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun setPageIndex(index: Int) {
        val maxIndex = (_uiState.value.currentDocument?.pages?.size ?: 1) - 1
        _uiState.update { it.copy(selectedPageIndex = index.coerceIn(0, maxIndex.coerceAtLeast(0))) }
    }

    fun updateRenderOptions(newOptions: RenderOptions) {
        _uiState.update { it.copy(renderOptions = newOptions) }
        reRenderWithCurrentOptions()
    }

    private fun reRenderWithCurrentOptions() {
        val options = _uiState.value.renderOptions
        val fileName = lastFileName ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingMessage = "Re-rendering at ${(options.resolutionScale * 100).toInt()}% resolution..."
                )
            }
            try {
                val doc = if (lastRawBytes != null) {
                    repository.renderBytes(lastRawBytes!!, fileName, options, "SAMPLE")
                } else if (lastSourceUri != null) {
                    repository.renderUri(lastSourceUri!!, fileName, options, "USER_FILE")
                } else null

                if (doc != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentDocument = doc,
                            selectedPageIndex = it.selectedPageIndex.coerceAtMost(doc.pages.size - 1),
                            userMessage = "Updated render (${(options.resolutionScale * 100).toInt()}%, ${doc.renderDurationMs}ms)"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userMessage = "Failed to update render: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun toggleExportSheet(open: Boolean) {
        _uiState.update { it.copy(isExportSheetOpen = open) }
    }

    fun togglePipelineInfo(open: Boolean) {
        _uiState.update { it.copy(isPipelineInfoOpen = open) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
