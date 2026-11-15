package com.example.features.scanner

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.core.image.SafeBitmapDecoder
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScanOutputFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScannerStudioScreen(pages: List<String>, title: String, onTitle: (String) -> Unit,
    filter: ScanFilterMode, onFilter: (ScanFilterMode) -> Unit,
    format: ScanOutputFormat, onFormat: (ScanOutputFormat) -> Unit,
    onAdd: () -> Unit, onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit,
    onSave: () -> Unit, busy: Boolean) {
    var selected by remember { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val context = LocalContext.current
    val preview by produceState<Bitmap?>(null, pages.getOrNull(index), filter) {
        value = null
        value = withContext(Dispatchers.Default) {
            pages.getOrNull(index)?.let { uri ->
                SafeBitmapDecoder.decodeUriSafely(context, Uri.parse(uri), 1200)?.let {
                    SafeBitmapDecoder.applyScanFilter(it, filter)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(title, onTitle, label = { Text("Document name") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Card(Modifier.fillMaxWidth().height(320.dp)) {
            preview?.let { Image(it.asImageBitmap(), "Selected page preview", Modifier.fillMaxSize().padding(12.dp)) }
                ?: Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(if (pages.isEmpty()) "Add your first page" else "Preparing preview…")
                }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pages) { i, _ ->
                FilterChip(selected = index == i, onClick = { selected = i }, label = { Text("Page ${i + 1}") })
            }
            item { AssistChip(onClick = onAdd, label = { Text("Add pages") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }) }
        }
        if (pages.isNotEmpty()) Row {
            IconButton(onClick = { onMove(index, index - 1); selected = index - 1 }, enabled = index > 0 && !busy) {
                Icon(Icons.Rounded.ArrowBack, "Move page earlier")
            }
            IconButton(onClick = { onMove(index, index + 1); selected = index + 1 }, enabled = index < pages.lastIndex && !busy) {
                Icon(Icons.Rounded.ArrowForward, "Move page later")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onRemove(index) }, enabled = !busy) { Text("Remove page") }
        }
        Text("Finish", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScanFilterMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(filter == mode, { onFilter(mode) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(mode.label) }
            }
        }
        Text("Save as", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScanOutputFormat.entries.forEachIndexed { i, item ->
                SegmentedButton(format == item, { onFormat(item) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(item.name) }
            }
        }
        Button(onClick = onSave, enabled = pages.isNotEmpty() && title.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(10.dp)); Text("Save to Downloads")
        }
        Text("JPEG and PNG pages are saved together in a ZIP file.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
