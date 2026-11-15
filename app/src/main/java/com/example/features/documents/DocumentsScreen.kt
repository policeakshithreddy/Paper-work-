package com.example.features.documents

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.ui.components.*

@Composable
fun DocumentsScreen(documents: List<PaperworkDocument>, query: String, filter: DocumentFilter,
    sort: SortOption, grid: Boolean, onQuery: (String) -> Unit, onFilter: (DocumentFilter) -> Unit,
    onSort: (SortOption) -> Unit, onGrid: () -> Unit, onDirection: () -> Unit,
    onOpen: (PaperworkDocument) -> Unit, onAction: (PaperworkDocument, String) -> Unit) {
    var showSort by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search your documents") }, singleLine = true,
            shape = MaterialTheme.shapes.extraLarge, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") } })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DocumentFilter.entries) { item ->
                FilterChip(selected = item == filter, onClick = { onFilter(item) }, label = { Text(item.label) })
            }
        }
        Row {
            Text("${documents.size} documents", Modifier.weight(1f).padding(top = 12.dp),
                style = MaterialTheme.typography.labelLarge)
            Box {
                IconButton(onClick = { showSort = true }) { Icon(Icons.Rounded.Sort, "Sort documents") }
                DropdownMenu(showSort, { showSort = false }) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onSort(option); showSort = false },
                            trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null) })
                    }
                    DropdownMenuItem(text = { Text("Reverse order") }, onClick = { onDirection(); showSort = false })
                }
            }
            IconButton(onClick = onGrid) { Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, "Toggle document layout") }
        }
        if (documents.isEmpty()) EmptyState("Nothing here yet", "Scan a document, import a PDF, or try a different search.")
        LazyVerticalGrid(columns = if (grid) GridCells.Adaptive(260.dp) else GridCells.Fixed(1),
            contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(documents, key = { it.id }) { document ->
                DocumentRow(document, { onOpen(document) }, { onAction(document, it) }, Modifier.animateItem())
            }
        }
    }
}
