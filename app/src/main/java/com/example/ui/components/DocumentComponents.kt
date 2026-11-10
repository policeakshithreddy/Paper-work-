package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.DocumentType
import com.example.domain.model.PaperworkDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatRelativeDate(epochMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(epochMs))
}

@Composable
fun DocumentThumbnailView(
    document: PaperworkDocument,
    repository: DocumentRepository,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(document.id, document.modifiedAt) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(document.id, document.modifiedAt) {
        thumbnail = repository.loadDecryptedThumbnail(document)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Preview of ${document.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = when {
                    document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED -> Icons.Default.Warning
                    document.documentType == DocumentType.SCANNED -> Icons.Default.Scanner
                    document.documentType == DocumentType.CONVERTED -> Icons.Default.Description
                    else -> Icons.Default.PictureAsPdf
                },
                contentDescription = document.documentType.label,
                tint = if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun DocumentListItemCard(
    document: PaperworkDocument,
    repository: DocumentRepository,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleOriginalEnhanced: ((Boolean) -> Unit)? = null,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isUnreadable = document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .testTag("document_card_${document.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnreadable) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumbnailView(
                document = document,
                repository = repository,
                modifier = Modifier
                    .width(56.dp)
                    .height(74.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isUnreadable -> MaterialTheme.colorScheme.errorContainer
                            document.documentType == DocumentType.SCANNED -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = when {
                                isUnreadable -> "Key v${document.keyVersion} Invalidated"
                                document.documentType == DocumentType.SCANNED -> {
                                    if (document.currentlyUsingEnhanced) "Scan • Enhanced" else "Scan • Original"
                                }
                                else -> document.documentType.label
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isUnreadable -> MaterialTheme.colorScheme.onErrorContainer
                                document.documentType == DocumentType.SCANNED -> MaterialTheme.colorScheme.onTertiaryContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"} • ${formatFileSize(document.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUnreadable) Icons.Default.Warning else Icons.Default.Lock,
                        contentDescription = "AES-256-GCM Encrypted",
                        tint = if (isUnreadable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Key v${document.keyVersion} • ${formatRelativeDate(document.modifiedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("document_menu_${document.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Document actions for ${document.title}"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (!isUnreadable) {
                        DropdownMenuItem(
                            text = { Text("Open viewer") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        if (document.scanPages.isNotEmpty() && onToggleOriginalEnhanced != null) {
                            val targetMode = !document.currentlyUsingEnhanced
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (targetMode) "Switch to Enhanced scan"
                                        else "Switch to Original scan"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleOriginalEnhanced(targetMode)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Print / Reverse print") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPrint()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share PDF") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete from vault",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDocumentDialog(
    document: PaperworkDocument,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var titleText by remember(document.id) { mutableStateOf(document.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename document") },
        text = {
            Column {
                Text(
                    text = "Enter a new title for this encrypted PDF document:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Document name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titleText.isNotBlank()) {
                        onConfirm(titleText.trim())
                    }
                },
                enabled = titleText.isNotBlank(),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteDocumentConfirmDialog(
    document: PaperworkDocument,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete '${document.title}'?") },
        text = {
            Text(
                text = "This will remove the encrypted PDF, thumbnail, Room database record, and any preserved Original/Enhanced scan layers from your vault.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text(
                    text = "Delete permanently",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
