package com.example.features.printing

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.pdf.SecureDocumentAccessManager
import com.example.core.security.VaultCryptoManager
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PrintColorOption
import com.example.domain.model.PrintConfiguration
import com.example.domain.model.PrintDuplexOption
import com.example.domain.model.PrintPageOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Android Print Framework integration (`PrintManager` + `PrintDocumentAdapter`).
 *
 * Implements:
 * - Normal order (1 -> N)
 * - Reverse order (N -> 1): sends the reversed page sequence (`Page N` down to `Page 1`)
 *   to Android's print service via non-rasterizing `pdfbox-android` page reordering.
 * - Custom Page Range
 * - Orientation (Portrait / Landscape), Paper Size (A4 / Letter / Legal), Color / Grayscale, Duplex
 * - Resilient handling of repeated `onLayout()` and `onWrite()` requests, `PageRange[]` filtering,
 *   `CancellationSignal` callbacks, and file-descriptor cleanup.
 * - Keeps the temporary decrypted print PDF available until `PrintDocumentAdapter.onFinish()`
 *   is invoked by Android's print spooler (both on completion and on dialog cancellation).
 */
class PaperworkPrintHelper(
    private val context: Context,
    private val cryptoManager: VaultCryptoManager,
    private val pdfEngine: PdfProcessingEngine,
    private val secureAccessManager: SecureDocumentAccessManager = SecureDocumentAccessManager(context, cryptoManager)
) {

    suspend fun prepareAndLaunchSystemPrint(
        activityContext: Context,
        document: PaperworkDocument,
        config: PrintConfiguration,
        onError: (String) -> Unit
    ) {
        if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
            onError("This document was encrypted with an invalidated Keystore key and cannot be decrypted for printing.")
            return
        }

        val preparedFiles = withContext(Dispatchers.IO) {
            val session = secureAccessManager.openDecryptedSession(
                document = document,
                purpose = "print_src"
            ).getOrNull() ?: return@withContext null

            try {
                val parsedRange = pdfEngine.parsePageRange(config.pageRangeText, document.pageCount)
                val needsCustomOrdering = config.pageOrder == PrintPageOrder.REVERSE ||
                    parsedRange.size != document.pageCount ||
                    config.pageRangeText.isNotBlank()

                if (needsCustomOrdering) {
                    val orderedTemp = File(
                        File(context.cacheDir, "active_session_tmp").apply { mkdirs() },
                        "print_ordered_${document.id}_${System.nanoTime()}.pdf"
                    )
                    val outPageCount = pdfEngine.createReorderedOrFilteredPdf(
                        sourcePdfFile = session.decryptedFile,
                        targetPdfFile = orderedTemp,
                        reverseOrder = config.pageOrder == PrintPageOrder.REVERSE,
                        oneBasedPageNumbers = parsedRange
                    )
                    // Close initial source handle now that orderedTemp has been validated
                    session.close()
                    Triple(orderedTemp, outPageCount, null)
                } else {
                    Triple(session.decryptedFile, document.pageCount, session)
                }
            } catch (e: Exception) {
                session.close()
                null
            }
        }

        if (preparedFiles == null) {
            onError("Unable to decrypt and prepare document for printing.")
            return
        }

        val (printPdfFile, finalPageCount, activeSessionHandle) = preparedFiles
        try {
            val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                activeSessionHandle?.close()
                cryptoManager.deleteFileSafely(printPdfFile)
                onError("Android Print Service is unavailable on this device.")
                return
            }

            val baseMediaSize = when (config.pageSize) {
                PageSizeOption.A4 -> PrintAttributes.MediaSize.ISO_A4
                PageSizeOption.LETTER -> PrintAttributes.MediaSize.NA_LETTER
                PageSizeOption.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
            }
            val mediaSize = if (config.orientation == PageOrientation.LANDSCAPE) {
                baseMediaSize.asLandscape()
            } else {
                baseMediaSize.asPortrait()
            }

            val attributesBuilder = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setColorMode(
                    when (config.colorOption) {
                        PrintColorOption.COLOR -> PrintAttributes.COLOR_MODE_COLOR
                        PrintColorOption.GRAYSCALE -> PrintAttributes.COLOR_MODE_MONOCHROME
                    }
                )
                .setDuplexMode(
                    when (config.duplexOption) {
                        PrintDuplexOption.NONE -> PrintAttributes.DUPLEX_MODE_NONE
                        PrintDuplexOption.LONG_EDGE -> PrintAttributes.DUPLEX_MODE_LONG_EDGE
                        PrintDuplexOption.SHORT_EDGE -> PrintAttributes.DUPLEX_MODE_SHORT_EDGE
                    }
                )

            val jobName = "Paperwork_${document.title.take(24)}"
            val adapter = PaperworkPrintDocumentAdapter(
                context = context.applicationContext,
                documentTitle = document.fileName.ifBlank { "${document.title}.pdf" },
                preparedPdfFile = printPdfFile,
                pageCount = finalPageCount,
                pdfEngine = pdfEngine,
                cryptoManager = cryptoManager,
                onCleanup = {
                    activeSessionHandle?.close()
                    cryptoManager.deleteFileSafely(printPdfFile)
                }
            )

            printManager.print(jobName, adapter, attributesBuilder.build())
        } catch (e: Exception) {
            activeSessionHandle?.close()
            cryptoManager.deleteFileSafely(printPdfFile)
            onError("Could not launch system print dialog: ${e.message}")
        }
    }
}

/**
 * Resilient [PrintDocumentAdapter] supporting:
 * - Repeated `onLayout()` and `onWrite()` calls as users change attributes in the system print UI
 * - Immediate cancellation via `CancellationSignal.setOnCancelListener`
 * - System-requested `PageRange[]` subset extraction
 * - Deterministic cleanup of all temporary print files only inside [onFinish]
 */
class PaperworkPrintDocumentAdapter(
    private val context: Context,
    private val documentTitle: String,
    private val preparedPdfFile: File,
    private val pageCount: Int,
    private val pdfEngine: PdfProcessingEngine,
    private val cryptoManager: VaultCryptoManager,
    private val onCleanup: () -> Unit
) : PrintDocumentAdapter() {

    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isFinished = AtomicBoolean(false)
    private var lastAttributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        cancellationSignal?.setOnCancelListener {
            callback?.onLayoutCancelled()
        }

        if (!preparedPdfFile.exists() || preparedPdfFile.length() == 0L) {
            callback?.onLayoutFailed("Prepared print PDF file is no longer available.")
            return
        }

        val layoutChanged = oldAttributes != newAttributes || lastAttributes != newAttributes
        lastAttributes = newAttributes

        val info = PrintDocumentInfo.Builder(documentTitle)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount.coerceAtLeast(1))
            .build()

        callback?.onLayoutFinished(info, layoutChanged)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Missing print destination file descriptor.")
            return
        }
        if (cancellationSignal?.isCanceled == true) {
            runCatching { destination.close() }
            callback?.onWriteCancelled()
            return
        }

        adapterScope.launch {
            var sliceTempFile: File? = null
            try {
                val requestedOneBasedPages = expandRequestedPageRanges(pages, pageCount)
                val needsSubsetSlice = requestedOneBasedPages != null &&
                    requestedOneBasedPages.size in 1 until pageCount

                val fileToStream = if (needsSubsetSlice && requestedOneBasedPages != null) {
                    val tempSlice = File(
                        File(context.cacheDir, "active_session_tmp").apply { mkdirs() },
                        "print_slice_${System.nanoTime()}.pdf"
                    )
                    sliceTempFile = tempSlice
                    pdfEngine.createReorderedOrFilteredPdf(
                        sourcePdfFile = preparedPdfFile,
                        targetPdfFile = tempSlice,
                        reverseOrder = false,
                        oneBasedPageNumbers = requestedOneBasedPages
                    )
                    tempSlice
                } else {
                    preparedPdfFile
                }

                FileInputStream(fileToStream).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return@launch
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }

                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                } else {
                    val writtenRanges = if (pages.isNullOrEmpty()) {
                        arrayOf(PageRange.ALL_PAGES)
                    } else {
                        pages
                    }
                    callback?.onWriteFinished(writtenRanges)
                }
            } catch (e: Exception) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                } else {
                    callback?.onWriteFailed(e.message ?: "Error writing PDF stream to print spooler.")
                }
            } finally {
                sliceTempFile?.let { cryptoManager.deleteFileSafely(it) }
                runCatching { destination.close() }
            }
        }
    }

    override fun onFinish() {
        super.onFinish()
        if (isFinished.compareAndSet(false, true)) {
            adapterScope.cancel()
            onCleanup()
        }
    }

    private fun expandRequestedPageRanges(
        ranges: Array<out PageRange>?,
        totalPages: Int
    ): List<Int>? {
        if (ranges.isNullOrEmpty()) return null
        if (ranges.any { it == PageRange.ALL_PAGES }) return null
        val list = mutableListOf<Int>()
        for (range in ranges) {
            val start = (range.start + 1).coerceAtLeast(1)
            val end = (range.end + 1).coerceAtMost(totalPages)
            for (p in start..end) {
                if (!list.contains(p)) list.add(p)
            }
        }
        return if (list.isEmpty() || list.size >= totalPages) null else list
    }
}
