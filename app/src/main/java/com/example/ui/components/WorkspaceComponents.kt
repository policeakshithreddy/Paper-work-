package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PaperworkDocument
import java.util.Locale

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
}

@Composable
fun SectionTitle(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        trailing()
    }
}

@Composable
fun PaperMark(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.tertiaryContainer
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val paper = Path().apply {
            moveTo(w * .18f, h * .10f); lineTo(w * .65f, h * .10f)
            lineTo(w * .86f, h * .31f); lineTo(w * .86f, h * .9f)
            lineTo(w * .18f, h * .9f); close()
        }
        drawPath(paper, base)
        val fold = Path().apply {
            moveTo(w * .65f, h * .1f); lineTo(w * .65f, h * .31f)
            lineTo(w * .86f, h * .31f); close()
        }
        drawPath(fold, ink.copy(alpha = .22f))
        for (i in 0..2) drawLine(ink, Offset(w * .32f, h * (.48f + i * .13f)),
            Offset(w * (if (i == 2) .57f else .72f), h * (.48f + i * .13f)), w * .035f)
    }
}

@Composable
fun EmptyState(title: String, description: String, icon: ImageVector = Icons.Rounded.FolderOpen,
               action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, null, Modifier.padding(24.dp).size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action()
    }
}

@Composable
fun DocumentRow(document: PaperworkDocument, onOpen: () -> Unit,
                onAction: (String) -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null,
                    Modifier.padding(14.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text("${document.pageCount} pages · ${formatFileSize(document.fileSize)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateUtils.getRelativeTimeSpanString(document.modifiedAt).toString(),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Document actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("Share", "Print", "Rename", "Duplicate", "Delete").forEach { action ->
                        DropdownMenuItem(text = { Text(action) }, onClick = { menu = false; onAction(action) })
                    }
                }
            }
        }
    }
}

@Composable
fun ActionTile(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit,
               modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 100.dp),
        shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(18.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
