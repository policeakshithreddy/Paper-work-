package com.example.domain.model

enum class DocumentType(val label: String) {
    PDF("PDF"),
    SCANNED("Scanned"),
    CONVERTED("Converted")
}

enum class SortOption(val label: String) {
    DATE_MODIFIED("Date modified"),
    DATE_CREATED("Date created"),
    NAME("Name"),
    FILE_SIZE("File size")
}

enum class DocumentFilter(val label: String) {
    ALL("All documents"),
    PDF("PDFs"),
    SCANNED("Scanned"),
    RECENT("Recently modified")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int, val dimensionsDesc: String) {
    A4("A4", 595, 842, "210 × 297 mm"),
    LETTER("Letter", 612, 792, "8.5 × 11 in"),
    LEGAL("Legal", 612, 1008, "8.5 × 14 in")
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PdfQuality(val label: String, val jpegQuality: Int, val maxDimensionPx: Int, val description: String) {
    HIGH("High (Print archival)", 92, 2400, "Sharpest text & detail"),
    BALANCED("Balanced (Recommended)", 82, 1800, "Crisp readability & compact size"),
    COMPACT("Compact (Fast share)", 68, 1280, "Smallest file footprint")
}

enum class MarginSize(val label: String, val marginPt: Int) {
    NONE("No margin", 0),
    SMALL("Small (18 pt)", 18),
    STANDARD("Standard (36 pt)", 36)
}

enum class PrintPageOrder(val label: String) {
    NORMAL("Normal order (1 → N)"),
    REVERSE("Reverse order (N → 1)")
}

enum class PrintColorOption(val label: String) {
    COLOR("Full Color"),
    GRAYSCALE("Grayscale / Black & White")
}

enum class PrintDuplexOption(val label: String) {
    NONE("Single-sided"),
    LONG_EDGE("Two-sided (Long-edge)"),
    SHORT_EDGE("Two-sided (Short-edge)")
}

enum class ThemePreference(val label: String) {
    SYSTEM("System default"),
    LIGHT("Light theme"),
    DARK("Dark theme")
}

/**
 * Scanner image enhancement filter modes.
 * - [ORIGINAL]: Untouched camera capture.
 * - [ENHANCED_COLOR]: Default color-preserving shadow normalization that keeps blue/red stamps,
 *   signatures, and highlights intact.
 * - [INK_SAVING_GRAYSCALE]: Optional monochrome high-contrast mode for ink-saving printing.
 */
enum class ScanFilterMode(val label: String, val description: String) {
    ORIGINAL("Original", "Untouched camera capture"),
    ENHANCED_COLOR("Enhanced Color (Default)", "Shadow-normalized while preserving color stamps & ink"),
    INK_SAVING_GRAYSCALE("Ink-Saving Grayscale", "Optional high-contrast black & white")
}

/**
 * Explicitly distinguishes embedded PDF text extraction from on-device OCR recognition.
 */
enum class TextSource(val label: String) {
    NATIVE_PDF_LAYER("Embedded PDF Text Layer"),
    MLKIT_OCR("On-Device ML Kit OCR"),
    BOTH("Embedded PDF Text + ML Kit OCR"),
    NONE("No Extracted Text")
}

/**
 * Tracks whether a vault document can be decrypted by the active Keystore key version
 * or whether its key version was permanently invalidated.
 */
enum class DocumentEncryptionStatus(val label: String) {
    ACTIVE_READABLE("Encrypted (Readable)"),
    UNREADABLE_KEY_INVALIDATED("Unreadable (Key Invalidated)")
}

data class PrintConfiguration(
    val pageOrder: PrintPageOrder = PrintPageOrder.NORMAL,
    val pageRangeText: String = "", // empty means all pages, or e.g. "1-3, 5"
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val colorOption: PrintColorOption = PrintColorOption.COLOR,
    val duplexOption: PrintDuplexOption = PrintDuplexOption.NONE,
    val copies: Int = 1
)

/**
 * Represents a single scanned page preserving both the untouched original image
 * and the enhanced document version permanently until deleted.
 */
data class ScanPageRecord(
    val pageId: String,
    val pageIndex: Int,
    val originalEncryptedPath: String,
    val enhancedEncryptedPath: String,
    val activeEnhanced: Boolean = true,
    val filterMode: ScanFilterMode = ScanFilterMode.ENHANCED_COLOR,
    val rotationDegrees: Int = 0
)

/**
 * Decrypted domain representation of a Paperwork document in the user's local vault.
 * Keeps [nativePdfText] (embedded PDF text streams) strictly separate from [ocrRecognizedText]
 * (ML Kit optical character recognition output).
 */
data class PaperworkDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val encryptedPdfPath: String,
    val encryptedThumbnailPath: String?,
    val fileSize: Long,
    val pageCount: Int,
    val documentType: DocumentType,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedPage: Int = 0,
    val hasNativeTextLayer: Boolean = false,
    val nativePdfText: String = "",
    val ocrRecognizedText: String = "",
    val scanPages: List<ScanPageRecord> = emptyList(),
    val currentlyUsingEnhanced: Boolean = true,
    val activeScanFilterMode: ScanFilterMode = ScanFilterMode.ENHANCED_COLOR,
    val keyVersion: Int = 1,
    val encryptionStatus: DocumentEncryptionStatus = DocumentEncryptionStatus.ACTIVE_READABLE,
    val hasDigitalSignatures: Boolean = false
) {
    val combinedSearchableText: String
        get() = buildString {
            if (nativePdfText.isNotBlank()) append(nativePdfText)
            if (ocrRecognizedText.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(ocrRecognizedText)
            }
        }

    val textSource: TextSource
        get() = when {
            nativePdfText.isNotBlank() && ocrRecognizedText.isNotBlank() -> TextSource.BOTH
            nativePdfText.isNotBlank() -> TextSource.NATIVE_PDF_LAYER
            ocrRecognizedText.isNotBlank() -> TextSource.MLKIT_OCR
            else -> TextSource.NONE
        }

    // Backwards-compatible alias for UI snippets
    val extractedOcrText: String
        get() = ocrRecognizedText.ifBlank { nativePdfText }
}
