package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PaperworkContainer
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.DocumentFilter
import com.example.domain.model.DocumentType
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.SortOption
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PaperworkViewModel(private val container: PaperworkContainer) : ViewModel() {
    private val repository = container.documentRepository
    private val preferences = container.preferencesRepository

    val preferencesState: StateFlow<UserPreferencesState> = preferences.preferencesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesState()
    )
    val allDocuments: StateFlow<List<PaperworkDocument>> = repository.allDocumentsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val vaultStorageBytes: StateFlow<Long> = repository.storageBytesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _selectedFilter = MutableStateFlow(DocumentFilter.ALL)
    val selectedFilter = _selectedFilter.asStateFlow()
    private val _selectedSort = MutableStateFlow(SortOption.DATE_MODIFIED)
    val selectedSort = _selectedSort.asStateFlow()
    private val _sortAscending = MutableStateFlow(false)
    val sortAscending = _sortAscending.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy = _isBusy.asStateFlow()
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    val filteredDocuments: StateFlow<List<PaperworkDocument>> = combine(
        allDocuments, searchQuery, selectedFilter, selectedSort, sortAscending
    ) { documents, query, filter, sort, ascending ->
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val filtered = documents.filter { document ->
            val matchesQuery = query.isBlank() || document.title.contains(query, true) ||
                document.nativePdfText.contains(query, true) || document.ocrRecognizedText.contains(query, true)
            val matchesFilter = when (filter) {
                DocumentFilter.ALL -> true
                DocumentFilter.PDF -> document.documentType == DocumentType.PDF
                DocumentFilter.SCANNED -> document.documentType == DocumentType.SCANNED
                DocumentFilter.RECENT -> document.modifiedAt >= cutoff
            }
            matchesQuery && matchesFilter
        }
        val comparator = when (sort) {
            SortOption.DATE_MODIFIED -> compareBy<PaperworkDocument> { it.modifiedAt }
            SortOption.DATE_CREATED -> compareBy { it.createdAt }
            SortOption.NAME -> compareBy { it.title.lowercase() }
            SortOption.FILE_SIZE -> compareBy { it.fileSize }
        }
        (if (ascending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator.reversed()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(value: String) { _searchQuery.value = value }
    fun setFilter(value: DocumentFilter) { _selectedFilter.value = value }
    fun setSort(value: SortOption) { _selectedSort.value = value }
    fun toggleSortDirection() { _sortAscending.value = !_sortAscending.value }
    fun clearMessage() { _snackbarMessage.value = null }
    fun showMessage(value: String) { _snackbarMessage.value = value }

    fun importPdf(uri: Uri, onSuccess: (PaperworkDocument) -> Unit = {}) = runDocumentAction("Importing PDF") {
        repository.importPdf(uri).also(onSuccess)
    }


    fun rename(document: PaperworkDocument, title: String) = runDocumentAction("Renaming document") {
        repository.rename(document, title)
    }

    fun duplicate(document: PaperworkDocument, onSuccess: (PaperworkDocument) -> Unit = {}) = runDocumentAction("Duplicating document") {
        repository.duplicate(document).also(onSuccess)
    }

    fun delete(document: PaperworkDocument) = runDocumentAction("Deleting document") {
        repository.delete(document)
    }

    fun updateLastOpenedPage(documentId: String, page: Int) {
        viewModelScope.launch { repository.updateLastOpenedPage(documentId, page) }
    }

    fun setTheme(value: ThemePreference) { viewModelScope.launch { preferences.setThemePreference(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { preferences.setDynamicColor(value) } }
    fun setGridView(value: Boolean) { viewModelScope.launch { preferences.setGridView(value) } }
    fun setQuality(value: com.example.domain.model.PdfQuality) { viewModelScope.launch { preferences.setDefaultPdfQuality(value) } }
    fun setPageSize(value: com.example.domain.model.PageSizeOption) { viewModelScope.launch { preferences.setDefaultPageSize(value) } }

    fun savePages(images: List<Uri>, title: String, type: DocumentType,
        filter: com.example.domain.model.ScanFilterMode, format: com.example.domain.model.ScanOutputFormat,
        onSuccess: (PaperworkDocument?) -> Unit) = runDocumentAction("Saving pages") {
        val prefs = preferencesState.value
        onSuccess(repository.savePages(images, title, type, filter, format, prefs.defaultPageSize, prefs.defaultPdfQuality))
    }

    fun resetLegacyVault(onComplete: () -> Unit) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { container.legacyResetManager.clearLegacyData() }
            onComplete()
        }
    }

    fun needsLegacyReset(): Boolean = container.legacyResetManager.needsReset()

    fun createShareIntent(document: PaperworkDocument) = repository.createShareIntent(document)
    suspend fun copyToCache(document: PaperworkDocument, purpose: String) = repository.copyToCache(document, purpose)

    private fun runDocumentAction(label: String, action: suspend () -> Unit) {
        if (_isBusy.value) return
        _isBusy.value = true
        viewModelScope.launch {
            _isBusy.value = true
            try {
                action()
                _snackbarMessage.value = "$label complete"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _snackbarMessage.value = error.message ?: "$label failed"
            } finally {
                _isBusy.value = false
            }
        }
    }

    companion object {
        fun provideFactory(context: Context, container: PaperworkContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = PaperworkViewModel(container) as T
            }
    }
}
