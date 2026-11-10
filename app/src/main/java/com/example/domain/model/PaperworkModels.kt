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
    val rotationDegrees: Int = 0
)

/**
 * Decrypted domain representation of a Paperwork document in the user's local vault.
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
    val extractedOcrText: String = "",
    val scanPages: List<ScanPageRecord> = emptyList(),
    val currentlyUsingEnhanced: Boolean = true
)
