package com.example.features.pdfviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.example.core.pdf.DecryptedPdfSessionHandle
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.security.VaultCryptoManager
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.ScanFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runtime capability checker and engine selector for the PDF viewer.
 *
 * Official `androidx.pdf:1.0.0-beta01` (`SandboxedPdfLoader` + `PdfDocumentService`)
 * requires Android 15 (API 35+) or Android 12–14 with `SdkExtensions.getExtensionVersion(R) >= 13`.
 * On API 28–30 or devices without the required SDK extension, Paperwork automatically routes
 * to [PdfViewerEngine.FRAMEWORK_PDF_RENDERER].
 */
enum class PdfViewerEngine(val label: String) {
    JETPACK_SANDBOXED_PDF("Official AndroidX PdfViewer (Sandboxed)"),
    FRAMEWORK_PDF_RENDERER("Android PdfRenderer Compatibility Engine")
}

object PdfViewerCapabilityChecker {
    fun isJetpackPdfSupported(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 35) {
                true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 13
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun selectDefaultEngine(): PdfViewerEngine {
        return if (isJetpackPdfSupported()) {
            PdfViewerEngine.JETPACK_SANDBOXED_PDF
        } else {
            PdfViewerEngine.FRAMEWORK_PDF_RENDERER
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPdfApi::class)
@Composable
fun NativePdfViewerScreen(
    document: PaperworkDocument,
    repository: DocumentRepository,
    cryptoManager: VaultCryptoManager,
    pdfEngine: PdfProcessingEngine,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onExportToSaf: () -> Unit,
    onPrint: () -> Unit,
    onToggleOriginalEnhanced: (Boolean) -> Unit,
    onSwitchScanFilterMode: (ScanFilterMode) -> Unit = { mode ->
        onToggleOriginalEnhanced(mode != ScanFilterMode.ORIGINAL)
    },
    onPagePositionChanged: (Int) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Controlled Decryption Session Handle:
    // Encrypted Vault File -> Controlled Decryption -> DecryptedPdfSessionHandle -> Viewer Engine -> Deterministic Cleanup
    var activeSessionHandle by remember(document.id, document.modifiedAt) {
        mutableStateOf<DecryptedPdfSessionHandle?>(null)
    }
    var isLoadingFile by remember(document.id, document.modifiedAt) { mutableStateOf(true) }
    var loadError by remember(document.id, document.modifiedAt) { mutableStateOf<String?>(null) }

    val jetpackPdfSupported = remember { PdfViewerCapabilityChecker.isJetpackPdfSupported() }
    var activeEngine by remember { mutableStateOf(PdfViewerCapabilityChecker.selectDefaultEngine()) }

    // Search & Selectable Text / OCR Inspection state
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTextAndOcrSheet by remember { mutableStateOf(false) }
    var isRunningOnDemandOcr by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Strictly separate Embedded PDF Text Layer vs. ML Kit OCR Text maps (0-based pageIndex -> text)
    val embeddedPdfTextByPage = remember(document.id, document.modifiedAt) { mutableStateMapOf<Int, String>() }
    val ocrRecognizedTextByPage = remember(document.id, document.modifiedAt) { mutableStateMapOf<Int, String>() }
    var inspectOcrTabInSheet by remember(document.id, document.modifiedAt) {
        mutableStateOf(!document.hasNativeTextLayer)
    }

    var currentPageIndex by remember(document.id) {
        mutableIntStateOf(document.lastOpenedPage.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)))
    }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Deterministic cleanup of DecryptedPdfSessionHandle on viewer exit or document reload
    DisposableEffect(document.id, document.modifiedAt) {
        onDispose {
            activeSessionHandle?.close()
            activeSessionHandle = null
        }
    }

    LaunchedEffect(document.id, document.modifiedAt) {
        isLoadingFile = true
        loadError = null
        if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
            loadError = "This document was encrypted with an older Android Keystore key (v${document.keyVersion}) that was permanently invalidated by the OS."
            isLoadingFile = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val sessionResult = repository.secureDocumentAccessManager.openDecryptedSession(
                document = document,
                purpose = "viewer"
            )
            sessionResult.fold(
                onSuccess = { handle ->
                    activeSessionHandle = handle
                    // Extract embedded PDF text stream per page via NativePdfTextExtractor (never OCR)
                    val embeddedMap = pdfEngine.nativeTextExtractor.extractEmbeddedTextByPage(handle.decryptedFile)
                    embeddedPdfTextByPage.clear()
                    embeddedPdfTextByPage.putAll(embeddedMap)

                    ocrRecognizedTextByPage.clear()
                    if (document.ocrRecognizedText.isNotBlank()) {
                        ocrRecognizedTextByPage[0] = document.ocrRecognizedText
                    }
                    inspectOcrTabInSheet = embeddedMap.isEmpty()
                    isLoadingFile = false
                },
                onFailure = { err ->
                    loadError = err.message ?: "Failed to decrypt PDF for viewing."
                    isLoadingFile = false
                }
            )
        }
    }

    val matchingPages by remember(searchQuery, embeddedPdfTextByPage.size, ocrRecognizedTextByPage.size) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                val pages = mutableSetOf<Int>()
                embeddedPdfTextByPage.forEach { (idx, txt) ->
                    if (txt.contains(searchQuery, ignoreCase = true)) pages.add(idx)
                }
                ocrRecognizedTextByPage.forEach { (idx, txt) ->
                    if (txt.contains(searchQuery, ignoreCase = true)) pages.add(idx)
                }
                pages.sorted()
            }
        }
    }

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPageIndex.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
    )

    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        val visibleIdx = lazyListState.firstVisibleItemIndex
        if (visibleIdx != currentPageIndex && visibleIdx in 0 until document.pageCount) {
            currentPageIndex = visibleIdx
            onPagePositionChanged(visibleIdx)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_viewer_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = document.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (document.hasNativeTextLayer) {
                                        "Embedded PDF Text Layer • Key v${document.keyVersion}"
                                    } else {
                                        "Scanned PDF • On-Device OCR Ready • Key v${document.keyVersion}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchBarVisible = !isSearchBarVisible },
                            modifier = Modifier.testTag("viewer_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search within PDF")
                        }
                        IconButton(
                            onClick = { showTextAndOcrSheet = true },
                            modifier = Modifier.testTag("viewer_text_ocr_button")
                        ) {
                            Icon(Icons.Default.TextSnippet, contentDescription = "Embedded text & OCR")
                        }
                        IconButton(
                            onClick = onPrint,
                            modifier = Modifier.testTag("viewer_print_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print document")
                        }

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("viewer_more_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Embedded Text & ML Kit OCR") },
                                    leadingIcon = { Icon(Icons.Default.TextSnippet, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showTextAndOcrSheet = true
                                    }
                                )
                                if (document.scanPages.isNotEmpty()) {
                                    ScanFilterMode.entries.forEach { mode ->
                                        if (mode != document.activeScanFilterMode) {
                                            DropdownMenuItem(
                                                text = { Text("Switch to ${mode.label}") },
                                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                                onClick = {
                                                    menuExpanded = false
                                                    onSwitchScanFilterMode(mode)
                                                }
                                            )
                                        }
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Share PDF") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onShare()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download / Export PDF") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onExportToSaf()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Print / Reverse print") },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onPrint()
                                    }
                                )
                                if (jetpackPdfSupported) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (activeEngine == PdfViewerEngine.JETPACK_SANDBOXED_PDF) {
                                                    "Switch to PdfRenderer Engine"
                                                } else {
                                                    "Switch to Official AndroidX PdfViewer"
                                                }
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            activeEngine = if (activeEngine == PdfViewerEngine.JETPACK_SANDBOXED_PDF) {
                                                PdfViewerEngine.FRAMEWORK_PDF_RENDERER
                                            } else {
                                                PdfViewerEngine.JETPACK_SANDBOXED_PDF
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                // Reversible Original / Enhanced Color / Grayscale Banner for Scanned Documents
                if (document.scanPages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan filter:",
                                style = MaterialTheme.typography.labelMedium
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.width(280.dp)
                            ) {
                                ScanFilterMode.entries.forEachIndexed { index, mode ->
                                    val shortLabel = when (mode) {
                                        ScanFilterMode.ORIGINAL -> "Original"
                                        ScanFilterMode.ENHANCED_COLOR -> "Color+"
                                        ScanFilterMode.INK_SAVING_GRAYSCALE -> "B&W"
                                    }
                                    SegmentedButton(
                                        selected = document.activeScanFilterMode == mode,
                                        onClick = {
                                            if (document.activeScanFilterMode != mode) {
                                                onSwitchScanFilterMode(mode)
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = ScanFilterMode.entries.size
                                        )
                                    ) {
                                        Text(shortLabel, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Inline Search Bar within PDF
                if (isSearchBarVisible) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Find keyword in PDF...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("viewer_search_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (searchQuery.isNotBlank()) {
                                Text(
                                    text = "${matchingPages.size} ${if (matchingPages.size == 1) "page" else "pages"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = {
                                        val prev = matchingPages.lastOrNull { it < currentPageIndex }
                                            ?: matchingPages.lastOrNull()
                                        if (prev != null) {
                                            currentPageIndex = prev
                                            scope.launch { lazyListState.animateScrollToItem(prev) }
                                        }
                                    },
                                    enabled = matchingPages.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                }
                                IconButton(
                                    onClick = {
                                        val next = matchingPages.firstOrNull { it > currentPageIndex }
                                            ?: matchingPages.firstOrNull()
                                        if (next != null) {
                                            currentPageIndex = next
                                            scope.launch { lazyListState.animateScrollToItem(next) }
                                        }
                                    },
                                    enabled = matchingPages.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                }
                            }
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
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            val session = activeSessionHandle
            when {
                isLoadingFile -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Decrypting PDF session via Android Keystore...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                loadError != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = loadError ?: "Unable to open PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onBack) {
                            Text("Return to Documents")
                        }
                    }
                }

                session != null -> {
                    var jetpackFailed by remember(session.sessionId) { mutableStateOf(false) }

                    if (activeEngine == PdfViewerEngine.JETPACK_SANDBOXED_PDF && !jetpackFailed) {
                        JetpackOfficialComposePdfSurface(
                            sessionHandle = session,
                            onFallbackRequired = {
                                jetpackFailed = true
                                activeEngine = PdfViewerEngine.FRAMEWORK_PDF_RENDERER
                            }
                        )
                    } else {
                        // Tested Compatibility Fallback: Framework PdfRenderer Page-by-Page Surface
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                        zoomScale = newScale
                                        panOffset = if (newScale > 1.01f) {
                                            Offset(
                                                x = (panOffset.x + pan.x).coerceIn(-600f, 600f),
                                                y = (panOffset.y + pan.y).coerceIn(-800f, 800f)
                                            )
                                        } else {
                                            Offset.Zero
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoomScale > 1.2f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                zoomScale = 2.0f
                                            }
                                        }
                                    )
                                }
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                        ) {
                            items(document.pageCount) { pageIdx ->
                                PdfRenderedPageCard(
                                    sessionHandle = session,
                                    pageIndex = pageIdx,
                                    totalPages = document.pageCount,
                                    pdfEngine = pdfEngine,
                                    isSearchMatch = matchingPages.contains(pageIdx),
                                    searchQuery = searchQuery,
                                    embeddedText = embeddedPdfTextByPage[pageIdx],
                                    ocrText = ocrRecognizedTextByPage[pageIdx],
                                    onOpenTextSelect = {
                                        currentPageIndex = pageIdx
                                        showTextAndOcrSheet = true
                                    }
                                )
                            }
                        }
                    }

                    // Bottom Floating Page Indicator & Zoom Controls Pill
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .testTag("viewer_bottom_controls_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Page ${currentPageIndex + 1} / ${document.pageCount}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                                    if (zoomScale == 1f) panOffset = Offset.Zero
                                },
                                enabled = zoomScale > 1f,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom out",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )

                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale + 0.25f).coerceAtMost(3.5f)
                                },
                                enabled = zoomScale < 3.5f,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom in",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Strictly Separated Embedded PDF Text vs. On-Device ML Kit OCR Sheet
    val sessionForSheet = activeSessionHandle
    if (showTextAndOcrSheet && sessionForSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val embeddedPageText = embeddedPdfTextByPage[currentPageIndex] ?: document.nativePdfText
        val ocrPageText = ocrRecognizedTextByPage[currentPageIndex] ?: document.ocrRecognizedText
        val displayedText = if (inspectOcrTabInSheet) ocrPageText else embeddedPageText

        ModalBottomSheet(
            onDismissRequest = { showTextAndOcrSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Embedded PDF Text & On-Device OCR",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Strictly separated native PDF text extraction vs. optical character recognition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("PDF Text", displayedText))
                            onShowMessage("Copied text to clipboard.")
                        },
                        enabled = displayedText.isNotBlank()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }

                // Segmented Selector: Embedded PDF Text Stream vs. On-Device ML Kit OCR
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !inspectOcrTabInSheet,
                        onClick = { inspectOcrTabInSheet = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Embedded PDF Text")
                    }
                    SegmentedButton(
                        selected = inspectOcrTabInSheet,
                        onClick = { inspectOcrTabInSheet = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("ML Kit OCR Text")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Page ${currentPageIndex + 1} of ${document.pageCount}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isRunningOnDemandOcr = true
                                val pageBmp = pdfEngine.renderPdfPageToBitmap(
                                    pdfFile = sessionForSheet.decryptedFile,
                                    pageIndex = currentPageIndex,
                                    targetWidthPx = 1440
                                )
                                if (pageBmp != null) {
                                    val recognized = pdfEngine.ocrService.recognizeBitmapText(pageBmp)
                                    if (recognized.isNotBlank()) {
                                        ocrRecognizedTextByPage[currentPageIndex] = recognized
                                        inspectOcrTabInSheet = true
                                        repository.updateExtractedOcrText(document, recognized)
                                        onShowMessage("ML Kit OCR completed for Page ${currentPageIndex + 1}.")
                                    } else {
                                        onShowMessage("No recognizable Latin script text detected on Page ${currentPageIndex + 1}.")
                                    }
                                }
                                isRunningOnDemandOcr = false
                            }
                        },
                        enabled = !isRunningOnDemandOcr
                    ) {
                        if (isRunningOnDemandOcr) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running OCR...")
                        } else {
                            Text("Run ML Kit OCR on Page ${currentPageIndex + 1}")
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (displayedText.isBlank()) {
                            Text(
                                text = if (inspectOcrTabInSheet) {
                                    "No ML Kit OCR text cached for this page yet. Tap 'Run ML Kit OCR' above to recognize text from the rendered page bitmap."
                                } else {
                                    "This PDF page does not contain an embedded vector text stream. Switch to the 'ML Kit OCR Text' tab or tap 'Run ML Kit OCR' above."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SelectionContainer {
                                LazyColumn {
                                    item {
                                        HighlightedSearchText(
                                            fullText = displayedText,
                                            query = searchQuery
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPdfApi::class)
@Composable
private fun JetpackOfficialComposePdfSurface(
    sessionHandle: DecryptedPdfSessionHandle,
    onFallbackRequired: () -> Unit
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    val pdfLoader = remember(context) {
        runCatching { SandboxedPdfLoader(context) }.getOrNull()
    }
    var pdfDocument by remember(sessionHandle.sessionId) { mutableStateOf<PdfDocument?>(null) }
    val containerId = remember(sessionHandle.sessionId) { View.generateViewId() }

    LaunchedEffect(sessionHandle.sessionId) {
        if (pdfLoader == null || fragmentActivity == null || sessionHandle.isClosed()) {
            onFallbackRequired()
            return@LaunchedEffect
        }
        try {
            pdfDocument = pdfLoader.openDocument(sessionHandle.fileUri)
        } catch (_: Throwable) {
            onFallbackRequired()
        }
    }

    if (pdfDocument != null && fragmentActivity != null) {
        AndroidView(
            factory = { ctx ->
                FragmentContainerView(ctx).apply {
                    id = containerId
                    post {
                        runCatching {
                            val fm = fragmentActivity.supportFragmentManager
                            if (!fm.isStateSaved) {
                                val fragment = PdfViewerFragment().apply {
                                    documentUri = sessionHandle.fileUri
                                }
                                fm.beginTransaction()
                                    .replace(containerId, fragment, "pdf_viewer_${sessionHandle.sessionId}")
                                    .commitAllowingStateLoss()
                            }
                        }.onFailure {
                            onFallbackRequired()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("jetpack_compose_pdf_viewer")
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun PdfRenderedPageCard(
    sessionHandle: DecryptedPdfSessionHandle,
    pageIndex: Int,
    totalPages: Int,
    pdfEngine: PdfProcessingEngine,
    isSearchMatch: Boolean,
    searchQuery: String,
    embeddedText: String?,
    ocrText: String?,
    onOpenTextSelect: () -> Unit
) {
    var pageBitmap by remember(sessionHandle.sessionId, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sessionHandle.sessionId, pageIndex) {
        if (!sessionHandle.isClosed()) {
            pageBitmap = pdfEngine.renderPdfPageToBitmap(
                pdfFile = sessionHandle.decryptedFile,
                pageIndex = pageIndex,
                targetWidthPx = 1280
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bmp = pageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1} of $totalPages",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (isSearchMatch && searchQuery.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Match for \"$searchQuery\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val sourceLabel = when {
                !embeddedText.isNullOrBlank() && !ocrText.isNullOrBlank() -> "Embedded PDF Text + OCR"
                !embeddedText.isNullOrBlank() -> "Embedded PDF Text"
                !ocrText.isNullOrBlank() -> "ML Kit OCR Text"
                else -> "Page ${pageIndex + 1}"
            }
            Text(
                text = "Page ${pageIndex + 1} of $totalPages • $sourceLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenTextSelect) {
                Icon(
                    imageVector = Icons.Default.TextSnippet,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (!embeddedText.isNullOrBlank() || !ocrText.isNullOrBlank()) {
                        "Select / Copy Text"
                    } else {
                        "Run OCR"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun HighlightedSearchText(
    fullText: String,
    query: String
) {
    if (query.isBlank()) {
        Text(
            text = fullText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        return
    }

    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer

    val annotated = remember(fullText, query, highlightColor, highlightTextColor) {
        buildAnnotatedString {
            var startIndex = 0
            val lowerText = fullText.lowercase()
            val lowerQuery = query.lowercase()
            while (startIndex < fullText.length) {
                val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
                if (matchIndex == -1) {
                    append(fullText.substring(startIndex))
                    break
                }
                if (matchIndex > startIndex) {
                    append(fullText.substring(startIndex, matchIndex))
                }
                withStyle(
                    SpanStyle(
                        background = highlightColor,
                        color = highlightTextColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(fullText.substring(matchIndex, matchIndex + query.length))
                }
                startIndex = matchIndex + query.length
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
