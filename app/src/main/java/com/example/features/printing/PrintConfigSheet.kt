package com.example.features.printing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PrintColorOption
import com.example.domain.model.PrintConfiguration
import com.example.domain.model.PrintDuplexOption
import com.example.domain.model.PrintPageOrder

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrintConfigSheet(
    document: PaperworkDocument,
    defaultPrintOrder: PrintPageOrder,
    defaultPageSize: PageSizeOption,
    onDismiss: () -> Unit,
    onConfirmPrint: (PrintConfiguration) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pageOrder by remember { mutableStateOf(defaultPrintOrder) }
    var pageRangeText by remember { mutableStateOf("") }
    var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var pageSize by remember { mutableStateOf(defaultPageSize) }
    var colorOption by remember { mutableStateOf(PrintColorOption.COLOR) }
    var duplexOption by remember { mutableStateOf(PrintDuplexOption.NONE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Print configuration",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${document.title} • ${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Page Ordering (Normal 1 -> N vs Reverse N -> 1)
            Text(
                text = "Page order",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PrintPageOrder.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = pageOrder == option,
                        onClick = { pageOrder = option },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PrintPageOrder.entries.size
                        ),
                        modifier = Modifier.testTag("print_order_${option.name.lowercase()}")
                    ) {
                        Text(option.label)
                    }
                }
            }

            if (pageOrder == PrintPageOrder.REVERSE) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reverse printing constructs a lossless vector PDF ordered from Page ${document.pageCount} down to Page 1 before sending to Android PrintManager.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Page Range
            OutlinedTextField(
                value = pageRangeText,
                onValueChange = { pageRangeText = it },
                label = { Text("Page range (blank = all ${document.pageCount} pages)") },
                placeholder = { Text("e.g. 1-${document.pageCount} or 1, 3") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("print_page_range_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Paper Size & Orientation
            Text(
                text = "Paper size & orientation",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageSizeOption.entries.forEach { size ->
                    FilterChip(
                        selected = pageSize == size,
                        onClick = { pageSize = size },
                        label = { Text("${size.label} (${size.dimensionsDesc})") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PageOrientation.entries.forEachIndexed { idx, orient ->
                    SegmentedButton(
                        selected = orientation == orient,
                        onClick = { orientation = orient },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = PageOrientation.entries.size
                        )
                    ) {
                        Text(orient.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Color & Duplex
            Text(
                text = "Color & two-sided (duplex) mode",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PrintColorOption.entries.forEachIndexed { idx, col ->
                    SegmentedButton(
                        selected = colorOption == col,
                        onClick = { colorOption = col },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = idx,
                            count = PrintColorOption.entries.size
                        )
                    ) {
                        Text(col.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrintDuplexOption.entries.forEach { dup ->
                    FilterChip(
                        selected = duplexOption == dup,
                        onClick = { duplexOption = dup },
                        label = { Text(dup.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        onConfirmPrint(
                            PrintConfiguration(
                                pageOrder = pageOrder,
                                pageRangeText = pageRangeText,
                                orientation = orientation,
                                pageSize = pageSize,
                                colorOption = colorOption,
                                duplexOption = duplexOption
                            )
                        )
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("launch_system_print_button")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (pageOrder == PrintPageOrder.REVERSE) {
                            "System Print (Reverse)"
                        } else {
                            "Continue to System Print"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
