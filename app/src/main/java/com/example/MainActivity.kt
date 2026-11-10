package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.security.VaultKeyState
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.ThemePreference
import com.example.features.documents.DocumentsScreen
import com.example.features.home.HomeScreen
import com.example.features.pdfviewer.NativePdfViewerScreen
import com.example.features.printing.PrintConfigSheet
import com.example.features.scanner.CameraXFallbackScannerScreen
import com.example.features.scanner.ScannerStudioScreen
import com.example.features.settings.PinLockGateScreen
import com.example.features.settings.SettingsScreen
import com.example.features.tools.ToolsScreen
import com.example.ui.PaperworkViewModel
import com.example.ui.components.DeleteDocumentConfirmDialog
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.theme.PaperworkTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

enum class PrimaryDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    DOCUMENTS("documents", R.string.nav_documents, Icons.Filled.Folder, Icons.Outlined.Folder),
    TOOLS("tools", R.string.nav_tools, Icons.Filled.Build, Icons.Outlined.Build),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PaperworkApplication
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val viewModel: PaperworkViewModel = viewModel(
                factory = PaperworkViewModel.provideFactory(this, app.container)
            )
            PaperworkAppRoot(
                activity = this,
                viewModel = viewModel,
                widthSizeClass = windowSizeClass.widthSizeClass
            )
        }
    }
}

@Composable
fun PaperworkAppRoot(
    activity: Activity,
    viewModel: PaperworkViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val allDocuments by viewModel.allDocuments.collectAsStateWithLifecycle()
    val filteredDocuments by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isSessionUnlocked by viewModel.isSessionUnlocked.collectAsStateWithLifecycle()
    val vaultStorageBytes by viewModel.vaultStorageBytes.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val vaultKeyState by viewModel.cryptoManager.vaultKeyState.collectAsStateWithLifecycle()
    val keystoreSecurityLevel by viewModel.cryptoManager.detectedSecurityLevel.collectAsStateWithLifecycle()

    val isDarkTheme = when (preferences.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    PaperworkTheme(darkTheme = isDarkTheme) {
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(snackbarMessage) {
            val msg = snackbarMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearMessage()
            }
        }

        // Primary navigation & modal flow states
        var currentTab by rememberSaveable { mutableStateOf(PrimaryDestination.HOME) }
        var activeViewerDocId by rememberSaveable { mutableStateOf<String?>(null) }
        var isScannerStudioOpen by rememberSaveable { mutableStateOf(false) }
        var isCameraXFallbackOpen by rememberSaveable { mutableStateOf(false) }

        var documentToRename by remember { mutableStateOf<PaperworkDocument?>(null) }
        var documentToDelete by remember { mutableStateOf<PaperworkDocument?>(null) }
        var documentToPrint by remember { mutableStateOf<PaperworkDocument?>(null) }
        var documentPendingSafExport by remember { mutableStateOf<PaperworkDocument?>(null) }

        val activeViewerDocument = remember(allDocuments, activeViewerDocId) {
            allDocuments.find { it.id == activeViewerDocId }
        }
        val unreadableCount = remember(allDocuments) {
            allDocuments.count { it.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED }
        }
        val biometricAvailable = remember {
            viewModel.accessLockManager.canAuthenticateWithBiometricOrDeviceCredential()
        }

        // 1. Official Google ML Kit Document Scanner ActivityResultLauncher
        val mlKitScannerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pageUris = scanResult?.pages?.map { it.imageUri }.orEmpty()
                if (pageUris.isNotEmpty()) {
                    viewModel.appendScannedUris(pageUris) {
                        isScannerStudioOpen = true
                    }
                }
            } else {
                viewModel.cancelSinglePageRetake()
            }
        }

        val launchPrimaryMlKitOrFallbackScanner: () -> Unit = {
            if (!isScannerStudioOpen && scannerState.pages.isEmpty()) {
                viewModel.startNewScanSession()
            }
            try {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(if (scannerState.isRetakingCurrentPage) 1 else 30)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        try {
                            mlKitScannerLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } catch (_: Exception) {
                            isCameraXFallbackOpen = true
                        }
                    }
                    .addOnFailureListener {
                        // Automatic fallback to built-in CameraX Studio when Play Services scanner UI is unavailable
                        isCameraXFallbackOpen = true
                    }
            } catch (_: Exception) {
                isCameraXFallbackOpen = true
            }
        }

        // 2. Zero-Permission Android Photo Picker for Scanner Page Import
        val scannerPhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(20)
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.appendScannedUris(uris) {
                    isScannerStudioOpen = true
                    isCameraXFallbackOpen = false
                }
            }
        }

        // 3. Zero-Permission Android Photo Picker for Image-to-PDF Conversion Studio
        val conversionPhotoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(25)
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.appendConversionImages(uris) {
                    currentTab = PrimaryDestination.TOOLS
                }
            }
        }

        // 4. SAF PDF File Import Launcher
        val importPdfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.importPdfFromUri(uri) { importedDoc ->
                    activeViewerDocId = importedDoc.id
                }
            }
        }

        // 5. SAF PDF Export / Download Launcher
        val exportPdfSafLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf")
        ) { targetUri ->
            val doc = documentPendingSafExport
            if (targetUri != null && doc != null) {
                viewModel.exportDocumentToSafUri(doc, targetUri)
            }
            documentPendingSafExport = null
        }

        // Optional App Access Lock (4-digit PIN + Biometric / Device Credential)
        if (preferences.pinEnabled && !isSessionUnlocked) {
            PinLockGateScreen(
                failedAttempts = preferences.failedPinAttempts,
                lockoutUntilEpochMs = preferences.lockoutUntilEpochMs,
                biometricEnabled = preferences.biometricUnlockEnabled && biometricAvailable,
                onRequestBiometricUnlock = {
                    val fragActivity = activity as? FragmentActivity
                    if (fragActivity != null) {
                        viewModel.accessLockManager.promptBiometricOrDeviceCredential(
                            activity = fragActivity,
                            onSuccess = { viewModel.onBiometricUnlockSucceeded() },
                            onError = { err -> viewModel.showMessage(err) }
                        )
                    }
                },
                onUnlockAttempt = { pin, callback ->
                    viewModel.attemptPinUnlock(pin, callback)
                }
            )
            return@PaperworkTheme
        }

        // Explicit 4-State Keystore Key Invalidation Recovery Dialog
        if (vaultKeyState == VaultKeyState.INVALIDATED || vaultKeyState == VaultKeyState.RECOVERY_REQUIRED) {
            val currentVer = viewModel.cryptoManager.getCurrentKeyVersion()
            AlertDialog(
                onDismissRequest = { viewModel.cryptoManager.transitionToRecoveryRequired() },
                title = { Text("Keystore Key Invalidated (v$currentVer)") },
                text = {
                    Text(
                        "Android Keystore reported that key v$currentVer was permanently invalidated by an OS security state change. Generating a new key (v${currentVer + 1}) allows new documents to be encrypted, but CANNOT decrypt files encrypted with key v$currentVer."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.createReplacementKeyAfterInvalidation(purgeUnreadableOldDocuments = false) }
                    ) {
                        Text("Create New Key (v${currentVer + 1})")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.createReplacementKeyAfterInvalidation(purgeUnreadableOldDocuments = true) }
                    ) {
                        Text("Create Key & Purge Old Records")
                    }
                }
            )
        }

        // Full-Screen Flows (CameraX Fallback, Scanner Studio, Native PDF Viewer)
        when {
            isCameraXFallbackOpen -> {
                CameraXFallbackScannerScreen(
                    isRetakeMode = scannerState.isRetakingCurrentPage,
                    onBack = {
                        viewModel.cancelSinglePageRetake()
                        isCameraXFallbackOpen = false
                    },
                    onBitmapCaptured = { bmp ->
                        viewModel.appendCapturedScanBitmap(bmp) {
                            isCameraXFallbackOpen = false
                            isScannerStudioOpen = true
                        }
                    },
                    onImportFromGallery = {
                        scannerPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            }

            isScannerStudioOpen -> {
                ScannerStudioScreen(
                    state = scannerState,
                    onBack = { isScannerStudioOpen = false },
                    onLaunchMlKitOrCameraScan = launchPrimaryMlKitOrFallbackScanner,
                    onOpenInAppCameraFallback = { isCameraXFallbackOpen = true },
                    onRetakeCurrentPage = {
                        viewModel.prepareSinglePageRetake()
                        isCameraXFallbackOpen = true
                    },
                    onImportFromGallery = {
                        scannerPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCaptureSyntheticTestPage = {
                        isCameraXFallbackOpen = true
                    },
                    onSelectPage = { viewModel.selectScannerPage(it) },
                    onSelectFilterMode = { viewModel.setScannerFilterMode(it) },
                    onRotateCurrentPage = { viewModel.rotateCurrentScannerPage() },
                    onCropAndDeskewCurrentPage = { l, t, r, b, angle ->
                        viewModel.cropAndDeskewCurrentScannerPage(l, t, r, b, angle)
                    },
                    onMovePage = { from, to -> viewModel.moveScannerPage(from, to) },
                    onDeleteCurrentPage = { viewModel.deleteCurrentScannerPage() },
                    onUpdateSaveConfig = { title, quality, size, orient, margin ->
                        viewModel.updateScannerSaveConfig(title, quality, size, orient, margin)
                    },
                    onSaveToVault = {
                        viewModel.saveScanSessionToVault { savedDoc ->
                            isScannerStudioOpen = false
                            activeViewerDocId = savedDoc.id
                        }
                    }
                )
            }

            activeViewerDocument != null -> {
                NativePdfViewerScreen(
                    document = activeViewerDocument,
                    repository = viewModel.container.documentRepository,
                    cryptoManager = viewModel.cryptoManager,
                    pdfEngine = viewModel.pdfEngine,
                    onBack = { activeViewerDocId = null },
                    onShare = { viewModel.shareDocument(context, activeViewerDocument) },
                    onExportToSaf = {
                        documentPendingSafExport = activeViewerDocument
                        exportPdfSafLauncher.launch(activeViewerDocument.fileName)
                    },
                    onPrint = { documentToPrint = activeViewerDocument },
                    onToggleOriginalEnhanced = { useEnhanced ->
                        viewModel.toggleSavedScanEnhancement(activeViewerDocument, useEnhanced)
                    },
                    onSwitchScanFilterMode = { filterMode ->
                        viewModel.toggleSavedScanEnhancement(
                            document = activeViewerDocument,
                            useEnhanced = filterMode != com.example.domain.model.ScanFilterMode.ORIGINAL,
                            filterMode = filterMode
                        )
                    },
                    onPagePositionChanged = { pageIdx ->
                        viewModel.updateLastOpenedPage(activeViewerDocument.id, pageIdx)
                    },
                    onShowMessage = { viewModel.showMessage(it) }
                )
            }

            else -> {
                val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        if (!useNavigationRail && currentTab != PrimaryDestination.SETTINGS) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    viewModel.startNewScanSession()
                                    isScannerStudioOpen = true
                                    launchPrimaryMlKitOrFallbackScanner()
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = stringResource(R.string.action_scan_document)
                                    )
                                },
                                text = { Text(stringResource(R.string.action_scan_document)) },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("fab_scan_document")
                            )
                        }
                    },
                    bottomBar = {
                        if (!useNavigationRail) {
                            NavigationBar(
                                modifier = Modifier.testTag("bottom_navigation_bar")
                            ) {
                                PrimaryDestination.entries.forEach { dest ->
                                    val selected = currentTab == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag("nav_tab_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (useNavigationRail) {
                            NavigationRail(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .testTag("navigation_rail"),
                                header = {
                                    FloatingActionButton(
                                        onClick = {
                                            viewModel.startNewScanSession()
                                            isScannerStudioOpen = true
                                            launchPrimaryMlKitOrFallbackScanner()
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .padding(vertical = 12.dp)
                                            .testTag("rail_fab_scan_document")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DocumentScanner,
                                            contentDescription = stringResource(R.string.action_scan_document)
                                        )
                                    }
                                }
                            ) {
                                PrimaryDestination.entries.forEach { dest ->
                                    val selected = currentTab == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { currentTab = dest },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag("rail_tab_${dest.route}")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                PrimaryDestination.HOME -> {
                                    HomeScreen(
                                        recentDocuments = allDocuments,
                                        repository = viewModel.container.documentRepository,
                                        isBusy = isBusy,
                                        onScanDocument = {
                                            viewModel.startNewScanSession()
                                            isScannerStudioOpen = true
                                            launchPrimaryMlKitOrFallbackScanner()
                                        },
                                        onImportPdf = {
                                            importPdfLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onCreatePdfFromImages = {
                                            currentTab = PrimaryDestination.TOOLS
                                            conversionPhotoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onPrintQuickAction = {
                                            val firstDoc = allDocuments.firstOrNull()
                                            if (firstDoc != null) {
                                                documentToPrint = firstDoc
                                            } else {
                                                currentTab = PrimaryDestination.TOOLS
                                            }
                                        },
                                        onGenerateSampleDocuments = {
                                            viewModel.generateSampleDocuments()
                                        },
                                        onOpenSearch = {
                                            currentTab = PrimaryDestination.DOCUMENTS
                                        },
                                        onOpenSettings = {
                                            currentTab = PrimaryDestination.SETTINGS
                                        },
                                        onOpenAllDocuments = {
                                            currentTab = PrimaryDestination.DOCUMENTS
                                        },
                                        onOpenDocument = { doc -> activeViewerDocId = doc.id },
                                        onRenameDocument = { doc -> documentToRename = doc },
                                        onDuplicateDocument = { doc -> viewModel.duplicateDocument(doc) },
                                        onToggleOriginalEnhanced = { doc, useEnhanced ->
                                            viewModel.toggleSavedScanEnhancement(doc, useEnhanced)
                                        },
                                        onShareDocument = { doc -> viewModel.shareDocument(context, doc) },
                                        onPrintDocument = { doc -> documentToPrint = doc },
                                        onDeleteDocument = { doc -> documentToDelete = doc }
                                    )
                                }

                                PrimaryDestination.DOCUMENTS -> {
                                    DocumentsScreen(
                                        documents = filteredDocuments,
                                        searchQuery = searchQuery,
                                        selectedFilter = selectedFilter,
                                        selectedSort = selectedSort,
                                        sortAscending = sortAscending,
                                        isGridView = preferences.isGridView,
                                        repository = viewModel.container.documentRepository,
                                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                        onFilterChange = { viewModel.updateFilter(it) },
                                        onSortChange = { sort, asc -> viewModel.updateSort(sort, asc) },
                                        onGridViewToggle = { viewModel.setGridView(it) },
                                        onScanDocument = {
                                            viewModel.startNewScanSession()
                                            isScannerStudioOpen = true
                                            launchPrimaryMlKitOrFallbackScanner()
                                        },
                                        onImportPdf = {
                                            importPdfLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onOpenDocument = { doc -> activeViewerDocId = doc.id },
                                        onRenameDocument = { doc -> documentToRename = doc },
                                        onDuplicateDocument = { doc -> viewModel.duplicateDocument(doc) },
                                        onToggleOriginalEnhanced = { doc, useEnhanced ->
                                            viewModel.toggleSavedScanEnhancement(doc, useEnhanced)
                                        },
                                        onShareDocument = { doc -> viewModel.shareDocument(context, doc) },
                                        onPrintDocument = { doc -> documentToPrint = doc },
                                        onDeleteDocument = { doc -> documentToDelete = doc }
                                    )
                                }

                                PrimaryDestination.TOOLS -> {
                                    ToolsScreen(
                                        documents = allDocuments,
                                        conversionState = conversionState,
                                        onPickImagesForConversion = {
                                            conversionPhotoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onRotateConversionPage = { viewModel.rotateConversionPage(it) },
                                        onMoveConversionPage = { from, to -> viewModel.moveConversionPage(from, to) },
                                        onRemoveConversionPage = { viewModel.removeConversionPage(it) },
                                        onUpdateConversionConfig = { title, size, orient, margin, quality ->
                                            viewModel.updateConversionConfig(title, size, orient, margin, quality)
                                        },
                                        onExportConversionToPdf = { useEnhanced ->
                                            viewModel.exportConversionToPdf(useEnhanced) { createdDoc ->
                                                activeViewerDocId = createdDoc.id
                                            }
                                        },
                                        onCancelConversion = { viewModel.cancelConversionJob() },
                                        onLaunchScanner = {
                                            viewModel.startNewScanSession()
                                            isScannerStudioOpen = true
                                            launchPrimaryMlKitOrFallbackScanner()
                                        },
                                        onImportPdf = {
                                            importPdfLauncher.launch(arrayOf("application/pdf"))
                                        },
                                        onOpenPrintDialogForDocument = { doc ->
                                            documentToPrint = doc
                                        },
                                        onOpenDocumentInViewer = { doc ->
                                            activeViewerDocId = doc.id
                                        },
                                        onCreatePdfBoxModifiedCopy = { doc, title, rev, range, rot ->
                                            viewModel.createModifiedPdfCopyWithPdfBox(
                                                sourceDocument = doc,
                                                newTitle = title,
                                                reverseOrder = rev,
                                                pageRangeText = range,
                                                rotationDegrees = rot
                                            ) { newDoc ->
                                                activeViewerDocId = newDoc.id
                                            }
                                        }
                                    )
                                }

                                PrimaryDestination.SETTINGS -> {
                                    SettingsScreen(
                                        preferences = preferences,
                                        keystoreSecurityLevel = keystoreSecurityLevel,
                                        vaultKeyState = vaultKeyState,
                                        activeKeyVersion = viewModel.cryptoManager.getCurrentKeyVersion(),
                                        biometricAvailable = biometricAvailable,
                                        vaultStorageBytes = vaultStorageBytes,
                                        documentCount = allDocuments.size,
                                        unreadableDocumentCount = unreadableCount,
                                        onThemeChange = { viewModel.setThemePreference(it) },
                                        onDefaultQualityChange = { viewModel.setDefaultPdfQuality(it) },
                                        onDefaultPageSizeChange = { viewModel.setDefaultPageSize(it) },
                                        onDefaultPrintOrderChange = { viewModel.setDefaultPrintOrder(it) },
                                        onDefaultEnhancedScanChange = { viewModel.setDefaultEnhancedScan(it) },
                                        onBiometricUnlockChange = { viewModel.setBiometricUnlockEnabled(it) },
                                        onConfigurePin = { viewModel.setOrUpdatePin(it) },
                                        onDisablePin = { viewModel.disablePinLock() },
                                        onLockVaultNow = { viewModel.lockSessionNow() },
                                        onCreateReplacementKeyAfterInvalidation = { purgeOld ->
                                            viewModel.createReplacementKeyAfterInvalidation(purgeOld)
                                        },
                                        onPurgeTempCache = { viewModel.purgeTemporaryDecryptedCache() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shared Dialogs & Modal Bottom Sheets
        documentToRename?.let { doc ->
            RenameDocumentDialog(
                document = doc,
                onDismiss = { documentToRename = null },
                onConfirm = { newTitle ->
                    viewModel.renameDocument(doc, newTitle)
                    documentToRename = null
                }
            )
        }

        documentToDelete?.let { doc ->
            DeleteDocumentConfirmDialog(
                document = doc,
                onDismiss = { documentToDelete = null },
                onConfirmDelete = {
                    if (activeViewerDocId == doc.id) {
                        activeViewerDocId = null
                    }
                    viewModel.deleteDocument(doc)
                    documentToDelete = null
                }
            )
        }

        documentToPrint?.let { doc ->
            PrintConfigSheet(
                document = doc,
                defaultPrintOrder = preferences.defaultPrintOrder,
                defaultPageSize = preferences.defaultPageSize,
                onDismiss = { documentToPrint = null },
                onConfirmPrint = { config ->
                    documentToPrint = null
                    viewModel.printDocument(activity, doc, config)
                }
            )
        }
    }
}
