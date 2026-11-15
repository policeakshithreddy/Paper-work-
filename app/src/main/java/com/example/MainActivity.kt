package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.*
import com.example.features.documents.DocumentsScreen
import com.example.features.home.HomeScreen
import com.example.features.pdfviewer.NativePdfViewerScreen
import com.example.features.printing.launchPrint
import com.example.features.scanner.ScannerStudioScreen
import com.example.features.settings.SettingsScreen
import com.example.ui.PaperworkViewModel
import com.example.ui.components.*
import com.example.ui.theme.PaperworkTheme
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as PaperworkApplication).container
            val model: PaperworkViewModel = viewModel(factory = PaperworkViewModel.provideFactory(this, container))
            PaperworkApp(this, model, container)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun PaperworkApp(activity: Activity, model: PaperworkViewModel, container: PaperworkContainer) {
    val prefs by model.preferencesState.collectAsStateWithLifecycle()
    val documents by model.allDocuments.collectAsStateWithLifecycle()
    val filtered by model.filteredDocuments.collectAsStateWithLifecycle()
    val query by model.searchQuery.collectAsStateWithLifecycle()
    val filter by model.selectedFilter.collectAsStateWithLifecycle()
    val sort by model.selectedSort.collectAsStateWithLifecycle()
    val busy by model.isBusy.collectAsStateWithLifecycle()
    val message by model.snackbarMessage.collectAsStateWithLifecycle()
    val dark = when (prefs.themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    PaperworkTheme(darkTheme = dark, dynamicColor = prefs.useDynamicColor) {
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sourceSheet by rememberSaveable { mutableStateOf(false) }
        var studio by rememberSaveable { mutableStateOf(false) }
        var pages by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
        var scanTitle by rememberSaveable { mutableStateOf("Scan " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())) }
        var scanFilter by rememberSaveable { mutableStateOf(ScanFilterMode.ORIGINAL) }
        var scanFormat by rememberSaveable { mutableStateOf(ScanOutputFormat.PDF) }
        var scanType by rememberSaveable { mutableStateOf(DocumentType.SCANNED) }
        var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
        var renameDoc by remember { mutableStateOf<PaperworkDocument?>(null) }
        var deleteDoc by remember { mutableStateOf<PaperworkDocument?>(null) }
        var showReset by remember { mutableStateOf(model.needsLegacyReset()) }
        var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val navigator = rememberListDetailPaneScaffoldNavigator<String>()
        val activeDocument = documents.firstOrNull { it.id == navigator.currentDestination?.contentKey }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingStorageAction?.invoke() else model.showMessage("Allow storage access to save to Downloads on Android 9.")
            pendingStorageAction = null
        }
        val withStorage: (() -> Unit) -> Unit = { action ->
            if (Build.VERSION.SDK_INT == 28 && ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingStorageAction = action
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else action()
        }
        val openDocument: (PaperworkDocument) -> Unit = { doc ->
            tab = 1
            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, doc.id) }
        }
        val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
            if (uris.isNotEmpty()) { pages = ArrayList(pages + uris.map { it.toString() }); studio = true }
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) cameraUri?.let { pages = ArrayList(pages + it); studio = true }
        }
        val launchFallback: () -> Unit = {
            try {
                val folder = File(activity.cacheDir, "captures").apply { mkdirs() }
                val file = File.createTempFile("page_", ".jpg", folder)
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                cameraUri = uri.toString()
                camera.launch(uri)
            } catch (e: Exception) { model.showMessage("No camera app is available. You can import photos instead.") }
        }
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val captured = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages.orEmpty().map { it.imageUri.toString() }
                if (captured.isNotEmpty()) {
                    pages = ArrayList(pages + captured); studio = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
        val scan: () -> Unit = {
            sourceSheet = false
            scanType = DocumentType.SCANNED
            try {
                val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(100)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                    .addOnSuccessListener { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { launchFallback() }
            } catch (e: Exception) { launchFallback() }
        }
        val images: () -> Unit = {
            sourceSheet = false
            scanType = DocumentType.CONVERTED
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) withStorage { model.importPdf(uri, openDocument) }
        }
        val import: () -> Unit = { pdfPicker.launch(arrayOf("application/pdf")) }
        val documentAction: (PaperworkDocument, String) -> Unit = { doc, action ->
            when (action) {
                "Rename" -> renameDoc = doc
                "Delete" -> deleteDoc = doc
                "Duplicate" -> withStorage { model.duplicate(doc) }
                "Share" -> try { activity.startActivity(Intent.createChooser(model.createShareIntent(doc), "Share document")) }
                    catch (e: Exception) { model.showMessage("No app is available to share this document.") }
                "Print" -> scope.launch {
                    try { launchPrint(activity, model.copyToCache(doc, "print"), doc.fileName, doc.pageCount, container.pdfEngine) }
                    catch (e: Exception) { model.showMessage(e.message ?: "Unable to start printing.") }
                }
            }
        }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
        BackHandler(enabled = studio && !busy) { studio = false }
        if (showReset) {
            AlertDialog(onDismissRequest = {}, icon = { Icon(Icons.Rounded.FolderOpen, null) },
                title = { Text("Start your new workspace") },
                text = { Text("This update replaces the old encrypted vault. Continuing permanently removes its documents and app lock. New files will be saved unencrypted to Downloads. Cancel to leave the old files untouched.") },
                confirmButton = { TextButton(onClick = { model.resetLegacyVault { showReset = false } }) { Text("Delete old vault & continue") } },
                dismissButton = { TextButton(onClick = { activity.finish() }) { Text("Cancel") } })
        }
        NavigationSuiteScaffold(navigationSuiteItems = {
            if (!studio) {
                val labels = listOf("Home", "Documents", "Tools", "Settings")
                val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Folder, Icons.Rounded.AutoAwesome, Icons.Rounded.Settings)
                labels.forEachIndexed { i, label ->
                    item(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icons[i], label) }, label = { Text(label) })
                }
            }
        }) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
                if (tab != 1 || studio) TopAppBar(
                    title = { Text(if (studio) "Your pages" else listOf("Paperwork", "Documents", "Tools", "Settings")[tab]) },
                    navigationIcon = { if (studio) IconButton(onClick = { studio = false }, enabled = !busy) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = { if (tab == 0 && !studio) IconButton(onClick = { tab = 3 }) { Icon(Icons.Rounded.Palette, "Appearance settings") } })
            }, floatingActionButton = {
                if (!studio && tab == 1) ExtendedFloatingActionButton(onClick = { sourceSheet = true },
                    icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New document") })
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (studio) ScannerStudioScreen(pages, scanTitle, { scanTitle = it }, scanFilter, { scanFilter = it },
                        scanFormat, { scanFormat = it }, { sourceSheet = true },
                        { i -> pages = ArrayList(pages.filterIndexed { index, _ -> index != i }) },
                        { from, to -> pages = ArrayList(pages).apply { add(to, removeAt(from)) } },
                        {
                            withStorage {
                                model.savePages(pages.map(Uri::parse), scanTitle, scanType, scanFilter, scanFormat) { document ->
                                    studio = false; pages = arrayListOf()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    document?.let(openDocument)
                                }
                            }
                        }, busy)
                    else when (tab) {
                        0 -> Box(Modifier.widthIn(max = 1000.dp).fillMaxSize().align(androidx.compose.ui.Alignment.TopCenter)) {
                            HomeScreen(documents, { sourceSheet = true }, import, images, { tab = 1 }, openDocument, documentAction)
                        }
                        1 -> NavigableListDetailPaneScaffold(navigator = navigator,
                            listPane = { AnimatedPane {
                                Column {
                                    TopAppBar(title = { Text("Documents") })
                                    DocumentsScreen(filtered, query, filter, sort, prefs.isGridView, model::setSearchQuery,
                                        model::setFilter, model::setSort, { model.setGridView(!prefs.isGridView) },
                                        model::toggleSortDirection, openDocument, documentAction)
                                }
                            } },
                            detailPane = { AnimatedPane {
                                activeDocument?.let { doc ->
                                    NativePdfViewerScreen(doc, container.pdfEngine, { model.copyToCache(doc, "viewer") },
                                        { scope.launch { navigator.navigateBack() } }, { documentAction(doc, it) },
                                        { model.updateLastOpenedPage(doc.id, it) })
                                } ?: EmptyState("Space to focus", "Choose a document to open it here.", Icons.Rounded.Description)
                            } })
                        2 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item { Text("Good tools.\nLess busywork.", style = MaterialTheme.typography.headlineLarge) }
                            item { ActionTile("Scan documents", "Capture, crop, and clean up paper", Icons.Rounded.DocumentScanner, scan, Modifier.fillMaxWidth()) }
                            item { ActionTile("Images to PDF", "Collect your photos into one document", Icons.Rounded.AddPhotoAlternate, images, Modifier.fillMaxWidth()) }
                            item { ActionTile("Import PDF", "Read, recognize text, share, and print", Icons.Rounded.FileOpen, import, Modifier.fillMaxWidth()) }
                            item { Text("Open a document to copy text, run on-device OCR, or send it to your printer.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        3 -> Box(Modifier.widthIn(max = 720.dp).fillMaxSize().align(androidx.compose.ui.Alignment.TopCenter)) {
                            SettingsScreen(prefs, model::setTheme, model::setDynamicColor, model::setQuality, model::setPageSize)
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (sourceSheet) ModalBottomSheet(onDismissRequest = { sourceSheet = false }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bring a page to life", style = MaterialTheme.typography.headlineSmall)
                ActionTile("Camera scan", "Automatic edges and perspective correction", Icons.Rounded.DocumentScanner, scan, Modifier.fillMaxWidth())
                ActionTile("Import images", "Choose photos from your device", Icons.Rounded.AddPhotoAlternate, images, Modifier.fillMaxWidth())
                TextButton(onClick = { sourceSheet = false; import() }) { Text("Import a PDF instead") }
            }
        }
        renameDoc?.let { doc ->
            var name by remember(doc.id) { mutableStateOf(doc.title) }
            AlertDialog(onDismissRequest = { renameDoc = null }, title = { Text("Rename document") },
                text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
                confirmButton = { TextButton(onClick = { model.rename(doc, name); renameDoc = null }, enabled = name.isNotBlank()) { Text("Save") } },
                dismissButton = { TextButton(onClick = { renameDoc = null }) { Text("Cancel") } })
        }
        deleteDoc?.let { doc ->
            AlertDialog(onDismissRequest = { deleteDoc = null }, title = { Text("Delete this document?") },
                text = { Text("“${doc.title}” will be removed from Downloads. This cannot be undone.") },
                confirmButton = { TextButton(onClick = {
                    model.delete(doc); deleteDoc = null
                    scope.launch { navigator.navigateBack() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deleteDoc = null }) { Text("Keep") } })
        }
    }
}
