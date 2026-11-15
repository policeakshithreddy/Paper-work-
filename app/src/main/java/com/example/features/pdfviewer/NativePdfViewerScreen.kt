package com.example.features.pdfviewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.core.pdf.PdfProcessingEngine
import com.example.domain.model.PaperworkDocument
import com.example.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativePdfViewerScreen(document: PaperworkDocument, engine: PdfProcessingEngine,
    copyFile: suspend () -> File, onBack: () -> Unit, onAction: (String) -> Unit,
    onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    var page by remember(document.id) { mutableIntStateOf(document.lastOpenedPage) }
    var file by remember(document.id) { mutableStateOf<File?>(null) }
    var error by remember(document.id) { mutableStateOf<String?>(null) }
    var bitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }
    var showText by remember(document.id) { mutableStateOf(false) }
    var text by remember(document.id) { mutableStateOf(document.nativePdfText) }
    var ocrBusy by remember { mutableStateOf(false) }
    var zoom by remember(document.id, page) { mutableFloatStateOf(1f) }
    var offset by remember(document.id, page) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    DisposableEffect(document.id) { onDispose { file?.delete() } }
    LaunchedEffect(document.id) {
        try { file = copyFile() } catch (e: Exception) { error = e.message ?: "Unable to open this file." }
    }
    LaunchedEffect(file, page) {
        bitmap = null
        file?.let { source ->
            try {
                bitmap = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                                val width = 1400
                                Bitmap.createBitmap(width, (width * pdfPage.height.toFloat() / pdfPage.width).toInt().coerceIn(1, 2400),
                                    Bitmap.Config.ARGB_8888).also { image ->
                                    image.eraseColor(android.graphics.Color.WHITE)
                                    pdfPage.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { error = "This PDF cannot be opened. It may be password-protected, damaged, or removed." }
        }
    }
    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text(document.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close document") } },
            actions = {
                IconButton(onClick = { showText = true }) { Icon(Icons.Rounded.TextSnippet, "Read or recognize text") }
                IconButton(onClick = { onAction("Share") }) { Icon(Icons.Rounded.Share, "Share document") }
                IconButton(onClick = { onAction("Print") }) { Icon(Icons.Rounded.Print, "Print document") }
            })
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
            .pointerInput(document.id, page) { detectTransformGestures { _, pan, scale, _ ->
                zoom = (zoom * scale).coerceIn(1f, 4f)
                offset = if (zoom == 1f) Offset.Zero else offset + pan
            } }, contentAlignment = Alignment.Center) {
            when {
                error != null -> EmptyState("Unable to open document", error!!)
                bitmap != null -> Image(bitmap!!.asImageBitmap(), "Page ${page + 1}",
                    Modifier.fillMaxSize().padding(12.dp).graphicsLayer {
                        scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
                    })
                else -> CircularProgressIndicator()
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page--; onPage(page) }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, "Previous page") }
                Text("${page + 1} / ${document.pageCount}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { zoom = 1f; offset = Offset.Zero }) { Icon(Icons.Rounded.FitScreen, "Reset zoom") }
                IconButton(onClick = { page++; onPage(page) }, enabled = page < document.pageCount - 1) { Icon(Icons.Rounded.ChevronRight, "Next page") }
            }
        }
    }
    if (showText) ModalBottomSheet(onDismissRequest = { showText = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Document text", style = MaterialTheme.typography.headlineSmall)
            SelectionContainer { Text(text.ifBlank { "No embedded text. Recognize the current page to copy its text." }) }
            Button(onClick = {
                val image = bitmap ?: return@Button
                scope.launch {
                    ocrBusy = true
                    try { text = engine.recognizeTextWithMlKit(image).ifBlank { "No text found on this page." } }
                    catch (e: Exception) { text = "Text recognition is unavailable. Try again when the language model has downloaded." }
                    finally { ocrBusy = false }
                }
            }, enabled = bitmap != null && !ocrBusy) { Text(if (ocrBusy) "Recognizing…" else "Recognize page ${page + 1}") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
