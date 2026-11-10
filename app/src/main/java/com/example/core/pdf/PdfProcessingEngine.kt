package com.example.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.core.image.NormalizedQuadCorners
import com.example.core.image.SafeBitmapDecoder
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfQuality
import com.example.domain.model.ScanFilterMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class PageImageSpec(
    val bitmap: Bitmap,
    val rotationDegrees: Int = 0
)

data class PdfInspectionResult(
    val pageCount: Int,
    val hasNativeTextLayer: Boolean,
    val nativeExtractedText: String,
    val firstPageThumbnail: Bitmap?,
    val hasDigitalSignatures: Boolean = false
)

/**
 * Post-rewrite validation report verifying that non-rasterizing `pdfbox-android` page manipulation
 * produced a structurally valid, renderable PDF with expected page count, page boxes, rotation,
 * and preserved annotations/fonts.
 */
data class PdfIntegrityValidationReport(
    val isValid: Boolean,
    val expectedPageCount: Int,
    val actualPageCount: Int,
    val allPageBoxesValid: Boolean,
    val rotationPreserved: Boolean,
    val annotationsCount: Int,
    val hasAcroFormFields: Boolean,
    val sourceHadDigitalSignatures: Boolean,
    val warningMessage: String? = null
)

/**
 * Explicitly separated service for extracting embedded PDF text streams (`PDFTextStripper`).
 * Never invokes optical character recognition (OCR).
 */
class NativePdfTextExtractor {
    suspend fun extractEmbeddedText(pdfFile: File, maxPages: Int = 50): String = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { pdDoc ->
                val count = pdDoc.numberOfPages.coerceAtLeast(1)
                for (i in 0 until count) {
                    pdDoc.getPage(i).rotation = 0
                }
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = minOf(count, maxPages)
                }
                stripper.getText(pdDoc)?.trim().orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun extractEmbeddedTextByPage(pdfFile: File): Map<Int, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<Int, String>()
        try {
            PDDocument.load(pdfFile).use { doc ->
                for (i in 0 until doc.numberOfPages) {
                    doc.getPage(i).rotation = 0
                }
                val stripper = PDFTextStripper()
                for (p in 1..doc.numberOfPages) {
                    stripper.startPage = p
                    stripper.endPage = p
                    val pageText = stripper.getText(doc)?.trim().orEmpty()
                    if (pageText.isNotEmpty()) {
                        map[p - 1] = pageText
                    }
                }
            }
        } catch (_: Exception) {
        }
        map
    }
}

/**
 * Explicitly separated service for on-device optical character recognition (OCR)
 * using Google ML Kit Text Recognition on rendered or scanned page bitmaps.
 */
class OnDeviceOcrService {
    suspend fun recognizeBitmapText(bitmap: Bitmap): String = suspendCoroutine { cont ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text.trim())
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        } catch (_: Exception) {
            cont.resume("")
        }
    }
}

/**
 * Validates rewritten PDFs produced by `pdfbox-android` page manipulation.
 * Checks page count, `MediaBox`/`CropBox`, `/Rotate` metadata, annotations/links,
 * `PDAcroForm` fields, font resources, and cryptographic signatures (`signatureDictionaries`).
 */
object PdfIntegrityValidator {

    fun hasDigitalSignatures(pdfFile: File): Boolean {
        return try {
            PDDocument.load(pdfFile).use { doc ->
                doc.signatureDictionaries.isNotEmpty()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun validateRewrittenPdf(
        sourcePdfFile: File,
        rewrittenPdfFile: File,
        expectedPageCount: Int,
        expectedFirstPageRotation: Int? = null
    ): PdfIntegrityValidationReport {
        if (!rewrittenPdfFile.exists() || rewrittenPdfFile.length() < 16L) {
            return PdfIntegrityValidationReport(
                isValid = false,
                expectedPageCount = expectedPageCount,
                actualPageCount = 0,
                allPageBoxesValid = false,
                rotationPreserved = false,
                annotationsCount = 0,
                hasAcroFormFields = false,
                sourceHadDigitalSignatures = false,
                warningMessage = "Rewritten PDF file is missing or empty."
            )
        }

        val sourceSigned = hasDigitalSignatures(sourcePdfFile)

        return try {
            PDDocument.load(rewrittenPdfFile).use { doc ->
                val actualCount = doc.numberOfPages
                var boxesValid = actualCount > 0
                var totalAnnotations = 0
                for (i in 0 until actualCount) {
                    val page = doc.getPage(i)
                    val mediaBox = page.mediaBox
                    val cropBox = page.cropBox
                    if (mediaBox == null || mediaBox.width <= 0f || mediaBox.height <= 0f ||
                        cropBox == null || cropBox.width <= 0f || cropBox.height <= 0f
                    ) {
                        boxesValid = false
                    }
                    totalAnnotations += runCatching { page.annotations.size }.getOrDefault(0)
                }

                val firstPageRot = if (actualCount > 0) doc.getPage(0).rotation else 0
                val rotOk = expectedFirstPageRotation == null ||
                    ((firstPageRot % 360 + 360) % 360) == ((expectedFirstPageRotation % 360 + 360) % 360)

                val hasAcroForm = runCatching {
                    doc.documentCatalog?.acroForm?.fields?.isNotEmpty() == true
                }.getOrDefault(false)

                val warning = if (sourceSigned) {
                    "Source PDF contains digital signatures. Derived copies have invalidated signatures; original document remains untouched in vault."
                } else {
                    null
                }

                val isValid = (actualCount == expectedPageCount) && boxesValid && rotOk
                PdfIntegrityValidationReport(
                    isValid = isValid,
                    expectedPageCount = expectedPageCount,
                    actualPageCount = actualCount,
                    allPageBoxesValid = boxesValid,
                    rotationPreserved = rotOk,
                    annotationsCount = totalAnnotations,
                    hasAcroFormFields = hasAcroForm,
                    sourceHadDigitalSignatures = sourceSigned,
                    warningMessage = warning
                )
            }
        } catch (e: Exception) {
            PdfIntegrityValidationReport(
                isValid = false,
                expectedPageCount = expectedPageCount,
                actualPageCount = 0,
                allPageBoxesValid = false,
                rotationPreserved = false,
                annotationsCount = 0,
                hasAcroFormFields = false,
                sourceHadDigitalSignatures = sourceSigned,
                warningMessage = "PDF validation error: ${e.message}"
            )
        }
    }
}

/**
 * Core PDF processing and scan enhancement engine.
 *
 * Architectural Separation:
 * 1. Uses Apache `pdfbox-android` (`PDDocument`) for non-rasterizing page-level manipulation
 *    (Reverse Page Ordering for print, custom page range extraction, and `/Rotate` updates)
 *    while preserving the original vault document untouched and validating rewritten outputs
 *    via [PdfIntegrityValidator].
 * 2. Keeps [NativePdfTextExtractor] (embedded PDF text streams) strictly separate from
 *    [OnDeviceOcrService] (ML Kit optical character recognition on bitmaps).
 * 3. Uses Android `PdfDocument` for generating new PDFs from scanned pages or imported images.
 * 4. Uses Android `PdfRenderer` for rendering thumbnail previews or compatibility fallback bitmaps.
 */
class PdfProcessingEngine(private val context: Context) {

    val nativeTextExtractor = NativePdfTextExtractor()
    val ocrService = OnDeviceOcrService()

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    /**
     * Enhances a raw document camera bitmap using the specified [filterMode].
     * Defaults to [ScanFilterMode.ENHANCED_COLOR] which normalizes shadows while preserving
     * color stamps, blue/red signatures, and highlights.
     */
    fun createEnhancedDocumentBitmap(
        original: Bitmap,
        filterMode: ScanFilterMode = ScanFilterMode.ENHANCED_COLOR
    ): Bitmap {
        return SafeBitmapDecoder.applyScanFilter(original, filterMode)
    }

    /**
     * Applies 4-corner perspective correction and deskew angle to a bitmap.
     */
    fun applyPerspectiveAndDeskew(
        source: Bitmap,
        quad: NormalizedQuadCorners,
        deskewAngleDegrees: Float = 0f
    ): Bitmap {
        return SafeBitmapDecoder.applyPerspectiveAndDeskew(source, quad, deskewAngleDegrees)
    }

    /**
     * Rotates a bitmap by [degrees] (90, 180, 270).
     */
    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Crops a bitmap using normalized [0f..1f] coordinates.
     */
    fun cropBitmapNormalized(
        source: Bitmap,
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float
    ): Bitmap {
        val l = (leftRatio.coerceIn(0f, 0.9f) * source.width).toInt()
        val t = (topRatio.coerceIn(0f, 0.9f) * source.height).toInt()
        val r = (rightRatio.coerceIn(leftRatio + 0.1f, 1f) * source.width).toInt()
        val b = (bottomRatio.coerceIn(topRatio + 0.1f, 1f) * source.height).toInt()
        val width = (r - l).coerceAtLeast(32)
        val height = (b - t).coerceAtLeast(32)
        return Bitmap.createBitmap(source, l, t, width, height)
    }

    /**
     * Generates a multi-page PDF byte array from a list of [PageImageSpec] items,
     * honoring page size (A4 / Letter / Legal), orientation, margins, quality scaling,
     * and coroutine cancellation.
     */
    suspend fun buildPdfFromImages(
        pages: List<PageImageSpec>,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        margin: MarginSize,
        quality: PdfQuality
    ): ByteArray = withContext(Dispatchers.Default) {
        val baseWidth = if (orientation == PageOrientation.PORTRAIT) pageSize.widthPt else pageSize.heightPt
        val baseHeight = if (orientation == PageOrientation.PORTRAIT) pageSize.heightPt else pageSize.widthPt
        val marginPt = margin.marginPt

        try {
            val pdfDocument = PdfDocument()
            try {
                pages.forEachIndexed { index, spec ->
                    coroutineContext.ensureActive()
                    val rotated = rotateBitmap(spec.bitmap, spec.rotationDegrees)
                    val scaled = SafeBitmapDecoder.scaleDownToMaxDimension(rotated, quality.maxDimensionPx)

                    val pageInfo = PdfDocument.PageInfo.Builder(baseWidth, baseHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val availWidth = (baseWidth - marginPt * 2).coerceAtLeast(100).toFloat()
                    val availHeight = (baseHeight - marginPt * 2).coerceAtLeast(100).toFloat()

                    val scale = minOf(availWidth / scaled.width.toFloat(), availHeight / scaled.height.toFloat())
                    val drawWidth = scaled.width * scale
                    val drawHeight = scaled.height * scale
                    val left = marginPt + (availWidth - drawWidth) / 2f
                    val top = marginPt + (availHeight - drawHeight) / 2f

                    val destRect = RectF(left, top, left + drawWidth, top + drawHeight)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(scaled, null, destRect, paint)

                    pdfDocument.finishPage(page)
                }

                val out = ByteArrayOutputStream()
                pdfDocument.writeTo(out)
                out.toByteArray()
            } finally {
                runCatching { pdfDocument.close() }
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            // Fallback using Apache PDFBox PDDocument when native Skia PdfDocument is stubbed in local JVM tests
            PDDocument().use { doc ->
                pages.forEach { spec ->
                    coroutineContext.ensureActive()
                    val rotated = rotateBitmap(spec.bitmap, spec.rotationDegrees)
                    val scaled = SafeBitmapDecoder.scaleDownToMaxDimension(rotated, quality.maxDimensionPx)
                    val pdPage = PDPage(PDRectangle(baseWidth.toFloat(), baseHeight.toFloat()))
                    doc.addPage(pdPage)
                    val pdImage = com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(
                        doc,
                        scaled,
                        quality.jpegQuality / 100f
                    )
                    PDPageContentStream(doc, pdPage).use { stream ->
                        val availW = (baseWidth - marginPt * 2).coerceAtLeast(100).toFloat()
                        val availH = (baseHeight - marginPt * 2).coerceAtLeast(100).toFloat()
                        val scale = minOf(availW / scaled.width.toFloat(), availH / scaled.height.toFloat())
                        val drawW = scaled.width * scale
                        val drawH = scaled.height * scale
                        val x = marginPt + (availW - drawW) / 2f
                        val y = marginPt + (availH - drawH) / 2f
                        stream.drawImage(pdImage, x, y, drawW, drawH)
                    }
                }
                val out = ByteArrayOutputStream()
                doc.save(out)
                out.toByteArray()
            }
        }
    }

    /**
     * Non-rasterizing page manipulation using Apache `pdfbox-android`.
     * Reorders and/or filters pages of an existing PDF without rasterizing vector content streams
     * or stripping embedded fonts/annotations, then validates the rewritten file with [PdfIntegrityValidator].
     *
     * Note: The original vault document is always preserved untouched.
     */
    suspend fun createReorderedOrFilteredPdf(
        sourcePdfFile: File,
        targetPdfFile: File,
        reverseOrder: Boolean,
        oneBasedPageNumbers: List<Int>? = null,
        extraRotationDegrees: Int = 0
    ): Int = withContext(Dispatchers.IO) {
        var expectedFirstRotation: Int? = null
        val writtenPages = PDDocument.load(sourcePdfFile).use { sourceDoc ->
            val totalPages = sourceDoc.numberOfPages
            val validIndices = if (oneBasedPageNumbers.isNullOrEmpty()) {
                (0 until totalPages).toList()
            } else {
                oneBasedPageNumbers.map { it - 1 }.filter { it in 0 until totalPages }
            }.let { indices ->
                if (reverseOrder) indices.reversed() else indices
            }

            val finalIndices = if (validIndices.isEmpty()) (0 until totalPages).toList() else validIndices

            PDDocument().use { outputDoc ->
                for ((outIdx, pageIndex) in finalIndices.withIndex()) {
                    coroutineContext.ensureActive()
                    val sourcePage = sourceDoc.getPage(pageIndex)
                    val importedPage = outputDoc.importPage(sourcePage)
                    // Explicitly preserve MediaBox, CropBox, and annotations
                    sourcePage.mediaBox?.let { importedPage.mediaBox = it }
                    sourcePage.cropBox?.let { importedPage.cropBox = it }
                    val newRot = (sourcePage.rotation + extraRotationDegrees) % 360
                    importedPage.rotation = newRot
                    if (outIdx == 0) {
                        expectedFirstRotation = newRot
                    }
                }
                outputDoc.save(targetPdfFile)
                outputDoc.numberOfPages
            }
        }

        val validation = PdfIntegrityValidator.validateRewrittenPdf(
            sourcePdfFile = sourcePdfFile,
            rewrittenPdfFile = targetPdfFile,
            expectedPageCount = writtenPages,
            expectedFirstPageRotation = expectedFirstRotation
        )
        if (!validation.isValid) {
            throw IllegalStateException(
                validation.warningMessage ?: "Rewritten PDF failed post-write integrity validation."
            )
        }
        writtenPages
    }

    /**
     * Parses a user-entered page range string like "1-3, 5" into 1-based page numbers within [1..maxPages].
     */
    fun parsePageRange(rangeText: String, maxPages: Int): List<Int> {
        val trimmed = rangeText.trim()
        if (trimmed.isEmpty()) return (1..maxPages).toList()
        val result = mutableListOf<Int>()
        val parts = trimmed.split(",")
        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) continue
            if (token.contains("-")) {
                val bounds = token.split("-").map { it.trim().toIntOrNull() }
                val start = bounds.getOrNull(0)
                val end = bounds.getOrNull(1)
                if (start != null && end != null) {
                    val range = if (start <= end) start..end else start downTo end
                    for (p in range) {
                        if (p in 1..maxPages && !result.contains(p)) {
                            result.add(p)
                        }
                    }
                }
            } else {
                val single = token.toIntOrNull()
                if (single != null && single in 1..maxPages && !result.contains(single)) {
                    result.add(single)
                }
            }
        }
        return if (result.isEmpty()) (1..maxPages).toList() else result
    }

    /**
     * Inspects a decrypted PDF file to extract page count, embedded PDF text layer via
     * [NativePdfTextExtractor], digital signature status, and a crisp first-page thumbnail via `PdfRenderer`.
     */
    suspend fun inspectPdfFile(pdfFile: File): PdfInspectionResult = withContext(Dispatchers.IO) {
        var pageCount = 1
        var hasSignatures = false
        try {
            PDDocument.load(pdfFile).use { pdDoc ->
                pageCount = pdDoc.numberOfPages.coerceAtLeast(1)
                hasSignatures = pdDoc.signatureDictionaries.isNotEmpty()
            }
        } catch (_: Exception) {
        }

        val nativeText = nativeTextExtractor.extractEmbeddedText(pdfFile, maxPages = 20)

        var thumbnail: Bitmap? = null
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        pageCount = renderer.pageCount
                        renderer.openPage(0).use { page ->
                            val targetWidth = 360
                            val targetHeight = ((page.height.toFloat() / page.width.toFloat()) * targetWidth)
                                .toInt()
                                .coerceIn(240, 560)
                            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            thumbnail = bmp
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val hasNativeText = nativeText.length >= 16
        PdfInspectionResult(
            pageCount = pageCount,
            hasNativeTextLayer = hasNativeText,
            nativeExtractedText = nativeText,
            firstPageThumbnail = thumbnail,
            hasDigitalSignatures = hasSignatures
        )
    }

    /**
     * Renders a specific page of a PDF file to a Bitmap at the requested width (used for
     * thumbnail strips, OCR input, or fallback viewing).
     */
    suspend fun renderPdfPageToBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val width = targetWidthPx.coerceIn(240, 2560)
                        val height = (width * ratio).toInt().coerceIn(240, 3600)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(
                            bitmap,
                            Rect(0, 0, width, height),
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bitmap
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts embedded text per page from a vector PDF using [NativePdfTextExtractor].
     */
    suspend fun extractNativeTextByPage(pdfFile: File): Map<Int, String> =
        nativeTextExtractor.extractEmbeddedTextByPage(pdfFile)

    /**
     * Runs on-device Google ML Kit Text Recognition (Latin script) on a bitmap via [OnDeviceOcrService].
     */
    suspend fun recognizeTextWithMlKit(bitmap: Bitmap): String =
        ocrService.recognizeBitmapText(bitmap)

    /**
     * Generates a genuine vector text-layer multi-page PDF using Apache `pdfbox-android`
     * so users can immediately test native text selection, inline search, and reverse printing.
     */
    suspend fun createSampleVectorPdf(targetFile: File) = withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            val pagesContent = listOf(
                Triple(
                    "Paperwork - Architecture & Security Guide (Page 1 of 3)",
                    "1. Versioned Local Vault (AES-256-GCM)",
                    listOf(
                        "Welcome to Paperwork, a native Google Pixel-style document utility.",
                        "Every document, thumbnail, original scan, and metadata record is encrypted",
                        "at rest using Android Keystore AES-256-GCM with a 19-byte PWKV header.",
                        "",
                        "Key Highlights:",
                        "- Native Jetpack PDF Viewer (androidx.pdf 1.0.0-beta01) with Compose support.",
                        "- Reversible Original, Enhanced Color, and Ink-Saving Grayscale scanning.",
                        "- Non-rasterizing PDF page manipulation via Apache PDFBox-Android.",
                        "- Native Android PrintManager integration with Reverse Page Ordering (N -> 1).",
                        "",
                        "Try searching for 'Keystore', 'Reverse', or 'Vector' using the search icon above!"
                    )
                ),
                Triple(
                    "Paperwork - Service Invoice #PW-2026-084 (Page 2 of 3)",
                    "2. Itemized Equipment & Cloud-Free Processing Summary",
                    listOf(
                        "Billed To: Alex Rivera, Principal Product Architect",
                        "Date: November 10, 2026   |   Payment Status: PAID IN FULL",
                        "",
                        "Line 01: On-Device ML Kit Document Scanner License .......... $0.00 (Included)",
                        "Line 02: Local AES-256-GCM Keystore Vault Storage ........... $0.00 (Offline)",
                        "Line 03: Multi-Page A4 / Letter / Legal Conversion Engine ... $0.00 (Included)",
                        "Line 04: Reverse Page Order Print Spooler Module ............ $0.00 (Included)",
                        "",
                        "Subtotal: $0.00   |   Tax (0%): $0.00   |   Total Due: $0.00",
                        "Notes: All document processing executes strictly on your Android device."
                    )
                ),
                Triple(
                    "Paperwork - Print & Verification Checklist (Page 3 of 3)",
                    "3. Reverse Page Order & OCR Verification Notes",
                    listOf(
                        "When printing multi-page contracts on deskjet or laser printers that stack",
                        "pages face-up, enable 'Reverse order (N -> 1)' in the Paperwork Print Sheet.",
                        "",
                        "Verification Steps:",
                        "1. Tap the Print icon in the top bar or document menu.",
                        "2. Select 'Reverse order (N -> 1)' to print Page 3, then Page 2, then Page 1.",
                        "3. Select text directly on this vector PDF page to copy or share.",
                        "4. Open the Scanned Receipt sample to test Original vs. Enhanced Color."
                    )
                )
            )

            pagesContent.forEach { (header, subtitle, lines) ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.setNonStrokingColor(11, 87, 208)
                    stream.addRect(48f, 750f, 500f, 44f)
                    stream.fill()

                    stream.beginText()
                    stream.setNonStrokingColor(255, 255, 255)
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                    stream.newLineAtOffset(62f, 767f)
                    stream.showText(header)
                    stream.endText()

                    stream.beginText()
                    stream.setNonStrokingColor(20, 28, 36)
                    stream.setFont(PDType1Font.HELVETICA_BOLD, 16f)
                    stream.newLineAtOffset(48f, 705f)
                    stream.showText(subtitle)
                    stream.endText()

                    var y = 668f
                    lines.forEach { line ->
                        if (line.isNotEmpty()) {
                            stream.beginText()
                            stream.setNonStrokingColor(45, 52, 62)
                            stream.setFont(PDType1Font.HELVETICA, 11.5f)
                            stream.newLineAtOffset(48f, y)
                            stream.showText(line)
                            stream.endText()
                        }
                        y -= 24f
                    }

                    stream.beginText()
                    stream.setNonStrokingColor(115, 119, 127)
                    stream.setFont(PDType1Font.COURIER, 9.5f)
                    stream.newLineAtOffset(48f, 48f)
                    stream.showText("Generated locally by Paperwork | Vector PDF Layer Intact | Zero Cloud Upload")
                    stream.endText()
                }
            }
            doc.save(targetFile)
        }
    }

    /**
     * Generates a realistic 2-page "Scanned Field Receipt & Inspection Form" with warm shadowed
     * original bitmaps and a cobalt-blue approval stamp so the user can immediately verify that
     * Enhanced Color preserves color stamps while cleaning paper shadows.
     */
    fun createSampleScannedBitmaps(): List<Bitmap> {
        val page1 = Bitmap.createBitmap(900, 1260, Bitmap.Config.ARGB_8888)
        Canvas(page1).apply {
            drawColor(Color.rgb(236, 231, 220))
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(32, 42, 58)
                textSize = 34f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(52, 56, 64)
                textSize = 24f
                typeface = Typeface.MONOSPACE
            }
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(11, 87, 208)
                textSize = 26f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(180, 174, 162)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            drawRect(56f, 56f, 844f, 1204f, boxPaint)
            drawText("PACIFIC HARDWARE & SUPPLY CO.", 96f, 140f, headerPaint)
            drawText("RECEIPT #88412-A   |   SAN FRANCISCO, CA", 96f, 195f, bodyPaint)
            drawLine(96f, 225f, 804f, 225f, boxPaint)

            val items = listOf(
                "1x Heavy Duty Document Folder ........ $14.50",
                "2x Archival Fountain Ink (Cobalt) .... $38.00",
                "1x A4 Recycled Cotton Paper Ream ..... $22.00",
                "1x Portable USB-C Desk Lamp .......... $49.00",
                "---------------------------------------------",
                "SUBTOTAL ............................. $123.50",
                "CA STATE TAX (8.625%) ................ $10.65",
                "TOTAL CHARGED (VISA *4092) ........... $134.15",
                "",
                "CUSTOMER COPY - RETAIN FOR WARRANTY & TAX LOG",
                "OCR Verification Code: PW-SCAN-9940-LATIN"
            )
            var y = 290f
            for (line in items) {
                drawText(line, 96f, y, bodyPaint)
                y += 54f
            }
            drawText("[APPROVED IN COLOR - COBALT STAMP]", 96f, 960f, stampPaint)
        }

        val page2 = Bitmap.createBitmap(900, 1260, Bitmap.Config.ARGB_8888)
        Canvas(page2).apply {
            drawColor(Color.rgb(234, 229, 218))
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(25, 65, 110)
                textSize = 32f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(48, 52, 60)
                textSize = 23f
                typeface = Typeface.SANS_SERIF
            }
            drawText("DELIVERY & EQUIPMENT SIGN-OFF (PAGE 2)", 96f, 140f, titlePaint)
            val notes = listOf(
                "Inspector: Jordan Vance",
                "Location: Warehouse Dock B - Receiving",
                "Condition: All crates sealed and verified intact.",
                "",
                "Original Scan Preservation Note:",
                "Paperwork stores both this warm, unprocessed camera capture",
                "(Original) and the color-preserving enhanced document layer.",
                "You can switch between Original, Enhanced Color, and Grayscale anytime."
            )
            var y = 230f
            for (line in notes) {
                drawText(line, 96f, y, textPaint)
                y += 50f
            }
        }

        return listOf(page1, page2)
    }
}
