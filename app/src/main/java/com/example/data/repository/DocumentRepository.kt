package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.core.image.SafeBitmapDecoder
import com.example.core.pdf.PageImageSpec
import com.example.core.pdf.PdfProcessingEngine
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.domain.model.DocumentType
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PdfQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Local document catalogue backed by Room; the document bytes themselves are published into
 * the user's Downloads collection through MediaStore. No cryptographic vault or private file
 * provider is involved.
 */
class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    val pdfEngine: PdfProcessingEngine
) {
    val allDocumentsFlow: Flow<List<PaperworkDocument>> = documentDao.observeAll().map { rows ->
        rows.map { it.toDocument() }
    }

    val storageBytesFlow: Flow<Long> = allDocumentsFlow.map { documents -> documents.sumOf { it.fileSize } }

    suspend fun savePages(images: List<Uri>, title: String, type: DocumentType,
        filter: com.example.domain.model.ScanFilterMode, format: com.example.domain.model.ScanOutputFormat,
        size: PageSizeOption, quality: PdfQuality): PaperworkDocument? = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "Add a page first." }
        val temp = File.createTempFile("export_", ".tmp", context.cacheDir)
        try {
            if (format == com.example.domain.model.ScanOutputFormat.PDF) {
                val pdf = android.graphics.pdf.PdfDocument()
                try {
                    images.forEachIndexed { index, uri ->
                        val original = SafeBitmapDecoder.decodeUriSafely(context, uri, quality.maxDimensionPx)
                            ?: error("Unable to read page ${index + 1}. Please add it again.")
                        val image = SafeBitmapDecoder.applyScanFilter(original, filter)
                        try {
                            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(size.widthPt, size.heightPt, index + 1).create())
                            page.canvas.drawColor(android.graphics.Color.WHITE)
                            val scale = minOf((size.widthPt - 36f) / image.width, (size.heightPt - 36f) / image.height)
                            val w = image.width * scale
                            val h = image.height * scale
                            val x = (size.widthPt - w) / 2f
                            val y = (size.heightPt - h) / 2f
                            page.canvas.drawBitmap(image, null, android.graphics.RectF(x, y, x + w, y + h),
                                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            pdf.finishPage(page)
                        } finally { if (image !== original) image.recycle(); original.recycle() }
                    }
                    temp.outputStream().use { pdf.writeTo(it) }
                } finally { pdf.close() }
            } else {
                java.util.zip.ZipOutputStream(temp.outputStream()).use { zip ->
                    images.forEachIndexed { index, uri ->
                        val original = SafeBitmapDecoder.decodeUriSafely(context, uri, quality.maxDimensionPx)
                            ?: error("Unable to read page ${index + 1}.")
                        val image = SafeBitmapDecoder.applyScanFilter(original, filter)
                        try {
                            val png = format == com.example.domain.model.ScanOutputFormat.PNG
                            zip.putNextEntry(java.util.zip.ZipEntry("page_${index + 1}.${if (png) "png" else "jpg"}"))
                            check(image.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG,
                                quality.jpegQuality, zip))
                            zip.closeEntry()
                        } finally { if (image !== original) image.recycle(); original.recycle() }
                    }
                }
            }
            val name = ensurePdfName(title).removeSuffix(".pdf") + "." + format.extension
            val uri = publishFile(name, format.mimeType, temp)
            if (format != com.example.domain.model.ScanOutputFormat.PDF) return@withContext null
            val now = System.currentTimeMillis()
            try {
                DocumentEntity(UUID.randomUUID().toString(), title.trim(), name, uri.toString(),
                    temp.length(), images.size, type.name, now, now).also { documentDao.upsert(it) }.toDocument()
            } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
        } finally { temp.delete() }
    }

    suspend fun importPdf(source: Uri): PaperworkDocument = withContext(Dispatchers.IO) {
        val originalName = resolveDisplayName(source).ifBlank { "Imported document.pdf" }
        val fileName = ensurePdfName(originalName)
        val temp = copyUriToCache(source, "inspect_${UUID.randomUUID()}.pdf")
        try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(temp).use { pdf ->
                require(pdf.numberOfPages > 0) { "This PDF has no pages." }
            }
            val inspection = pdfEngine.inspectPdfFile(temp)
            val output = publishFile(fileName, "application/pdf", temp)
            val now = System.currentTimeMillis()
            DocumentEntity(
                id = UUID.randomUUID().toString(),
                title = fileName.substringBeforeLast('.').ifBlank { "Imported document" },
                fileName = fileName,
                contentUri = output.toString(),
                fileSize = temp.length(),
                pageCount = inspection.pageCount.coerceAtLeast(1),
                documentType = DocumentType.PDF.name,
                createdAt = now,
                modifiedAt = now,
                nativePdfText = inspection.nativeExtractedText
            ).also {
                try { documentDao.upsert(it) }
                catch (e: Exception) { context.contentResolver.delete(output, null, null); throw e }
            }.toDocument()
        } finally {
            temp.delete()
        }
    }


    suspend fun duplicate(document: PaperworkDocument): PaperworkDocument = withContext(Dispatchers.IO) {
        val copyTitle = "${document.title} copy"
        val fileName = ensurePdfName(copyTitle)
        val temp = copyUriToCache(Uri.parse(document.contentUri), "duplicate_${UUID.randomUUID()}.pdf")
        try {
        val output = publishFile(fileName, "application/pdf", temp)
        val now = System.currentTimeMillis()
        DocumentEntity(
            id = UUID.randomUUID().toString(), title = copyTitle, fileName = fileName,
            contentUri = output.toString(), fileSize = temp.length(), pageCount = document.pageCount,
            documentType = document.documentType.name, createdAt = now, modifiedAt = now,
            nativePdfText = document.nativePdfText, ocrRecognizedText = document.ocrRecognizedText
        ).also {
            try { documentDao.upsert(it) }
            catch (e: Exception) { context.contentResolver.delete(output, null, null); throw e }
        }.toDocument()
        } finally { temp.delete() }
    }

    suspend fun rename(document: PaperworkDocument, newTitle: String) = withContext(Dispatchers.IO) {
        val title = newTitle.trim().ifBlank { document.title }
        val fileName = ensurePdfName(title)
        val uri = Uri.parse(document.contentUri)
        val updated = context.contentResolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        }, null, null)
        check(updated > 0) { "This file is no longer available. It may have been moved or deleted." }
        documentDao.rename(document.id, title, fileName, System.currentTimeMillis())
    }

    suspend fun delete(document: PaperworkDocument) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(Uri.parse(document.contentUri), null, null)
        documentDao.delete(document.id)
    }

    suspend fun updateLastOpenedPage(documentId: String, page: Int) {
        documentDao.updateLastOpenedPage(documentId, page, System.currentTimeMillis())
    }

    suspend fun copyToCache(document: PaperworkDocument, purpose: String): File = withContext(Dispatchers.IO) {
        copyUriToCache(Uri.parse(document.contentUri), "${purpose}_${document.id}_${UUID.randomUUID()}.pdf")
    }

    fun createShareIntent(document: PaperworkDocument): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(document.contentUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun publishFile(fileName: String, mimeType: String, source: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Paperwork")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Paperwork").apply { mkdirs() }
                val target = File(folder, "${fileName.substringBeforeLast('.')} ${UUID.randomUUID().toString().take(6)}.${fileName.substringAfterLast('.')}")
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, target.absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values) ?: error("Could not create a Downloads file.")
        return try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open the Downloads file.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun copyUriToCache(uri: Uri, name: String): File {
        val folder = File(context.cacheDir, "paperwork_preview").apply { mkdirs() }
        val target = File(folder, name)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(16384)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 100 * 1024 * 1024) { "Choose a PDF smaller than 100 MB." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Paperwork cannot read the selected file.")
            target
        } catch (e: Exception) { target.delete(); throw e }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }

    private fun ensurePdfName(value: String): String {
        val safe = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").take(80).ifBlank { "Document" }
        return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
    }

    private fun DocumentEntity.toDocument() = PaperworkDocument(
        id = id, title = title, fileName = fileName, contentUri = contentUri, fileSize = fileSize,
        pageCount = pageCount, documentType = runCatching { DocumentType.valueOf(documentType) }.getOrDefault(DocumentType.PDF),
        createdAt = createdAt, modifiedAt = modifiedAt, lastOpenedPage = lastOpenedPage,
        nativePdfText = nativePdfText, ocrRecognizedText = ocrRecognizedText
    )
}
