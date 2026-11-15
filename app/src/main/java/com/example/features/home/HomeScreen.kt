package com.example.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.PaperworkDocument
import com.example.ui.components.*

@Composable
fun HomeScreen(documents: List<PaperworkDocument>, onScan: () -> Unit, onImport: () -> Unit,
    onImages: () -> Unit, onAll: () -> Unit, onOpen: (PaperworkDocument) -> Unit,
    onAction: (PaperworkDocument, String) -> Unit) {
    val weekly = documents.count { System.currentTimeMillis() - it.createdAt < 604800000L }
    LazyColumn(contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 64.dp, bottomStart = 32.dp, bottomEnd = 32.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("LESS PAPER. MORE SPACE.", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(14.dp))
                            Text("Make room\nfor what matters.", style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold)
                        }
                        PaperMark(Modifier.size(76.dp))
                    }
                    Text("Capture it. Find it. Get on with your day.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onScan,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Icon(Icons.Rounded.DocumentScanner, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Scan a document", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("${documents.size}", "Documents", Modifier.weight(1f))
                Metric("$weekly", "Added this week", Modifier.weight(1f))
                Metric(formatFileSize(documents.sumOf { it.fileSize }), "In Downloads", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile("Import PDF", "Bring your files together", Icons.Rounded.FileOpen, onImport, Modifier.weight(1f))
                ActionTile("Create PDF", "Turn photos into pages", Icons.Rounded.AddPhotoAlternate, onImages, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Pick up where you left off") { TextButton(onClick = onAll) { Text("See all") } } }
        if (documents.isEmpty()) item {
            EmptyState("A fresh page", "Your scans and imports will appear here. Start with a document you need today.")
        }
        items(documents.take(6), key = { it.id }) { doc ->
            DocumentRow(doc, { onOpen(doc) }, { onAction(doc, it) }, Modifier.animateItem())
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
