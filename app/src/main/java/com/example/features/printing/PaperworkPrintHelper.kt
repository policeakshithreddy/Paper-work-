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
import com.example.core.security.VaultCryptoManager
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PrintColorOption
import com.example.domain.model.PrintConfiguration
import com.example.domain.model.PrintDuplexOption
import com.example.domain.model.PrintPageOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Native Android Print Framework integration (`PrintManager` + `PrintDocumentAdapter`).
 *
 * Implements the architectural requirement for:
 * - Normal order (1 -> N)
 * - Reverse order (N -> 1)
 * - Custom Page Range
 * - Orientation (Portrait / Landscape)
 * - Paper Size (A4 / Letter / Legal)
 * - Color / Grayscale
 * - Duplex (Single-sided, Long-Edge, Short-Edge)
 *
 * When Reverse order or custom page ranges are selected, uses Apache `pdfbox-android`
 * (`PdfProcessingEngine.createReorderedOrFilteredPdf`) to construct a lossless temporary
 * print PDF preserving vector content and searchable text, and securely wipes the temporary
 * decrypted files inside `PrintDocumentAdapter.onFinish()`.
 */
class PaperworkPrintHelper(
    private val context: Context,
    private val cryptoManager: VaultCryptoManager,
    private val pdfEngine: PdfProcessingEngine
) {

    suspend fun prepareAndLaunchSystemPrint(
        activityContext: Context,
        document: PaperworkDocument,
        config: PrintConfiguration,
        onError: (String) -> Unit
    ) {
        val preparedFiles = withContext(Dispatchers.IO) {
            try {
                val encryptedFile = File(document.encryptedPdfPath)
                if (!encryptedFile.exists()) return@withContext null

                val rawDecryptedTemp = cryptoManager.materializeDecryptedTempFile(
                    sourceEncryptedFile = encryptedFile,
                    tempFileName = "print_src_${document.id}.pdf",
                    aad = document.id
                )

                val parsedRange = pdfEngine.parsePageRange(config.pageRangeText, document.pageCount)
                val needsCustomOrdering = config.pageOrder == PrintPageOrder.REVERSE ||
                    parsedRange.size != document.pageCount ||
                    config.pageRangeText.isNotBlank()

                if (needsCustomOrdering) {
                    val orderedTemp = File(
                        File(context.cacheDir, "active_session_tmp").apply { mkdirs() },
                        "print_ordered_${document.id}.pdf"
                    )
                    val outPageCount = pdfEngine.createReorderedOrFilteredPdf(
                        sourcePdfFile = rawDecryptedTemp,
                        targetPdfFile = orderedTemp,
                        reverseOrder = config.pageOrder == PrintPageOrder.REVERSE,
                        oneBasedPageNumbers = parsedRange
                    )
                    cryptoManager.secureDeleteFile(rawDecryptedTemp)
                    Pair(orderedTemp, outPageCount)
                } else {
                    Pair(rawDecryptedTemp, document.pageCount)
                }
            } catch (e: Exception) {
                null
            }
        }

        if (preparedFiles == null) {
            onError("Unable to decrypt and prepare document for printing.")
            return
        }

        val (printPdfFile, finalPageCount) = preparedFiles
        try {
            val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                cryptoManager.secureDeleteFile(printPdfFile)
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
                documentTitle = document.fileName.ifBlank { "${document.title}.pdf" },
                preparedPdfFile = printPdfFile,
                pageCount = finalPageCount,
                onCleanup = { cryptoManager.secureDeleteFile(printPdfFile) }
            )

            printManager.print(jobName, adapter, attributesBuilder.build())
        } catch (e: Exception) {
            cryptoManager.secureDeleteFile(printPdfFile)
            onError("Could not launch system print dialog: ${e.message}")
        }
    }
}

class PaperworkPrintDocumentAdapter(
    private val documentTitle: String,
    private val preparedPdfFile: File,
    private val pageCount: Int,
    private val onCleanup: () -> Unit
) : PrintDocumentAdapter() {

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

        val info = PrintDocumentInfo.Builder(documentTitle)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount.coerceAtLeast(1))
            .build()

        callback?.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Missing print destination descriptor.")
            return
        }

        try {
            FileInputStream(preparedPdfFile).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } >= 0) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onWriteCancelled()
                            return
                        }
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message ?: "Error writing PDF stream to print spooler.")
        }
    }

    override fun onFinish() {
        super.onFinish()
        onCleanup()
    }
}
