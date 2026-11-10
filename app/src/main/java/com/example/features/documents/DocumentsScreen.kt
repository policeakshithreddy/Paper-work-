package com.example.features.documents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repository.DocumentRepository
import com.example.domain.model.DocumentFilter
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.SortOption
import com.example.ui.components.DocumentListItemCard
import com.example.ui.components.DocumentThumbnailView
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatRelativeDate

@Composable
fun DocumentsScreen(
    documents: List<PaperworkDocument>,
    searchQuery: String,
    selectedFilter: DocumentFilter,
    selectedSort: SortOption,
    sortAscending: Boolean,
    isGridView: Boolean,
    repository: DocumentRepository,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (DocumentFilter) -> Unit,
    onSortChange: (SortOption, Boolean) -> Unit,
    onGridViewToggle: (Boolean) -> Unit,
    onScanDocument: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenDocument: (PaperworkDocument) -> Unit,
    onRenameDocument: (PaperworkDocument) -> Unit,
    onDuplicateDocument: (PaperworkDocument) -> Unit,
    onToggleOriginalEnhanced: (PaperworkDocument, Boolean) -> Unit,
    onShareDocument: (PaperworkDocument) -> Unit,
    onPrintDocument: (PaperworkDocument) -> Unit,
    onDeleteDocument: (PaperworkDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .testTag("documents_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header with Sort & Grid/List View Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Documents",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${documents.size} encrypted ${if (documents.size == 1) "document" else "documents"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { sortMenuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sort_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort documents"
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            val isSelected = selectedSort == option
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null
                                        )
                                    }
                                },
                                onClick = {
                                    val nextAsc = if (isSelected) !sortAscending else (option == SortOption.NAME)
                                    onSortChange(option, nextAsc)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onGridViewToggle(!isGridView) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("view_mode_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search filename, title, or OCR text...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("documents_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(DocumentFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedFilter != DocumentFilter.ALL) {
                            "No matching documents found"
                        } else {
                            "No documents in your vault yet"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan physical paperwork with ML Kit Document Scanner or import existing PDFs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onScanDocument) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan document")
                        }
                        FilledTonalButton(onClick = onImportPdf) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import PDF")
                        }
                    }
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentGridCard(
                        document = doc,
                        repository = repository,
                        onOpen = { onOpenDocument(doc) },
                        onPrint = { onPrintDocument(doc) },
                        onRename = { onRenameDocument(doc) },
                        onDelete = { onDeleteDocument(doc) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    DocumentListItemCard(
                        document = doc,
                        repository = repository,
                        onOpen = { onOpenDocument(doc) },
                        onRename = { onRenameDocument(doc) },
                        onDuplicate = { onDuplicateDocument(doc) },
                        onToggleOriginalEnhanced = { useEnhanced ->
                            onToggleOriginalEnhanced(doc, useEnhanced)
                        },
                        onShare = { onShareDocument(doc) },
                        onPrint = { onPrintDocument(doc) },
                        onDelete = { onDeleteDocument(doc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentGridCard(
    document: PaperworkDocument,
    repository: DocumentRepository,
    onOpen: () -> Unit,
    onPrint: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            DocumentThumbnailView(
                document = document,
                repository = repository,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${document.pageCount}p • ${formatFileSize(document.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRelativeDate(document.modifiedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options for ${document.title}"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            onClick = {
                                menuExpanded = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print / Reverse print") },
                            onClick = {
                                menuExpanded = false
                                onPrint()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
}
