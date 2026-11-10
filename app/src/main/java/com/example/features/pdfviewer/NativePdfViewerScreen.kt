package com.example.features.pdfviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material3.FilterChip
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
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.compose.PdfViewer
import androidx.pdf.compose.PdfViewerState
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.security.VaultCryptoManager
import com.example.data.repository.DocumentRepository
import com.example.domain.model.PaperworkDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Checks whether the current Android runtime supports the official Jetpack `androidx.pdf`
 * sandboxed PDF service (Android 15+ natively, or Android 12+ with SdkExtensions R >= 13).
 */
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
    onPagePositionChanged: (Int) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var decryptedTempFile by remember(document.id, document.modifiedAt) { mutableStateOf<File?>(null) }
    var isLoadingFile by remember(document.id, document.modifiedAt) { mutableStateOf(true) }
    var loadError by remember(document.id, document.modifiedAt) { mutableStateOf<String?>(null) }

    // Official Jetpack PDF Viewer vs. Compatibility Fallback toggle
    val jetpackPdfSupported = remember { PdfViewerCapabilityChecker.isJetpackPdfSupported() }
    var preferJetpackComposeViewer by remember { mutableStateOf(jetpackPdfSupported) }

    // Search & Selectable Text / OCR Inspection state
    var isSearchBarVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTextAndOcrSheet by remember { mutableStateOf(false) }
    var isRunningOnDemandOcr by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Page-indexed text map (0-based pageIndex -> text)
    val pageTextMap = remember(document.id, document.modifiedAt) { mutableStateMapOf<Int, String>() }
    var isOcrDerivedText by remember(document.id, document.modifiedAt) {
        mutableStateOf(!document.hasNativeTextLayer)
    }

    // Current page & zoom state for bottom pill and fallback viewer
    var currentPageIndex by remember(document.id) {
        mutableIntStateOf(document.lastOpenedPage.coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)))
    }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Decrypt PDF to isolated session cache on entry, and securely wipe on exit
    DisposableEffect(document.id, document.modifiedAt) {
        onDispose {
            decryptedTempFile?.let { cryptoManager.secureDeleteFile(it) }
        }
    }

    LaunchedEffect(document.id, document.modifiedAt) {
        isLoadingFile = true
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val encFile = File(document.encryptedPdfPath)
                if (!encFile.exists()) {
                    loadError = "Encrypted PDF file not found in vault."
                    isLoadingFile = false
                    return@withContext
                }
                val temp = cryptoManager.materializeDecryptedTempFile(
                    sourceEncryptedFile = encFile,
                    tempFileName = "viewer_${document.id}_${document.modifiedAt}.pdf",
                    aad = document.id
                )
                decryptedTempFile = temp

                // Populate native text layer per page via PDFBox if available
                val extractedByPage = pdfEngine.extractNativeTextByPage(temp)
                if (extractedByPage.isNotEmpty()) {
                    pageTextMap.clear()
                    pageTextMap.putAll(extractedByPage)
                    isOcrDerivedText = false
                } else if (document.extractedOcrText.isNotBlank()) {
                    pageTextMap.clear()
                    pageTextMap[0] = document.extractedOcrText
                    isOcrDerivedText = true
                }
                isLoadingFile = false
            } catch (e: Exception) {
                loadError = e.message ?: "Failed to decrypt PDF for viewing."
                isLoadingFile = false
            }
        }
    }

    val matchingPages by remember(searchQuery, pageTextMap.size) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                pageTextMap.entries
                    .filter { it.value.contains(searchQuery, ignoreCase = true) }
                    .map { it.key }
                    .sorted()
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
                                        "Native PDF Text Layer"
                                    } else {
                                        "Scanned PDF • ML Kit OCR Ready"
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
                            Icon(Icons.Default.TextSnippet, contentDescription = "Select text & OCR")
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
                                    text = { Text("Select & copy text / OCR") },
                                    leadingIcon = { Icon(Icons.Default.TextSnippet, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showTextAndOcrSheet = true
                                    }
                                )
                                if (document.scanPages.isNotEmpty()) {
                                    val nextEnhanced = !document.currentlyUsingEnhanced
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (nextEnhanced) "Switch to Enhanced scan"
                                                else "Switch to Original scan"
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleOriginalEnhanced(nextEnhanced)
                                        }
                                    )
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
                                                if (preferJetpackComposeViewer) "Use Page-by-Page Renderer"
                                                else "Use Jetpack PdfViewer Engine"
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            preferJetpackComposeViewer = !preferJetpackComposeViewer
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                // Reversible Original / Enhanced Banner for Scanned Documents
                if (document.scanPages.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan layer:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.width(220.dp)
                            ) {
                                SegmentedButton(
                                    selected = !document.currentlyUsingEnhanced,
                                    onClick = {
                                        if (document.currentlyUsingEnhanced) {
                                            onToggleOriginalEnhanced(false)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) {
                                    Text("Original")
                                }
                                SegmentedButton(
                                    selected = document.currentlyUsingEnhanced,
                                    onClick = {
                                        if (!document.currentlyUsingEnhanced) {
                                            onToggleOriginalEnhanced(true)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) {
                                    Text("Enhanced")
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
            when {
                isLoadingFile -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Decrypting PDF stream via Android Keystore...",
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

                decryptedTempFile != null -> {
                    val pdfFile = decryptedTempFile!!

                    // Primary Engine: Official Jetpack `androidx.pdf:pdf-compose` (`PdfViewer` + `SandboxedPdfLoader`)
                    var jetpackFailed by remember(pdfFile.absolutePath) { mutableStateOf(false) }

                    if (preferJetpackComposeViewer && !jetpackFailed) {
                        JetpackOfficialComposePdfSurface(
                            pdfFile = pdfFile,
                            onFallbackRequired = { jetpackFailed = true }
                        )
                    } else {
                        // High-DPI Lazy Page-by-Page `PdfRenderer` Surface with Pinch-to-Zoom & Double-Tap Zoom
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
                                    pdfFile = pdfFile,
                                    pageIndex = pageIdx,
                                    totalPages = document.pageCount,
                                    pdfEngine = pdfEngine,
                                    isSearchMatch = matchingPages.contains(pageIdx),
                                    searchQuery = searchQuery,
                                    pageText = pageTextMap[pageIdx],
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

    // Native Text Selection & On-Device ML Kit OCR Sheet
    if (showTextAndOcrSheet && decryptedTempFile != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val currentText = pageTextMap[currentPageIndex] ?: document.extractedOcrText

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
                            text = "Selectable Text & OCR Layer",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOcrDerivedText) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (isOcrDerivedText) {
                                    "Source: On-Device ML Kit OCR (Latin script)"
                                } else {
                                    "Source: Original PDF Vector Text Layer"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOcrDerivedText) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("PDF Text", currentText))
                            onShowMessage("Copied text to clipboard.")
                        },
                        enabled = currentText.isNotBlank()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy All")
                    }
                }

                // Run or Refresh On-Device ML Kit OCR for Current Page
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
                            val file = decryptedTempFile ?: return@Button
                            scope.launch {
                                isRunningOnDemandOcr = true
                                val pageBmp = pdfEngine.renderPdfPageToBitmap(file, currentPageIndex, 1440)
                                if (pageBmp != null) {
                                    val recognized = pdfEngine.recognizeTextWithMlKit(pageBmp)
                                    if (recognized.isNotBlank()) {
                                        pageTextMap[currentPageIndex] = recognized
                                        isOcrDerivedText = true
                                        repository.updateExtractedOcrText(document, recognized)
                                        onShowMessage("OCR completed for Page ${currentPageIndex + 1}.")
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
                        if (currentText.isBlank()) {
                            Text(
                                text = "No native text layer on this page. Tap 'Run ML Kit OCR' above to recognize text from this scanned page.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SelectionContainer {
                                LazyColumn {
                                    item {
                                        HighlightedSearchText(
                                            fullText = currentText,
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
    pdfFile: File,
    onFallbackRequired: () -> Unit
) {
    val context = LocalContext.current
    val pdfLoader = remember(context) {
        runCatching { SandboxedPdfLoader(context) }.getOrNull()
    }
    val pdfViewerState = remember { PdfViewerState() }
    var pdfDocument by remember(pdfFile.absolutePath) { mutableStateOf<PdfDocument?>(null) }

    LaunchedEffect(pdfFile.absolutePath) {
        if (pdfLoader == null) {
            onFallbackRequired()
            return@LaunchedEffect
        }
        try {
            val uri = Uri.fromFile(pdfFile)
            pdfDocument = pdfLoader.openDocument(uri)
        } catch (_: Throwable) {
            onFallbackRequired()
        }
    }

    if (pdfDocument != null) {
        PdfViewer(
            pdfDocument = pdfDocument,
            state = pdfViewerState,
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
    pdfFile: File,
    pageIndex: Int,
    totalPages: Int,
    pdfEngine: PdfProcessingEngine,
    isSearchMatch: Boolean,
    searchQuery: String,
    pageText: String?,
    onOpenTextSelect: () -> Unit
) {
    var pageBitmap by remember(pdfFile.absolutePath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pdfFile.absolutePath, pageIndex) {
        pageBitmap = pdfEngine.renderPdfPageToBitmap(
            pdfFile = pdfFile,
            pageIndex = pageIndex,
            targetWidthPx = 1280
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // White paper sheet container with natural proportions and subtle elevation
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
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp),
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

        // Page footer metadata & quick text selection chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Page ${pageIndex + 1} of $totalPages",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!pageText.isNullOrBlank()) {
                FilterChip(
                    selected = isSearchMatch,
                    onClick = onOpenTextSelect,
                    label = { Text("Select / Copy Page Text") }
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

    val highlightBg = MaterialTheme.colorScheme.tertiaryContainer
    val highlightFg = MaterialTheme.colorScheme.onTertiaryContainer
    val annotated = remember(fullText, query, highlightBg, highlightFg) {
        buildAnnotatedString {
            var startIndex = 0
            val lowerFull = fullText.lowercase()
            val lowerQuery = query.lowercase()
            while (startIndex < fullText.length) {
                val matchIdx = lowerFull.indexOf(lowerQuery, startIndex)
                if (matchIdx == -1) {
                    append(fullText.substring(startIndex))
                    break
                }
                if (matchIdx > startIndex) {
                    append(fullText.substring(startIndex, matchIdx))
                }
                withStyle(
                    SpanStyle(
                        background = highlightBg,
                        color = highlightFg,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(fullText.substring(matchIdx, matchIdx + query.length))
                }
                startIndex = matchIdx + query.length
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
