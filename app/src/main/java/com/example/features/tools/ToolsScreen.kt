package com.example.features.tools

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PdfQuality
import com.example.ui.ConversionStudioState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    documents: List<PaperworkDocument>,
    conversionState: ConversionStudioState,
    onPickImagesForConversion: () -> Unit,
    onRotateConversionPage: (Int) -> Unit,
    onMoveConversionPage: (Int, Int) -> Unit,
    onRemoveConversionPage: (Int) -> Unit,
    onUpdateConversionConfig: (String, PageSizeOption, PageOrientation, MarginSize, PdfQuality) -> Unit,
    onExportConversionToPdf: (Boolean) -> Unit,
    onLaunchScanner: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenPrintDialogForDocument: (PaperworkDocument) -> Unit,
    onOpenDocumentInViewer: (PaperworkDocument) -> Unit,
    onCreatePdfBoxModifiedCopy: (PaperworkDocument, String, Boolean, String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var useEnhancedForConversion by remember { mutableStateOf(true) }
    var selectedDocForPrintPicker by remember { mutableStateOf(false) }
    var selectedDocForVectorPrep by remember { mutableStateOf<PaperworkDocument?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_screen"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Convert & Print Hub",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Focused document conversion, multi-page PDF creation, vector page preparation, and reverse-order printing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Primary Image-to-PDF & Multi-Page PDF Creation Studio Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("image_to_pdf_studio_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Image to PDF Converter",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "A4, Letter, Legal • Portrait/Landscape • Margins & Quality",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (conversionState.pages.isEmpty()) {
                        Text(
                            text = "Select one or multiple images from your device using the zero-permission Android Photo Picker to arrange, rotate, and export a formatted PDF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onPickImagesForConversion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("select_images_to_pdf_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Images to Convert")
                        }
                    } else {
                        // Staged Images Strip with Reorder, Rotate, and Remove
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${conversionState.pages.size} ${if (conversionState.pages.size == 1) "page" else "pages"} staged",
                                style = MaterialTheme.typography.titleSmall
                            )
                            TextButton(onClick = onPickImagesForConversion) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add more")
                            }
                        }

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !useEnhancedForConversion,
                                onClick = { useEnhancedForConversion = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Original Colors")
                            }
                            SegmentedButton(
                                selected = useEnhancedForConversion,
                                onClick = { useEnhancedForConversion = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Enhanced Document Contrast")
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(conversionState.pages, key = { _, p -> p.id }) { idx, page ->
                                val bmp = if (useEnhancedForConversion) page.enhancedBitmap else page.originalBitmap
                                val rotated = remember(bmp, page.rotationDegrees) {
                                    rotateConversionBmp(bmp, page.rotationDegrees)
                                }
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier.width(148.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            bitmap = rotated.asImageBitmap(),
                                            contentDescription = "Staged page ${idx + 1}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Page ${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = { if (idx > 0) onMoveConversionPage(idx, idx - 1) },
                                                enabled = idx > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move left")
                                            }
                                            IconButton(
                                                onClick = { onRotateConversionPage(idx) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate")
                                            }
                                            IconButton(
                                                onClick = { if (idx < conversionState.pages.lastIndex) onMoveConversionPage(idx, idx + 1) },
                                                enabled = idx < conversionState.pages.lastIndex,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move right")
                                            }
                                            IconButton(
                                                onClick = { onRemoveConversionPage(idx) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Remove",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = conversionState.documentTitle,
                            onValueChange = {
                                onUpdateConversionConfig(
                                    it,
                                    conversionState.selectedPageSize,
                                    conversionState.selectedOrientation,
                                    conversionState.selectedMargin,
                                    conversionState.selectedQuality
                                )
                            },
                            label = { Text("Output PDF filename") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Page size", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PageSizeOption.entries.forEach { size ->
                                FilterChip(
                                    selected = conversionState.selectedPageSize == size,
                                    onClick = {
                                        onUpdateConversionConfig(
                                            conversionState.documentTitle,
                                            size,
                                            conversionState.selectedOrientation,
                                            conversionState.selectedMargin,
                                            conversionState.selectedQuality
                                        )
                                    },
                                    label = { Text("${size.label} (${size.dimensionsDesc})") }
                                )
                            }
                        }

                        Text("Orientation & Margins", style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PageOrientation.entries.forEachIndexed { idx, orient ->
                                SegmentedButton(
                                    selected = conversionState.selectedOrientation == orient,
                                    onClick = {
                                        onUpdateConversionConfig(
                                            conversionState.documentTitle,
                                            conversionState.selectedPageSize,
                                            orient,
                                            conversionState.selectedMargin,
                                            conversionState.selectedQuality
                                        )
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = PageOrientation.entries.size)
                                ) {
                                    Text(orient.label)
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MarginSize.entries.forEach { margin ->
                                FilterChip(
                                    selected = conversionState.selectedMargin == margin,
                                    onClick = {
                                        onUpdateConversionConfig(
                                            conversionState.documentTitle,
                                            conversionState.selectedPageSize,
                                            conversionState.selectedOrientation,
                                            margin,
                                            conversionState.selectedQuality
                                        )
                                    },
                                    label = { Text(margin.label) }
                                )
                            }
                        }

                        Text("Image quality", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfQuality.entries.forEach { q ->
                                FilterChip(
                                    selected = conversionState.selectedQuality == q,
                                    onClick = {
                                        onUpdateConversionConfig(
                                            conversionState.documentTitle,
                                            conversionState.selectedPageSize,
                                            conversionState.selectedOrientation,
                                            conversionState.selectedMargin,
                                            q
                                        )
                                    },
                                    label = { Text(q.label) }
                                )
                            }
                        }

                        Button(
                            onClick = { onExportConversionToPdf(useEnhancedForConversion) },
                            enabled = !conversionState.isConverting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("export_converted_pdf_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (conversionState.isConverting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Generate Encrypted PDF (${conversionState.pages.size} ${if (conversionState.pages.size == 1) "page" else "pages"})",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Core Focused Utilities Grid
        item {
            Text(
                text = "Core Document Workflows",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            FocusedToolCard(
                icon = Icons.Default.Print,
                title = "Print & Reverse Page Order (N → 1)",
                description = "Print any PDF via Android PrintManager in Normal (1 → N) or Reverse (N → 1) order with custom page ranges, paper sizes, color/grayscale, and duplex options.",
                actionLabel = "Select Document to Print",
                testTag = "tool_card_reverse_print",
                onClick = { selectedDocForPrintPicker = true }
            )
        }

        item {
            FocusedToolCard(
                icon = Icons.Default.DocumentScanner,
                title = "Multi-Page Document Scanner",
                description = "Capture documents with ML Kit Document Scanner or CameraX fallback. Permanently preserves Original images alongside reversible Enhanced pages.",
                actionLabel = "Open Scanner Studio",
                testTag = "tool_card_scanner",
                onClick = onLaunchScanner
            )
        }

        item {
            FocusedToolCard(
                icon = Icons.Default.SwapVert,
                title = "Lossless Vector Page Range & Rotation (PDFBox)",
                description = "Reorder pages, reverse page sequence, extract a page range, or rotate pages on an existing PDF using Apache PDFBox without rasterizing vector text.",
                actionLabel = "Prepare Existing PDF",
                testTag = "tool_card_vector_prep",
                onClick = {
                    val first = documents.firstOrNull()
                    if (first != null) {
                        selectedDocForVectorPrep = first
                    } else {
                        onImportPdf()
                    }
                }
            )
        }

        item {
            FocusedToolCard(
                icon = Icons.Default.TextSnippet,
                title = "On-Device OCR & Text Selection",
                description = "Open any scanned or text-based PDF to run on-device Google ML Kit Text Recognition, search keywords, and copy text offline.",
                actionLabel = "Select Document for OCR",
                testTag = "tool_card_ocr",
                onClick = { selectedDocForPrintPicker = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }

    // Document Picker Dialog for Printing or OCR
    if (selectedDocForPrintPicker) {
        AlertDialog(
            onDismissRequest = { selectedDocForPrintPicker = false },
            title = { Text("Select a document") },
            text = {
                if (documents.isEmpty()) {
                    Text("Your vault is empty. Import a PDF or scan a document first.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(260.dp)
                    ) {
                        items(documents, key = { it.id }) { doc ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedDocForPrintPicker = false
                                        onOpenPrintDialogForDocument(doc)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${doc.pageCount} pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                selectedDocForPrintPicker = false
                                                onOpenDocumentInViewer(doc)
                                            }
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Open viewer")
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedDocForPrintPicker = false
                                                onOpenPrintDialogForDocument(doc)
                                            }
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = "Print")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (documents.isEmpty()) {
                    Button(
                        onClick = {
                            selectedDocForPrintPicker = false
                            onImportPdf()
                        }
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDocForPrintPicker = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Vector-Preserving PDFBox Page Range / Reverse / Rotate Dialog
    val prepDoc = selectedDocForVectorPrep
    if (prepDoc != null) {
        var outputTitle by remember(prepDoc.id) { mutableStateOf("${prepDoc.title} (Prepared)") }
        var reversePages by remember { mutableStateOf(true) }
        var pageRange by remember { mutableStateOf("") }
        var rotationDeg by remember { mutableIntStateOf(0) }

        AlertDialog(
            onDismissRequest = { selectedDocForVectorPrep = null },
            title = { Text("Vector-Preserving PDF Preparation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Uses Apache PDFBox-Android to reorder or extract pages from '${prepDoc.title}' without rasterizing vector text or embedded fonts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        label = { Text("New document title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = pageRange,
                        onValueChange = { pageRange = it },
                        label = { Text("Page range (blank = all ${prepDoc.pageCount} pages)") },
                        placeholder = { Text("e.g. 1-${prepDoc.pageCount}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    FilterChip(
                        selected = reversePages,
                        onClick = { reversePages = !reversePages },
                        label = { Text("Reverse page order (Page ${prepDoc.pageCount} → Page 1)") }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Rotate pages:", style = MaterialTheme.typography.labelLarge)
                        listOf(0, 90, 180, 270).forEach { deg ->
                            FilterChip(
                                selected = rotationDeg == deg,
                                onClick = { rotationDeg = deg },
                                label = { Text("${deg}°") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePdfBoxModifiedCopy(
                            prepDoc,
                            outputTitle,
                            reversePages,
                            pageRange,
                            rotationDeg
                        )
                        selectedDocForVectorPrep = null
                    }
                ) {
                    Text("Save Prepared PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDocForVectorPrep = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FocusedToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private fun rotateConversionBmp(source: Bitmap, degrees: Int): Bitmap {
    val norm = ((degrees % 360) + 360) % 360
    if (norm == 0) return source
    val matrix = Matrix().apply { postRotate(norm.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
