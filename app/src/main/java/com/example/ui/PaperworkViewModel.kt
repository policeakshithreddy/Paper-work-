package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PaperworkContainer
import com.example.core.image.NormalizedQuadCorners
import com.example.core.image.SafeBitmapDecoder
import com.example.core.pdf.PageImageSpec
import com.example.data.preferences.UserPreferencesState
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.DocumentFilter
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PdfQuality
import com.example.domain.model.PrintConfiguration
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.SortOption
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StagedScanPage(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap,
    val enhancedBitmap: Bitmap,
    val rotationDegrees: Int = 0,
    val cropNormalized: FloatArray = floatArrayOf(0f, 0f, 1f, 1f),
    val deskewAngleDegrees: Float = 0f
)

data class ScannerStudioState(
    val pages: List<StagedScanPage> = emptyList(),
    val selectedPageIndex: Int = 0,
    val showEnhancedPreview: Boolean = true,
    val activeFilterMode: ScanFilterMode = ScanFilterMode.ENHANCED_COLOR,
    val isRetakingCurrentPage: Boolean = false,
    val documentTitle: String = "",
    val selectedQuality: PdfQuality = PdfQuality.BALANCED,
    val selectedPageSize: PageSizeOption = PageSizeOption.A4,
    val selectedOrientation: PageOrientation = PageOrientation.PORTRAIT,
    val selectedMargin: MarginSize = MarginSize.SMALL,
    val isSaving: Boolean = false
)

data class ConversionStudioState(
    val pages: List<StagedScanPage> = emptyList(),
    val documentTitle: String = "",
    val selectedPageSize: PageSizeOption = PageSizeOption.A4,
    val selectedOrientation: PageOrientation = PageOrientation.PORTRAIT,
    val selectedMargin: MarginSize = MarginSize.SMALL,
    val selectedQuality: PdfQuality = PdfQuality.BALANCED,
    val isConverting: Boolean = false
)

class PaperworkViewModel(
    private val appContext: Context,
    val container: PaperworkContainer
) : ViewModel() {

    private val repo = container.documentRepository
    private val prefsRepo = container.preferencesRepository
    val cryptoManager = container.cryptoManager
    val pdfEngine = container.pdfEngine
    val printHelper = container.printHelper
    val accessLockManager = container.accessLockManager

    private var activeConversionJob: Job? = null

    val preferencesState: StateFlow<UserPreferencesState> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferencesState()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(DocumentFilter.ALL)
    val selectedFilter: StateFlow<DocumentFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(SortOption.DATE_MODIFIED)
    val selectedSort: StateFlow<SortOption> = _selectedSort.asStateFlow()

    private val _sortAscending = MutableStateFlow(false)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    private val _vaultStorageBytes = MutableStateFlow(0L)
    val vaultStorageBytes: StateFlow<Long> = _vaultStorageBytes.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerStudioState())
    val scannerState: StateFlow<ScannerStudioState> = _scannerState.asStateFlow()

    private val _conversionState = MutableStateFlow(ConversionStudioState())
    val conversionState: StateFlow<ConversionStudioState> = _conversionState.asStateFlow()

    val allDocuments: StateFlow<List<PaperworkDocument>> = repo.allDocumentsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredDocuments: StateFlow<List<PaperworkDocument>> = combine(
        allDocuments,
        _searchQuery,
        _selectedFilter,
        _selectedSort,
        _sortAscending
    ) { docs, query, filter, sort, ascending ->
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000L

        val filtered = docs.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.fileName.contains(query, ignoreCase = true) ||
                doc.combinedSearchableText.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                DocumentFilter.ALL -> true
                DocumentFilter.PDF -> doc.documentType == com.example.domain.model.DocumentType.PDF ||
                    doc.documentType == com.example.domain.model.DocumentType.CONVERTED
                DocumentFilter.SCANNED -> doc.documentType == com.example.domain.model.DocumentType.SCANNED
                DocumentFilter.RECENT -> doc.modifiedAt >= sevenDaysAgo
            }
            matchesQuery && matchesFilter
        }

        val sorted = when (sort) {
            SortOption.DATE_MODIFIED -> filtered.sortedBy { it.modifiedAt }
            SortOption.DATE_CREATED -> filtered.sortedBy { it.createdAt }
            SortOption.NAME -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            SortOption.FILE_SIZE -> filtered.sortedBy { it.fileSize }
        }
        if (ascending) sorted else sorted.reversed()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshStorageMetrics()
    }

    fun refreshStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            _vaultStorageBytes.value = repo.calculateVaultStorageBytes()
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: DocumentFilter) {
        _selectedFilter.value = filter
    }

    fun updateSort(sort: SortOption, ascending: Boolean = _sortAscending.value) {
        _selectedSort.value = sort
        _sortAscending.value = ascending
    }

    // ─── Security, Salted PIN & Biometric Access Lock ─────────────────────────

    fun attemptPinUnlock(inputPin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val prefs = preferencesState.value
            val now = System.currentTimeMillis()
            if (prefs.lockoutUntilEpochMs > now) {
                val remainingSec = ((prefs.lockoutUntilEpochMs - now) / 1000L).coerceAtLeast(1L)
                onResult(false, "Too many failed attempts. Try again in ${remainingSec}s.")
                return@launch
            }

            val saltBytes = Base64.decode(prefs.pinSaltBase64, Base64.NO_WRAP)
            val expectedHashBytes = Base64.decode(prefs.pinHashBase64, Base64.NO_WRAP)
            val isValid = withContext(Dispatchers.Default) {
                cryptoManager.verifyPin(inputPin, saltBytes, expectedHashBytes)
            }

            if (isValid) {
                prefsRepo.resetFailedPinAttempts()
                _isSessionUnlocked.value = true
                onResult(true, null)
            } else {
                val nextCount = prefs.failedPinAttempts + 1
                val lockoutDuration = cryptoManager.calculateLockoutDurationMs(nextCount)
                val lockoutUntil = if (lockoutDuration > 0L) now + lockoutDuration else 0L
                prefsRepo.recordFailedPinAttempt(nextCount, lockoutUntil)
                val warnMsg = if (lockoutDuration > 0L) {
                    "Incorrect PIN ($nextCount failed attempts). Locked for ${lockoutDuration / 1000}s."
                } else {
                    "Incorrect PIN (${5 - nextCount} attempts remaining before cooldown)."
                }
                onResult(false, warnMsg)
            }
        }
    }

    fun onBiometricUnlockSucceeded() {
        viewModelScope.launch {
            prefsRepo.resetFailedPinAttempts()
            _isSessionUnlocked.value = true
        }
    }

    fun setOrUpdatePin(newPin: String) {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) {
            showMessage("PIN must be exactly 4 digits.")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            val salt = cryptoManager.generatePinSalt()
            val hash = cryptoManager.hashPin(newPin, salt)
            prefsRepo.configurePin(salt, hash)
            _isSessionUnlocked.value = true
            showMessage("4-digit PIN lock enabled with PBKDF2-SHA256 salting.")
        }
    }

    fun disablePinLock() {
        viewModelScope.launch {
            prefsRepo.disablePin()
            _isSessionUnlocked.value = true
            showMessage("App access lock disabled. Keystore AES-256-GCM encryption remains active.")
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setBiometricUnlockEnabled(enabled)
        }
    }

    fun lockSessionNow() {
        if (preferencesState.value.pinEnabled) {
            _isSessionUnlocked.value = false
        }
    }

    /**
     * Explicit user-confirmed transition from `INVALIDATED` / `RECOVERY_REQUIRED` -> `NEW_KEY_CREATED` -> `ACTIVE`.
     * Never claims old `v{N}` documents can be decrypted by the new `v{N+1}` key.
     */
    fun createReplacementKeyAfterInvalidation(purgeUnreadableOldDocuments: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newVer = cryptoManager.createReplacementKeyAfterInvalidation()
            if (purgeUnreadableOldDocuments) {
                repo.purgeUnreadableOldKeyDocuments(newVer)
            }
            cryptoManager.acknowledgeNewKeyCreated()
            refreshStorageMetrics()
            showMessage("Provisioned replacement Keystore key v$newVer for new documents.")
        }
    }

    // ─── Preferences Mutators ────────────────────────────────────────────────

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setThemePreference(theme) }
    }

    fun setDefaultPdfQuality(quality: PdfQuality) {
        viewModelScope.launch { prefsRepo.setDefaultPdfQuality(quality) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { prefsRepo.setDefaultPageSize(size) }
    }

    fun setDefaultPrintOrder(order: com.example.domain.model.PrintPageOrder) {
        viewModelScope.launch { prefsRepo.setDefaultPrintOrder(order) }
    }

    fun setDefaultEnhancedScan(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDefaultEnhancedScan(enabled) }
    }

    fun setGridView(isGrid: Boolean) {
        viewModelScope.launch { prefsRepo.setGridView(isGrid) }
    }

    fun purgeTemporaryDecryptedCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val purged = repo.secureDocumentAccessManager.cleanupAbandonedSessions()
            refreshStorageMetrics()
            showMessage("Cleaned $purged temporary session files.")
        }
    }

    // ─── Sample Document Generator & External Import ─────────────────────────

    fun generateSampleDocuments() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repo.generateSampleDocuments()
                refreshStorageMetrics()
                showMessage("Added Vector PDF & Scanned Receipt (with Original & Enhanced Color layers).")
            } catch (e: Exception) {
                showMessage("Failed to create sample documents: ${e.message}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun importPdfFromUri(uri: Uri, onImported: (PaperworkDocument) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val displayName = resolveDisplayName(uri) ?: "Imported_Document.pdf"
                val imported = repo.importExternalPdfUri(uri, displayName)
                refreshStorageMetrics()
                if (imported != null) {
                    showMessage("Encrypted and imported '${imported.title}'.")
                    onImported(imported)
                } else {
                    showMessage("Unable to read selected PDF file.")
                }
            } catch (e: Exception) {
                showMessage("Import failed: ${e.message}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun renameDocument(document: PaperworkDocument, newTitle: String) {
        viewModelScope.launch {
            repo.renameDocument(document, newTitle)
            showMessage("Renamed to '${newTitle.trim()}'.")
        }
    }

    fun duplicateDocument(document: PaperworkDocument) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val copy = repo.duplicateDocument(document)
                refreshStorageMetrics()
                if (copy != null) {
                    showMessage("Created duplicate '${copy.title}'.")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteDocument(document: PaperworkDocument) {
        viewModelScope.launch {
            repo.deleteDocument(document)
            refreshStorageMetrics()
            showMessage("Deleted '${document.title}' from vault.")
        }
    }

    fun updateLastOpenedPage(documentId: String, pageIndex: Int) {
        viewModelScope.launch {
            repo.updateLastOpenedPage(documentId, pageIndex)
        }
    }

    fun toggleSavedScanEnhancement(
        document: PaperworkDocument,
        useEnhanced: Boolean,
        filterMode: ScanFilterMode = if (useEnhanced) ScanFilterMode.ENHANCED_COLOR else ScanFilterMode.ORIGINAL,
        onCompleted: (PaperworkDocument) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val updated = repo.switchScanDocumentEnhancementMode(
                    document = document,
                    useEnhanced = filterMode != ScanFilterMode.ORIGINAL,
                    filterMode = filterMode,
                    pageSize = preferencesState.value.defaultPageSize,
                    quality = preferencesState.value.defaultPdfQuality
                )
                refreshStorageMetrics()
                if (updated != null) {
                    showMessage("Switched '${updated.title}' to ${filterMode.label}.")
                    onCompleted(updated)
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun shareDocument(context: Context, document: PaperworkDocument) {
        if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
            showMessage("Cannot share unreadable document encrypted with invalidated key v${document.keyVersion}.")
            return
        }
        viewModelScope.launch {
            try {
                val session = withContext(Dispatchers.IO) {
                    repo.secureDocumentAccessManager.openDecryptedSession(
                        document = document,
                        purpose = "share"
                    ).getOrThrow()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    session.decryptedFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, document.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share ${document.title}"))
            } catch (e: Exception) {
                showMessage("Unable to share PDF: ${e.message}")
            }
        }
    }

    fun exportDocumentToSafUri(document: PaperworkDocument, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decryptedBytes = cryptoManager.decryptFromFile(
                    sourceEncryptedFile = File(document.encryptedPdfPath),
                    aad = document.id
                )
                appContext.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(decryptedBytes)
                    out.flush()
                }
                showMessage("Exported '${document.fileName}' to device storage.")
            } catch (e: Exception) {
                showMessage("Export failed: ${e.message}")
            }
        }
    }

    fun printDocument(
        activityContext: Context,
        document: PaperworkDocument,
        config: PrintConfiguration
    ) {
        viewModelScope.launch {
            printHelper.prepareAndLaunchSystemPrint(
                activityContext = activityContext,
                document = document,
                config = config,
                onError = { err -> showMessage(err) }
            )
        }
    }

    // ─── Unified Scanner Preview Studio State Management ─────────────────────

    fun startNewScanSession() {
        val prefs = preferencesState.value
        val timestamp = SimpleDateFormat("MMM d, HHmm", Locale.getDefault()).format(Date())
        val defaultFilter = if (prefs.defaultEnhancedScan) {
            ScanFilterMode.ENHANCED_COLOR
        } else {
            ScanFilterMode.ORIGINAL
        }
        _scannerState.value = ScannerStudioState(
            pages = emptyList(),
            selectedPageIndex = 0,
            showEnhancedPreview = prefs.defaultEnhancedScan,
            activeFilterMode = defaultFilter,
            isRetakingCurrentPage = false,
            documentTitle = "Scan $timestamp",
            selectedQuality = prefs.defaultPdfQuality,
            selectedPageSize = prefs.defaultPageSize,
            selectedOrientation = PageOrientation.PORTRAIT,
            selectedMargin = MarginSize.SMALL
        )
    }

    fun prepareSinglePageRetake() {
        _scannerState.update { it.copy(isRetakingCurrentPage = true) }
    }

    fun cancelSinglePageRetake() {
        _scannerState.update { it.copy(isRetakingCurrentPage = false) }
    }

    fun appendScannedUris(uris: List<Uri>, onReady: () -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val filterMode = _scannerState.value.activeFilterMode
                val targetEnhFilter = if (filterMode == ScanFilterMode.ORIGINAL) {
                    ScanFilterMode.ENHANCED_COLOR
                } else {
                    filterMode
                }
                val newPages = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        SafeBitmapDecoder.decodeUriSafely(appContext, uri, 2200)?.let { orig ->
                            val enh = pdfEngine.createEnhancedDocumentBitmap(orig, targetEnhFilter)
                            StagedScanPage(
                                originalBitmap = orig,
                                enhancedBitmap = enh
                            )
                        }
                    }
                }
                if (newPages.isNotEmpty()) {
                    _scannerState.update { state ->
                        val baseTitle = state.documentTitle.ifBlank {
                            val ts = SimpleDateFormat("MMM d, HHmm", Locale.getDefault()).format(Date())
                            "Scan $ts"
                        }
                        if (state.isRetakingCurrentPage && state.selectedPageIndex in state.pages.indices) {
                            val mutable = state.pages.toMutableList()
                            mutable[state.selectedPageIndex] = newPages.first()
                            state.copy(
                                pages = mutable,
                                isRetakingCurrentPage = false,
                                documentTitle = baseTitle
                            )
                        } else {
                            val combined = state.pages + newPages
                            state.copy(
                                pages = combined,
                                selectedPageIndex = combined.lastIndex.coerceAtLeast(0),
                                isRetakingCurrentPage = false,
                                documentTitle = baseTitle
                            )
                        }
                    }
                    onReady()
                } else {
                    showMessage("Could not decode image from selection.")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun appendCapturedScanBitmap(bitmap: Bitmap, onReady: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val filterMode = _scannerState.value.activeFilterMode
                val targetEnhFilter = if (filterMode == ScanFilterMode.ORIGINAL) {
                    ScanFilterMode.ENHANCED_COLOR
                } else {
                    filterMode
                }
                val page = withContext(Dispatchers.Default) {
                    val enh = pdfEngine.createEnhancedDocumentBitmap(bitmap, targetEnhFilter)
                    StagedScanPage(
                        originalBitmap = bitmap,
                        enhancedBitmap = enh
                    )
                }
                _scannerState.update { state ->
                    val baseTitle = state.documentTitle.ifBlank {
                        val ts = SimpleDateFormat("MMM d, HHmm", Locale.getDefault()).format(Date())
                        "Scan $ts"
                    }
                    if (state.isRetakingCurrentPage && state.selectedPageIndex in state.pages.indices) {
                        val mutable = state.pages.toMutableList()
                        mutable[state.selectedPageIndex] = page
                        state.copy(
                            pages = mutable,
                            isRetakingCurrentPage = false,
                            documentTitle = baseTitle
                        )
                    } else {
                        val combined = state.pages + page
                        state.copy(
                            pages = combined,
                            selectedPageIndex = combined.lastIndex.coerceAtLeast(0),
                            isRetakingCurrentPage = false,
                            documentTitle = baseTitle
                        )
                    }
                }
                onReady()
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun selectScannerPage(index: Int) {
        _scannerState.update { state ->
            if (index in state.pages.indices) state.copy(selectedPageIndex = index) else state
        }
    }

    fun setScannerFilterMode(mode: ScanFilterMode) {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _scannerState.value
            val updatedPages = if (mode == ScanFilterMode.ORIGINAL) {
                state.pages
            } else {
                state.pages.map { page ->
                    val croppedOrig = pdfEngine.cropBitmapNormalized(
                        page.originalBitmap,
                        page.cropNormalized[0],
                        page.cropNormalized[1],
                        page.cropNormalized[2],
                        page.cropNormalized[3]
                    )
                    val deskewed = pdfEngine.applyPerspectiveAndDeskew(
                        croppedOrig,
                        NormalizedQuadCorners(),
                        page.deskewAngleDegrees
                    )
                    page.copy(
                        enhancedBitmap = pdfEngine.createEnhancedDocumentBitmap(deskewed, mode)
                    )
                }
            }
            _scannerState.update {
                it.copy(
                    pages = updatedPages,
                    showEnhancedPreview = mode != ScanFilterMode.ORIGINAL,
                    activeFilterMode = mode
                )
            }
        }
    }

    fun rotateCurrentScannerPage() {
        _scannerState.update { state ->
            val idx = state.selectedPageIndex
            if (idx !in state.pages.indices) return@update state
            val updated = state.pages.toMutableList()
            val target = updated[idx]
            updated[idx] = target.copy(rotationDegrees = (target.rotationDegrees + 90) % 360)
            state.copy(pages = updated)
        }
    }

    fun cropAndDeskewCurrentScannerPage(
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float,
        deskewAngleDegrees: Float = 0f
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _scannerState.value
            val idx = state.selectedPageIndex
            if (idx !in state.pages.indices) return@launch
            val page = state.pages[idx]
            val filterToApply = if (state.activeFilterMode == ScanFilterMode.ORIGINAL) {
                ScanFilterMode.ENHANCED_COLOR
            } else {
                state.activeFilterMode
            }

            // Apply 4-corner perspective correction and deskew from the untouched original bitmap
            val quad = NormalizedQuadCorners(
                topLeft = PointF(leftRatio, topRatio),
                topRight = PointF(rightRatio, topRatio),
                bottomRight = PointF(rightRatio, bottomRatio),
                bottomLeft = PointF(leftRatio, bottomRatio)
            )
            val perspectiveCorrected = pdfEngine.applyPerspectiveAndDeskew(
                source = page.originalBitmap,
                quad = quad,
                deskewAngleDegrees = deskewAngleDegrees
            )
            val cropped = pdfEngine.cropBitmapNormalized(
                perspectiveCorrected,
                leftRatio,
                topRatio,
                rightRatio,
                bottomRatio
            )
            val enhanced = pdfEngine.createEnhancedDocumentBitmap(cropped, filterToApply)

            _scannerState.update { current ->
                if (idx !in current.pages.indices) return@update current
                val mutable = current.pages.toMutableList()
                mutable[idx] = page.copy(
                    enhancedBitmap = enhanced,
                    cropNormalized = floatArrayOf(leftRatio, topRatio, rightRatio, bottomRatio),
                    deskewAngleDegrees = deskewAngleDegrees
                )
                current.copy(pages = mutable)
            }
            showMessage("Applied 4-corner perspective & deskew while preserving untouched original capture.")
        }
    }

    fun moveScannerPage(fromIndex: Int, toIndex: Int) {
        _scannerState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices) return@update state
            val mutable = state.pages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(pages = mutable, selectedPageIndex = toIndex)
        }
    }

    fun deleteCurrentScannerPage() {
        _scannerState.update { state ->
            val idx = state.selectedPageIndex
            if (idx !in state.pages.indices) return@update state
            val mutable = state.pages.toMutableList()
            mutable.removeAt(idx)
            val nextIdx = idx.coerceAtMost((mutable.size - 1).coerceAtLeast(0))
            state.copy(pages = mutable, selectedPageIndex = nextIdx)
        }
    }

    fun updateScannerSaveConfig(
        title: String = _scannerState.value.documentTitle,
        quality: PdfQuality = _scannerState.value.selectedQuality,
        pageSize: PageSizeOption = _scannerState.value.selectedPageSize,
        orientation: PageOrientation = _scannerState.value.selectedOrientation,
        margin: MarginSize = _scannerState.value.selectedMargin
    ) {
        _scannerState.update {
            it.copy(
                documentTitle = title,
                selectedQuality = quality,
                selectedPageSize = pageSize,
                selectedOrientation = orientation,
                selectedMargin = margin
            )
        }
    }

    fun saveScanSessionToVault(onSaved: (PaperworkDocument) -> Unit) {
        val state = _scannerState.value
        if (state.pages.isEmpty() || state.isSaving) return
        viewModelScope.launch {
            _scannerState.update { it.copy(isSaving = true) }
            try {
                val doc = repo.saveScannedDocument(
                    title = state.documentTitle.ifBlank { "Scanned Document" },
                    originalBitmaps = state.pages.map { it.originalBitmap },
                    enhancedBitmaps = state.pages.map { it.enhancedBitmap },
                    useEnhancedByDefault = state.showEnhancedPreview,
                    rotations = state.pages.map { it.rotationDegrees },
                    pageSize = state.selectedPageSize,
                    orientation = state.selectedOrientation,
                    margin = state.selectedMargin,
                    quality = state.selectedQuality,
                    filterMode = state.activeFilterMode
                )
                refreshStorageMetrics()
                _scannerState.update { ScannerStudioState() }
                showMessage("Saved '${doc.title}' (${doc.pageCount} pages) with Original & ${state.activeFilterMode.label} layers.")
                onSaved(doc)
            } catch (e: Exception) {
                showMessage("Failed to save scan: ${e.message}")
                _scannerState.update { it.copy(isSaving = false) }
            }
        }
    }

    // ─── Image-to-PDF Conversion Studio (OOM-Safe + Cancellable) ─────────────

    fun appendConversionImages(uris: List<Uri>, onAdded: () -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val decoded = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        SafeBitmapDecoder.decodeUriSafely(appContext, uri, 2200)?.let { bmp ->
                            StagedScanPage(
                                originalBitmap = bmp,
                                enhancedBitmap = pdfEngine.createEnhancedDocumentBitmap(
                                    bmp,
                                    ScanFilterMode.ENHANCED_COLOR
                                )
                            )
                        }
                    }
                }
                if (decoded.isNotEmpty()) {
                    val prefs = preferencesState.value
                    _conversionState.update { current ->
                        val title = current.documentTitle.ifBlank {
                            val ts = SimpleDateFormat("MMM d, HHmm", Locale.getDefault()).format(Date())
                            "Images to PDF $ts"
                        }
                        current.copy(
                            pages = current.pages + decoded,
                            documentTitle = title,
                            selectedPageSize = prefs.defaultPageSize,
                            selectedQuality = prefs.defaultPdfQuality
                        )
                    }
                    onAdded()
                } else {
                    showMessage("Unable to decode selected image(s).")
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun rotateConversionPage(index: Int) {
        _conversionState.update { state ->
            if (index !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            val page = list[index]
            list[index] = page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
            state.copy(pages = list)
        }
    }

    fun moveConversionPage(fromIndex: Int, toIndex: Int) {
        _conversionState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(pages = list)
        }
    }

    fun removeConversionPage(index: Int) {
        _conversionState.update { state ->
            if (index !in state.pages.indices) return@update state
            val list = state.pages.toMutableList()
            list.removeAt(index)
            state.copy(pages = list)
        }
    }

    fun updateConversionConfig(
        title: String = _conversionState.value.documentTitle,
        pageSize: PageSizeOption = _conversionState.value.selectedPageSize,
        orientation: PageOrientation = _conversionState.value.selectedOrientation,
        margin: MarginSize = _conversionState.value.selectedMargin,
        quality: PdfQuality = _conversionState.value.selectedQuality
    ) {
        _conversionState.update {
            it.copy(
                documentTitle = title,
                selectedPageSize = pageSize,
                selectedOrientation = orientation,
                selectedMargin = margin,
                selectedQuality = quality
            )
        }
    }

    fun cancelConversionJob() {
        activeConversionJob?.cancel()
        activeConversionJob = null
        _conversionState.update { it.copy(isConverting = false) }
        showMessage("Cancelled PDF conversion.")
    }

    fun exportConversionToPdf(useEnhancedImages: Boolean, onCreated: (PaperworkDocument) -> Unit) {
        val state = _conversionState.value
        if (state.pages.isEmpty() || state.isConverting) return
        activeConversionJob = viewModelScope.launch {
            _conversionState.update { it.copy(isConverting = true) }
            try {
                val specs = state.pages.map { page ->
                    PageImageSpec(
                        bitmap = if (useEnhancedImages) page.enhancedBitmap else page.originalBitmap,
                        rotationDegrees = page.rotationDegrees
                    )
                }
                val doc = repo.createPdfFromImages(
                    title = state.documentTitle.ifBlank { "Converted Document" },
                    specs = specs,
                    pageSize = state.selectedPageSize,
                    orientation = state.selectedOrientation,
                    margin = state.selectedMargin,
                    quality = state.selectedQuality
                )
                refreshStorageMetrics()
                _conversionState.value = ConversionStudioState()
                showMessage("Created PDF '${doc.title}' (${doc.pageCount} pages).")
                onCreated(doc)
            } catch (_: CancellationException) {
                _conversionState.update { it.copy(isConverting = false) }
            } catch (e: Exception) {
                showMessage("PDF creation failed: ${e.message}")
                _conversionState.update { it.copy(isConverting = false) }
            }
        }
    }

    /**
     * Non-rasterizing page manipulation using Apache `pdfbox-android`
     * with post-write integrity validation via `PdfIntegrityValidator`.
     */
    fun createModifiedPdfCopyWithPdfBox(
        sourceDocument: PaperworkDocument,
        newTitle: String,
        reverseOrder: Boolean,
        pageRangeText: String,
        rotationDegrees: Int,
        onCreated: (PaperworkDocument) -> Unit
    ) {
        if (sourceDocument.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
            showMessage("Cannot modify unreadable document encrypted with invalidated key.")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val created = withContext(Dispatchers.IO) {
                    val srcSession = repo.secureDocumentAccessManager.openDecryptedSession(
                        document = sourceDocument,
                        purpose = "pdfbox_mod"
                    ).getOrThrow()
                    val outTemp = File(
                        File(appContext.cacheDir, "active_session_tmp").apply { mkdirs() },
                        "mod_out_${sourceDocument.id}_${System.nanoTime()}.pdf"
                    )
                    try {
                        val range = pdfEngine.parsePageRange(pageRangeText, sourceDocument.pageCount)
                        pdfEngine.createReorderedOrFilteredPdf(
                            sourcePdfFile = srcSession.decryptedFile,
                            targetPdfFile = outTemp,
                            reverseOrder = reverseOrder,
                            oneBasedPageNumbers = range,
                            extraRotationDegrees = rotationDegrees
                        )
                        repo.importRawPdfBytes(
                            rawPdfBytes = outTemp.readBytes(),
                            displayName = if (newTitle.endsWith(".pdf", true)) newTitle else "$newTitle.pdf",
                            documentType = sourceDocument.documentType
                        )
                    } finally {
                        srcSession.close()
                        cryptoManager.deleteFileSafely(outTemp)
                    }
                }
                refreshStorageMetrics()
                showMessage("Created validated non-rasterized PDF '${created.title}'.")
                onCreated(created)
            } catch (e: Exception) {
                showMessage("Operation failed: ${e.message}")
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun provideFactory(
            appContext: Context,
            container: PaperworkContainer
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PaperworkViewModel(appContext.applicationContext, container) as T
            }
        }
    }
}
