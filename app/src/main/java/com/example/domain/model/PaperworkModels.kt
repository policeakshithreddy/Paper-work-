package com.example.domain.model

enum class DocumentType(val label: String) {
    PDF("PDF"),
    SCANNED("Scanned"),
    CONVERTED("Converted")
}

enum class SortOption(val label: String) {
    DATE_MODIFIED("Last modified"),
    DATE_CREATED("Date added"),
    NAME("Name"),
    FILE_SIZE("File size")
}

enum class DocumentFilter(val label: String) {
    ALL("All"),
    PDF("PDFs"),
    SCANNED("Scans"),
    RECENT("This week")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    LEGAL("Legal", 612, 1008)
}

enum class PageOrientation(val label: String) { PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

enum class PdfQuality(val label: String, val jpegQuality: Int, val maxDimensionPx: Int) {
    HIGH("High", 92, 2400),
    BALANCED("Balanced", 82, 1800),
    COMPACT("Compact", 68, 1280)
}

enum class MarginSize(val label: String, val marginPt: Int) {
    NONE("None", 0), SMALL("Small", 18), STANDARD("Standard", 36)
}

enum class PrintPageOrder(val label: String) { NORMAL("Normal"), REVERSE("Reverse") }
enum class PrintDpiOption(val label: String, val dpi: Int) { ORIGINAL_VECTOR("Original", 0), DPI_300("300 DPI", 300) }
enum class PrintScalingOption(val label: String) { FIT_TO_PAGE("Fit to page"), ACTUAL_SIZE("Actual size") }
enum class PrintColorOption(val label: String) { COLOR("Color"), GRAYSCALE("Grayscale") }
enum class PrintDuplexOption(val label: String) { NONE("Single-sided"), LONG_EDGE("Two-sided"), SHORT_EDGE("Short-edge") }
enum class ScanOutputFormat(val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "pdf", "application/pdf"),
    JPEG("JPEG bundle", "zip", "application/zip"),
    PNG("PNG bundle", "zip", "application/zip")
}
enum class ScanFilterMode(val label: String) { ORIGINAL("Original"), ENHANCED_COLOR("Enhanced"), INK_SAVING_GRAYSCALE("Grayscale") }
enum class ThemePreference(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class PrintConfiguration(
    val pageOrder: PrintPageOrder = PrintPageOrder.NORMAL,
    val pageRangeText: String = "",
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val colorOption: PrintColorOption = PrintColorOption.COLOR,
    val duplexOption: PrintDuplexOption = PrintDuplexOption.NONE,
    val copies: Int = 1,
    val scaling: PrintScalingOption = PrintScalingOption.FIT_TO_PAGE
)

/** A document owned by Paperwork and published to the user's Downloads collection. */
data class PaperworkDocument(
    val id: String,
    val title: String,
    val fileName: String,
    val contentUri: String,
    val fileSize: Long,
    val pageCount: Int,
    val documentType: DocumentType,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedPage: Int = 0,
    val nativePdfText: String = "",
    val ocrRecognizedText: String = ""
)
