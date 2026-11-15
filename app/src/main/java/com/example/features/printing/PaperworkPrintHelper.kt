package com.example.features.printing

import android.app.Activity
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import com.example.core.pdf.PdfProcessingEngine
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

fun launchPrint(activity: Activity, file: File, name: String, pageCount: Int, engine: PdfProcessingEngine) {
    val manager = activity.getSystemService(Activity.PRINT_SERVICE) as PrintManager
    manager.print(name, object : PrintDocumentAdapter() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, signal: CancellationSignal,
            callback: LayoutResultCallback, extras: Bundle?) {
            if (signal.isCanceled) callback.onLayoutCancelled()
            else callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setPageCount(pageCount)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), old != new)
        }
        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
            signal: CancellationSignal, callback: WriteResultCallback) {
            scope.launch {
                val slice = File.createTempFile("print_pages_", ".pdf", activity.cacheDir)
                try {
                    val selected = (0 until pageCount).filter { page -> pages.any { page in it.start..it.end } }
                    if (selected.isEmpty()) { callback.onWriteFailed("No pages selected."); return@launch }
                    val source = if (selected.size == pageCount) file else {
                        engine.createReorderedOrFilteredPdf(file, slice, false, selected.map { it + 1 }); slice
                    }
                    source.inputStream().use { input ->
                        FileOutputStream(destination.fileDescriptor).use { out ->
                            val buffer = ByteArray(16384)
                            while (true) {
                                if (signal.isCanceled) { callback.onWriteCancelled(); return@launch }
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                    callback.onWriteFinished(pages)
                } catch (e: Exception) { callback.onWriteFailed(e.message ?: "Unable to print.") }
                finally { slice.delete(); destination.close() }
            }
        }
        override fun onFinish() { scope.cancel(); file.delete() }
    }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}
