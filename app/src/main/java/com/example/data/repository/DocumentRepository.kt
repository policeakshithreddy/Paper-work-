package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.example.core.pdf.PageImageSpec
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.pdf.SecureDocumentAccessManager
import com.example.core.security.VaultCryptoManager
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.SensitiveDocumentMetadata
import com.example.domain.model.DocumentEncryptionStatus
import com.example.domain.model.DocumentType
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PdfQuality
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScanPageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Local-only privacy-first repository managing Paperwork's encrypted document vault.
 *
 * Security Guarantees:
 * - Every PDF file is stored AES-256-GCM encrypted in `/files/vault_pdfs/{id}.pdf.enc`
 *   with the 19-byte `PWKV` versioned binary header and a unique 12-byte random IV.
 * - Every scan page preserves BOTH its untouched original image (`/files/vault_scans/{pageId}_orig.jpg.enc`)
 *   and its enhanced image (`/files/vault_scans/{pageId}_enh.jpg.enc`) permanently until deleted.
 * - Every thumbnail is stored AES-256-GCM encrypted in `/files/vault_thumbs/{id}.jpg.enc` and
 *   cached only in an in-memory [LruCache] during the active session.
 * - Sensitive metadata (`title`, `fileName`, `nativePdfText`, `ocrRecognizedText`, `scanPages`)
 *   is encrypted inside `DocumentEntity.encryptedMetadataBlob` with `entity.id` bound as GCM AAD.
 * - If a previous Keystore key version (`v{N}`) was invalidated, documents encrypted with `v{N}`
 *   are honestly surfaced with [DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED] rather than
 *   silently failing or falsely claiming recovery.
 */
class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    val cryptoManager: VaultCryptoManager,
    val pdfEngine: PdfProcessingEngine,
    val secureDocumentAccessManager: SecureDocumentAccessManager = SecureDocumentAccessManager(context, cryptoManager)
) {

    private val vaultPdfDir = File(context.filesDir, "vault_pdfs").apply { mkdirs() }
    private val vaultScansDir = File(context.filesDir, "vault_scans").apply { mkdirs() }
    private val vaultThumbsDir = File(context.filesDir, "vault_thumbs").apply { mkdirs() }

    private val thumbnailMemoryCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    val allDocumentsFlow: Flow<List<PaperworkDocument>> = documentDao.observeAllDocuments().map { entities ->
        entities.map { entity -> decryptEntityOrSurfaceUnreadable(entity) }
    }

    private fun decryptEntityOrSurfaceUnreadable(entity: DocumentEntity): PaperworkDocument {
        val header = cryptoManager.inspectPayloadHeader(entity.encryptedMetadataBlob)
        val keyVer = header?.keyVersion ?: entity.keyVersion

        return try {
            val decryptedMetaBytes = cryptoManager.decryptBytes(
                encryptedPayload = entity.encryptedMetadataBlob,
                associatedData = entity.id.toByteArray(Charsets.UTF_8)
            )
            val meta = SensitiveDocumentMetadata.fromJsonBytes(decryptedMetaBytes)
            PaperworkDocument(
                id = entity.id,
                title = meta.title,
                fileName = meta.fileName,
                encryptedPdfPath = entity.encryptedPdfPath,
                encryptedThumbnailPath = entity.encryptedThumbnailPath,
                fileSize = entity.fileSize,
                pageCount = entity.pageCount,
                documentType = runCatching { DocumentType.valueOf(entity.documentType) }
                    .getOrDefault(DocumentType.PDF),
                createdAt = entity.createdAt,
                modifiedAt = entity.modifiedAt,
                lastOpenedPage = entity.lastOpenedPage,
                hasNativeTextLayer = entity.hasNativeTextLayer,
                nativePdfText = meta.nativePdfText,
                ocrRecognizedText = meta.ocrRecognizedText,
                scanPages = meta.scanPages,
                currentlyUsingEnhanced = entity.currentlyUsingEnhanced,
                activeScanFilterMode = meta.activeScanFilterMode,
                keyVersion = keyVer,
                encryptionStatus = DocumentEncryptionStatus.ACTIVE_READABLE,
                hasDigitalSignatures = meta.hasDigitalSignatures
            )
        } catch (_: Exception) {
            // Honestly surface unreadable v{N} records when their Keystore key was invalidated
            PaperworkDocument(
                id = entity.id,
                title = "Unreadable Document (Key v$keyVer Invalidated)",
                fileName = "unreadable_v${keyVer}_${entity.id.take(6)}.pdf",
                encryptedPdfPath = entity.encryptedPdfPath,
                encryptedThumbnailPath = entity.encryptedThumbnailPath,
                fileSize = entity.fileSize,
                pageCount = entity.pageCount,
                documentType = runCatching { DocumentType.valueOf(entity.documentType) }
                    .getOrDefault(DocumentType.PDF),
                createdAt = entity.createdAt,
                modifiedAt = entity.modifiedAt,
                lastOpenedPage = entity.lastOpenedPage,
                hasNativeTextLayer = entity.hasNativeTextLayer,
                nativePdfText = "",
                ocrRecognizedText = "",
                scanPages = emptyList(),
                currentlyUsingEnhanced = entity.currentlyUsingEnhanced,
                activeScanFilterMode = ScanFilterMode.ENHANCED_COLOR,
                keyVersion = keyVer,
                encryptionStatus = DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED,
                hasDigitalSignatures = false
            )
        }
    }

    suspend fun getDocumentById(id: String): PaperworkDocument? = withContext(Dispatchers.IO) {
        val entity = documentDao.getDocumentById(id) ?: return@withContext null
        decryptEntityOrSurfaceUnreadable(entity)
    }

    /**
     * Loads and decrypts a document's thumbnail into the in-memory LruCache.
     */
    suspend fun loadDecryptedThumbnail(document: PaperworkDocument): Bitmap? = withContext(Dispatchers.IO) {
        if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
            return@withContext null
        }
        val cacheKey = "${document.id}_${document.modifiedAt}"
        thumbnailMemoryCache.get(cacheKey)?.let { return@withContext it }

        val thumbPath = document.encryptedThumbnailPath ?: return@withContext null
        val thumbFile = File(thumbPath)
        if (!thumbFile.exists()) return@withContext null

        return@withContext try {
            val bytes = cryptoManager.decryptFromFile(thumbFile, aad = document.id)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                thumbnailMemoryCache.put(cacheKey, bmp)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a scanned page's Original or Enhanced bitmap on demand.
     */
    suspend fun loadScanPageBitmap(page: ScanPageRecord, useEnhanced: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            val path = if (useEnhanced) page.enhancedEncryptedPath else page.originalEncryptedPath
            val file = File(path)
            if (!file.exists()) return@withContext null
            try {
                val bytes = cryptoManager.decryptFromFile(file, aad = page.pageId)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Saves a newly scanned document into the encrypted vault, permanently preserving both
     * the untouched Original bitmaps and the Enhanced bitmaps for every page.
     */
    suspend fun saveScannedDocument(
        title: String,
        originalBitmaps: List<Bitmap>,
        enhancedBitmaps: List<Bitmap>,
        useEnhancedByDefault: Boolean,
        rotations: List<Int>,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        margin: MarginSize,
        quality: PdfQuality,
        filterMode: ScanFilterMode = if (useEnhancedByDefault) ScanFilterMode.ENHANCED_COLOR else ScanFilterMode.ORIGINAL
    ): PaperworkDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "Scan $now" }
        val fileName = if (cleanTitle.endsWith(".pdf", ignoreCase = true)) cleanTitle else "$cleanTitle.pdf"
        val activeKeyVer = cryptoManager.getCurrentKeyVersion()

        val scanRecords = mutableListOf<ScanPageRecord>()
        val pageSpecsForPdf = mutableListOf<PageImageSpec>()
        val ocrBuilder = StringBuilder()

        for (i in originalBitmaps.indices) {
            val pageId = "${docId}_p$i"
            val origBmp = originalBitmaps[i]
            val enhBmp = enhancedBitmaps.getOrElse(i) {
                pdfEngine.createEnhancedDocumentBitmap(
                    origBmp,
                    if (filterMode == ScanFilterMode.ORIGINAL) ScanFilterMode.ENHANCED_COLOR else filterMode
                )
            }
            val rot = rotations.getOrElse(i) { 0 }

            val origFile = File(vaultScansDir, "${pageId}_orig.jpg.enc")
            val enhFile = File(vaultScansDir, "${pageId}_enh.jpg.enc")

            cryptoManager.encryptToFile(
                plaintextBytes = bitmapToJpegBytes(origBmp, 94),
                targetEncryptedFile = origFile,
                aad = pageId
            )
            cryptoManager.encryptToFile(
                plaintextBytes = bitmapToJpegBytes(enhBmp, 92),
                targetEncryptedFile = enhFile,
                aad = pageId
            )

            scanRecords.add(
                ScanPageRecord(
                    pageId = pageId,
                    pageIndex = i,
                    originalEncryptedPath = origFile.absolutePath,
                    enhancedEncryptedPath = enhFile.absolutePath,
                    activeEnhanced = useEnhancedByDefault,
                    filterMode = filterMode,
                    rotationDegrees = rot
                )
            )

            val selectedBmp = if (useEnhancedByDefault) enhBmp else origBmp
            pageSpecsForPdf.add(PageImageSpec(selectedBmp, rot))

            // Run on-device OCR on the enhanced bitmap for searchable OCR indexing (separate from native PDF text)
            val recognized = pdfEngine.recognizeTextWithMlKit(
                pdfEngine.rotateBitmap(enhBmp, rot)
            )
            if (recognized.isNotBlank()) {
                ocrBuilder.append("--- Page ${i + 1} ---\n").append(recognized).append("\n\n")
            }
        }

        val pdfBytes = pdfEngine.buildPdfFromImages(
            pages = pageSpecsForPdf,
            pageSize = pageSize,
            orientation = orientation,
            margin = margin,
            quality = quality
        )

        val encryptedPdfFile = File(vaultPdfDir, "$docId.pdf.enc")
        cryptoManager.encryptToFile(pdfBytes, encryptedPdfFile, aad = docId)

        val firstBmp = pageSpecsForPdf.firstOrNull()?.let {
            pdfEngine.rotateBitmap(it.bitmap, it.rotationDegrees)
        }
        val encryptedThumbPath = firstBmp?.let { saveThumbnailEncrypted(docId, it) }

        val ocrText = ocrBuilder.toString().trim()
        val sensitiveMeta = SensitiveDocumentMetadata(
            title = cleanTitle.removeSuffix(".pdf"),
            fileName = fileName,
            nativePdfText = "",
            ocrRecognizedText = ocrText,
            scanPages = scanRecords,
            activeScanFilterMode = filterMode,
            hasDigitalSignatures = false
        )
        val encryptedMetaBlob = cryptoManager.encryptBytes(
            plaintext = sensitiveMeta.toJsonBytes(),
            associatedData = docId.toByteArray(Charsets.UTF_8)
        )

        val entity = DocumentEntity(
            id = docId,
            encryptedPdfPath = encryptedPdfFile.absolutePath,
            encryptedThumbnailPath = encryptedThumbPath,
            fileSize = pdfBytes.size.toLong(),
            pageCount = originalBitmaps.size.coerceAtLeast(1),
            documentType = DocumentType.SCANNED.name,
            createdAt = now,
            modifiedAt = now,
            lastOpenedPage = 0,
            hasNativeTextLayer = false,
            currentlyUsingEnhanced = useEnhancedByDefault,
            keyVersion = activeKeyVer,
            encryptedMetadataBlob = encryptedMetaBlob
        )
        documentDao.upsertDocument(entity)
        decryptEntityOrSurfaceUnreadable(entity)
    }

    /**
     * Reversibly switches an existing scanned document between its preserved Original images,
     * Enhanced Color, or Ink-Saving Grayscale without ever destroying the original scan.
     */
    suspend fun switchScanDocumentEnhancementMode(
        document: PaperworkDocument,
        useEnhanced: Boolean,
        filterMode: ScanFilterMode = if (useEnhanced) ScanFilterMode.ENHANCED_COLOR else ScanFilterMode.ORIGINAL,
        pageSize: PageSizeOption = PageSizeOption.A4,
        quality: PdfQuality = PdfQuality.BALANCED
    ): PaperworkDocument? = withContext(Dispatchers.IO) {
        if (document.scanPages.isEmpty()) return@withContext document

        val updatedRecords = mutableListOf<ScanPageRecord>()
        val pageSpecs = mutableListOf<PageImageSpec>()

        for (record in document.scanPages.sortedBy { it.pageIndex }) {
            val origBmp = loadScanPageBitmap(record, useEnhanced = false) ?: continue
            val targetBmp = when (filterMode) {
                ScanFilterMode.ORIGINAL -> origBmp
                ScanFilterMode.ENHANCED_COLOR -> pdfEngine.createEnhancedDocumentBitmap(
                    origBmp,
                    ScanFilterMode.ENHANCED_COLOR
                )
                ScanFilterMode.INK_SAVING_GRAYSCALE -> pdfEngine.createEnhancedDocumentBitmap(
                    origBmp,
                    ScanFilterMode.INK_SAVING_GRAYSCALE
                )
            }
            pageSpecs.add(PageImageSpec(targetBmp, record.rotationDegrees))
            updatedRecords.add(
                record.copy(
                    activeEnhanced = filterMode != ScanFilterMode.ORIGINAL,
                    filterMode = filterMode
                )
            )
        }
        if (pageSpecs.isEmpty()) return@withContext document

        val pdfBytes = pdfEngine.buildPdfFromImages(
            pages = pageSpecs,
            pageSize = pageSize,
            orientation = PageOrientation.PORTRAIT,
            margin = MarginSize.SMALL,
            quality = quality
        )

        val encryptedPdfFile = File(document.encryptedPdfPath)
        cryptoManager.encryptToFile(pdfBytes, encryptedPdfFile, aad = document.id)

        val firstBmp = pageSpecs.firstOrNull()?.let {
            pdfEngine.rotateBitmap(it.bitmap, it.rotationDegrees)
        }
        val thumbPath = firstBmp?.let { saveThumbnailEncrypted(document.id, it) }
            ?: document.encryptedThumbnailPath

        val now = System.currentTimeMillis()
        val sensitiveMeta = SensitiveDocumentMetadata(
            title = document.title,
            fileName = document.fileName,
            nativePdfText = document.nativePdfText,
            ocrRecognizedText = document.ocrRecognizedText,
            scanPages = updatedRecords,
            activeScanFilterMode = filterMode,
            hasDigitalSignatures = document.hasDigitalSignatures
        )
        val encryptedMetaBlob = cryptoManager.encryptBytes(
            plaintext = sensitiveMeta.toJsonBytes(),
            associatedData = document.id.toByteArray(Charsets.UTF_8)
        )

        val updatedEntity = DocumentEntity(
            id = document.id,
            encryptedPdfPath = encryptedPdfFile.absolutePath,
            encryptedThumbnailPath = thumbPath,
            fileSize = pdfBytes.size.toLong(),
            pageCount = pageSpecs.size,
            documentType = document.documentType.name,
            createdAt = document.createdAt,
            modifiedAt = now,
            lastOpenedPage = document.lastOpenedPage,
            hasNativeTextLayer = document.hasNativeTextLayer,
            currentlyUsingEnhanced = filterMode != ScanFilterMode.ORIGINAL,
            keyVersion = cryptoManager.getCurrentKeyVersion(),
            encryptedMetadataBlob = encryptedMetaBlob
        )
        documentDao.upsertDocument(updatedEntity)
        decryptEntityOrSurfaceUnreadable(updatedEntity)
    }

    /**
     * Converts multiple imported images into a multi-page encrypted PDF in the vault.
     */
    suspend fun createPdfFromImages(
        title: String,
        specs: List<PageImageSpec>,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        margin: MarginSize,
        quality: PdfQuality,
        runOcrIndexing: Boolean = true
    ): PaperworkDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { "Converted PDF $now" }
        val fileName = if (cleanTitle.endsWith(".pdf", ignoreCase = true)) cleanTitle else "$cleanTitle.pdf"

        val pdfBytes = pdfEngine.buildPdfFromImages(
            pages = specs,
            pageSize = pageSize,
            orientation = orientation,
            margin = margin,
            quality = quality
        )

        val encryptedPdfFile = File(vaultPdfDir, "$docId.pdf.enc")
        cryptoManager.encryptToFile(pdfBytes, encryptedPdfFile, aad = docId)

        val firstBmp = specs.firstOrNull()?.let { pdfEngine.rotateBitmap(it.bitmap, it.rotationDegrees) }
        val thumbPath = firstBmp?.let { saveThumbnailEncrypted(docId, it) }

        val ocrBuilder = StringBuilder()
        if (runOcrIndexing) {
            specs.forEachIndexed { idx, spec ->
                val rotated = pdfEngine.rotateBitmap(spec.bitmap, spec.rotationDegrees)
                val text = pdfEngine.recognizeTextWithMlKit(rotated)
                if (text.isNotBlank()) {
                    ocrBuilder.append("--- Page ${idx + 1} ---\n").append(text).append("\n\n")
                }
            }
        }

        val sensitiveMeta = SensitiveDocumentMetadata(
            title = cleanTitle.removeSuffix(".pdf"),
            fileName = fileName,
            nativePdfText = "",
            ocrRecognizedText = ocrBuilder.toString().trim(),
            scanPages = emptyList(),
            activeScanFilterMode = ScanFilterMode.ORIGINAL,
            hasDigitalSignatures = false
        )
        val encryptedMetaBlob = cryptoManager.encryptBytes(
            plaintext = sensitiveMeta.toJsonBytes(),
            associatedData = docId.toByteArray(Charsets.UTF_8)
        )

        val entity = DocumentEntity(
            id = docId,
            encryptedPdfPath = encryptedPdfFile.absolutePath,
            encryptedThumbnailPath = thumbPath,
            fileSize = pdfBytes.size.toLong(),
            pageCount = specs.size.coerceAtLeast(1),
            documentType = DocumentType.CONVERTED.name,
            createdAt = now,
            modifiedAt = now,
            lastOpenedPage = 0,
            hasNativeTextLayer = false,
            currentlyUsingEnhanced = true,
            keyVersion = cryptoManager.getCurrentKeyVersion(),
            encryptedMetadataBlob = encryptedMetaBlob
        )
        documentDao.upsertDocument(entity)
        decryptEntityOrSurfaceUnreadable(entity)
    }

    /**
     * Imports an external PDF from a content Uri into the Keystore-encrypted vault.
     */
    suspend fun importExternalPdfUri(uri: Uri, suggestedDisplayName: String?): PaperworkDocument? =
        withContext(Dispatchers.IO) {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            if (rawBytes.isEmpty()) return@withContext null
            importRawPdfBytes(
                rawPdfBytes = rawBytes,
                displayName = suggestedDisplayName ?: "Imported_Document.pdf",
                documentType = DocumentType.PDF
            )
        }

    suspend fun importRawPdfBytes(
        rawPdfBytes: ByteArray,
        displayName: String,
        documentType: DocumentType = DocumentType.PDF
    ): PaperworkDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val cleanName = displayName.trim().ifBlank { "Document_$now.pdf" }
        val title = cleanName.removeSuffix(".pdf").removeSuffix(".PDF")
        val fileName = if (cleanName.endsWith(".pdf", ignoreCase = true)) cleanName else "$cleanName.pdf"

        val tempInspectFile = File(
            File(context.cacheDir, "active_session_tmp").apply { mkdirs() },
            "inspect_$docId.pdf"
        )
        val inspection = try {
            tempInspectFile.writeBytes(rawPdfBytes)
            pdfEngine.inspectPdfFile(tempInspectFile)
        } finally {
            cryptoManager.deleteFileSafely(tempInspectFile)
        }

        val encryptedPdfFile = File(vaultPdfDir, "$docId.pdf.enc")
        cryptoManager.encryptToFile(rawPdfBytes, encryptedPdfFile, aad = docId)

        val thumbPath = inspection.firstPageThumbnail?.let { saveThumbnailEncrypted(docId, it) }

        val sensitiveMeta = SensitiveDocumentMetadata(
            title = title,
            fileName = fileName,
            nativePdfText = inspection.nativeExtractedText,
            ocrRecognizedText = "",
            scanPages = emptyList(),
            activeScanFilterMode = ScanFilterMode.ORIGINAL,
            hasDigitalSignatures = inspection.hasDigitalSignatures
        )
        val encryptedMetaBlob = cryptoManager.encryptBytes(
            plaintext = sensitiveMeta.toJsonBytes(),
            associatedData = docId.toByteArray(Charsets.UTF_8)
        )

        val entity = DocumentEntity(
            id = docId,
            encryptedPdfPath = encryptedPdfFile.absolutePath,
            encryptedThumbnailPath = thumbPath,
            fileSize = rawPdfBytes.size.toLong(),
            pageCount = inspection.pageCount,
            documentType = documentType.name,
            createdAt = now,
            modifiedAt = now,
            lastOpenedPage = 0,
            hasNativeTextLayer = inspection.hasNativeTextLayer,
            currentlyUsingEnhanced = true,
            keyVersion = cryptoManager.getCurrentKeyVersion(),
            encryptedMetadataBlob = encryptedMetaBlob
        )
        documentDao.upsertDocument(entity)
        decryptEntityOrSurfaceUnreadable(entity)
    }

    suspend fun renameDocument(document: PaperworkDocument, newTitle: String): PaperworkDocument? =
        withContext(Dispatchers.IO) {
            if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
                return@withContext document
            }
            val clean = newTitle.trim().ifBlank { return@withContext document }
            val newFileName = if (clean.endsWith(".pdf", ignoreCase = true)) clean else "$clean.pdf"
            val now = System.currentTimeMillis()

            val meta = SensitiveDocumentMetadata(
                title = clean.removeSuffix(".pdf"),
                fileName = newFileName,
                nativePdfText = document.nativePdfText,
                ocrRecognizedText = document.ocrRecognizedText,
                scanPages = document.scanPages,
                activeScanFilterMode = document.activeScanFilterMode,
                hasDigitalSignatures = document.hasDigitalSignatures
            )
            val encryptedBlob = cryptoManager.encryptBytes(
                plaintext = meta.toJsonBytes(),
                associatedData = document.id.toByteArray(Charsets.UTF_8)
            )
            val entity = DocumentEntity(
                id = document.id,
                encryptedPdfPath = document.encryptedPdfPath,
                encryptedThumbnailPath = document.encryptedThumbnailPath,
                fileSize = document.fileSize,
                pageCount = document.pageCount,
                documentType = document.documentType.name,
                createdAt = document.createdAt,
                modifiedAt = now,
                lastOpenedPage = document.lastOpenedPage,
                hasNativeTextLayer = document.hasNativeTextLayer,
                currentlyUsingEnhanced = document.currentlyUsingEnhanced,
                keyVersion = cryptoManager.getCurrentKeyVersion(),
                encryptedMetadataBlob = encryptedBlob
            )
            documentDao.upsertDocument(entity)
            decryptEntityOrSurfaceUnreadable(entity)
        }

    suspend fun updateExtractedOcrText(document: PaperworkDocument, newOcrText: String): PaperworkDocument? =
        withContext(Dispatchers.IO) {
            if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
                return@withContext document
            }
            val meta = SensitiveDocumentMetadata(
                title = document.title,
                fileName = document.fileName,
                nativePdfText = document.nativePdfText,
                ocrRecognizedText = newOcrText,
                scanPages = document.scanPages,
                activeScanFilterMode = document.activeScanFilterMode,
                hasDigitalSignatures = document.hasDigitalSignatures
            )
            val encryptedBlob = cryptoManager.encryptBytes(
                plaintext = meta.toJsonBytes(),
                associatedData = document.id.toByteArray(Charsets.UTF_8)
            )
            val entity = DocumentEntity(
                id = document.id,
                encryptedPdfPath = document.encryptedPdfPath,
                encryptedThumbnailPath = document.encryptedThumbnailPath,
                fileSize = document.fileSize,
                pageCount = document.pageCount,
                documentType = document.documentType.name,
                createdAt = document.createdAt,
                modifiedAt = System.currentTimeMillis(),
                lastOpenedPage = document.lastOpenedPage,
                hasNativeTextLayer = document.hasNativeTextLayer,
                currentlyUsingEnhanced = document.currentlyUsingEnhanced,
                keyVersion = cryptoManager.getCurrentKeyVersion(),
                encryptedMetadataBlob = encryptedBlob
            )
            documentDao.upsertDocument(entity)
            decryptEntityOrSurfaceUnreadable(entity)
        }

    suspend fun duplicateDocument(document: PaperworkDocument): PaperworkDocument? =
        withContext(Dispatchers.IO) {
            if (document.encryptionStatus == DocumentEncryptionStatus.UNREADABLE_KEY_INVALIDATED) {
                return@withContext null
            }
            val srcFile = File(document.encryptedPdfPath)
            if (!srcFile.exists()) return@withContext null
            val decryptedBytes = cryptoManager.decryptFromFile(srcFile, aad = document.id)
            importRawPdfBytes(
                rawPdfBytes = decryptedBytes,
                displayName = "${document.title} (Copy).pdf",
                documentType = document.documentType
            )
        }

    suspend fun updateLastOpenedPage(documentId: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        documentDao.updateLastOpenedPage(documentId, pageIndex, System.currentTimeMillis())
    }

    /**
     * Deletes the encrypted PDF, thumbnail, and all preserved Original & Enhanced
     * scan bitmaps for a document.
     */
    suspend fun deleteDocument(document: PaperworkDocument) = withContext(Dispatchers.IO) {
        cryptoManager.deleteFileSafely(File(document.encryptedPdfPath))
        document.encryptedThumbnailPath?.let { cryptoManager.deleteFileSafely(File(it)) }
        document.scanPages.forEach { page ->
            cryptoManager.deleteFileSafely(File(page.originalEncryptedPath))
            cryptoManager.deleteFileSafely(File(page.enhancedEncryptedPath))
        }
        documentDao.deleteDocumentById(document.id)
    }

    /**
     * Purges unreadable documents encrypted under an invalidated key version (`v{N}`)
     * after the user explicitly confirms creating a new replacement key (`v{N+1}`).
     */
    suspend fun purgeUnreadableOldKeyDocuments(activeKeyVersion: Int) = withContext(Dispatchers.IO) {
        documentDao.deleteDocumentsWithOldKeyVersions(activeKeyVersion)
    }

    /**
     * Generates two realistic sample documents on user request:
     * 1. A 3-page Vector PDF (`Paperwork Architecture & Invoice Guide.pdf`) with native selectable text.
     * 2. A 2-page Scanned Document (`Pacific Hardware Receipt (Scanned).pdf`) preserving both warm
     *    Original camera captures and crisp Enhanced Color pages so Original/Enhanced toggling and ML Kit OCR
     *    can be tested immediately.
     */
    suspend fun generateSampleDocuments() = withContext(Dispatchers.IO) {
        val tempVectorFile = File(
            File(context.cacheDir, "active_session_tmp").apply { mkdirs() },
            "sample_vector.pdf"
        )
        try {
            pdfEngine.createSampleVectorPdf(tempVectorFile)
            importRawPdfBytes(
                rawPdfBytes = tempVectorFile.readBytes(),
                displayName = "Paperwork Architecture & Invoice Guide.pdf",
                documentType = DocumentType.PDF
            )
        } finally {
            cryptoManager.deleteFileSafely(tempVectorFile)
        }

        val originalBitmaps = pdfEngine.createSampleScannedBitmaps()
        val enhancedBitmaps = originalBitmaps.map {
            pdfEngine.createEnhancedDocumentBitmap(it, ScanFilterMode.ENHANCED_COLOR)
        }
        saveScannedDocument(
            title = "Pacific Hardware Receipt (Scanned)",
            originalBitmaps = originalBitmaps,
            enhancedBitmaps = enhancedBitmaps,
            useEnhancedByDefault = true,
            rotations = listOf(0, 0),
            pageSize = PageSizeOption.A4,
            orientation = PageOrientation.PORTRAIT,
            margin = MarginSize.SMALL,
            quality = PdfQuality.BALANCED,
            filterMode = ScanFilterMode.ENHANCED_COLOR
        )
    }

    fun calculateVaultStorageBytes(): Long {
        val pdfBytes = vaultPdfDir.listFiles()?.sumOf { it.length() } ?: 0L
        val scanBytes = vaultScansDir.listFiles()?.sumOf { it.length() } ?: 0L
        val thumbBytes = vaultThumbsDir.listFiles()?.sumOf { it.length() } ?: 0L
        return pdfBytes + scanBytes + thumbBytes
    }

    private fun saveThumbnailEncrypted(docId: String, bitmap: Bitmap): String {
        val thumbRatio = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
        val thumbW = 320
        val thumbH = (thumbW * thumbRatio).toInt().coerceIn(200, 480)
        val scaled = Bitmap.createScaledBitmap(bitmap, thumbW, thumbH, true)
        val jpegBytes = bitmapToJpegBytes(scaled, 82)
        val thumbFile = File(vaultThumbsDir, "$docId.jpg.enc")
        cryptoManager.encryptToFile(jpegBytes, thumbFile, aad = docId)
        return thumbFile.absolutePath
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), out)
        return out.toByteArray()
    }
}
