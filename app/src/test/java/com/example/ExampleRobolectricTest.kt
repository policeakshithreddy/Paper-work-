package com.example

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.core.image.NormalizedQuadCorners
import com.example.core.image.SafeBitmapDecoder
import com.example.core.pdf.PageImageSpec
import com.example.core.pdf.PdfIntegrityValidator
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.pdf.SecureDocumentAccessManager
import com.example.core.security.VaultCryptoManager
import com.example.core.security.VaultKeyState
import com.example.data.local.SensitiveDocumentMetadata
import com.example.domain.model.DocumentType
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PaperworkDocument
import com.example.domain.model.PdfQuality
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScanPageRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `verify app name is Paperwork and MainActivity launches cleanly without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Paperwork", appName)

        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            assertNotNull(activity)
            assertFalse(activity.isFinishing)
        }
    }

    @Test
    fun `verify 19-byte PWKV versioned header, 100 unique 12-byte IVs, and GCM AAD tamper rejection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)

        val secretPayload = "Sensitive Contract #PW-90210 & OCR Text Cache".toByteArray(Charsets.UTF_8)
        val aad = "doc_uuid_12345".toByteArray(Charsets.UTF_8)

        val uniqueIvs = mutableSetOf<String>()
        var lastEncrypted = ByteArray(0)
        repeat(100) {
            val enc = crypto.encryptBytes(secretPayload, aad)
            val header = crypto.inspectPayloadHeader(enc)
            assertNotNull("Encrypted payload must start with PWKV header", header)
            assertEquals(1, header!!.formatVersion)
            assertEquals(12, header.iv.size)
            uniqueIvs.add(header.iv.joinToString(",") { b -> b.toString() })
            lastEncrypted = enc
        }
        assertEquals("100 consecutive encryptions must produce 100 unique 12-byte IVs", 100, uniqueIvs.size)

        val decrypted = crypto.decryptBytes(lastEncrypted, aad)
        assertArrayEquals(secretPayload, decrypted)

        // Verify GCM Associated Authenticated Data (AAD) rejects row-swapping tampering
        var tamperDetected = false
        try {
            crypto.decryptBytes(lastEncrypted, "wrong_doc_uuid".toByteArray(Charsets.UTF_8))
        } catch (_: SecurityException) {
            tamperDetected = true
        }
        assertTrue("Expected SecurityException on mismatched GCM AAD", tamperDetected)
    }

    @Test
    fun `verify 4-state VaultKeyState lifecycle and honest key version invalidation behavior`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)

        assertEquals(VaultKeyState.ACTIVE, crypto.vaultKeyState.value)
        val initialKeyVer = crypto.getCurrentKeyVersion()
        val oldKeyPayload = crypto.encryptBytes("Encrypted under initial key".toByteArray(), "doc_1".toByteArray())

        // Simulate key invalidation -> recovery required -> new key created -> active
        crypto.markKeyInvalidatedForTesting()
        assertEquals(VaultKeyState.INVALIDATED, crypto.vaultKeyState.value)

        crypto.transitionToRecoveryRequired()
        assertEquals(VaultKeyState.RECOVERY_REQUIRED, crypto.vaultKeyState.value)

        val newKeyVer = crypto.createReplacementKeyAfterInvalidation()
        assertEquals(initialKeyVer + 1, newKeyVer)
        assertEquals(VaultKeyState.NEW_KEY_CREATED, crypto.vaultKeyState.value)

        crypto.acknowledgeNewKeyCreated()
        assertEquals(VaultKeyState.ACTIVE, crypto.vaultKeyState.value)

        // Old ciphertext encrypted under v{N} must NOT claim false recovery under v{N+1}
        var oldDataUnreadable = false
        try {
            crypto.decryptBytes(oldKeyPayload, "doc_1".toByteArray())
        } catch (_: SecurityException) {
            oldDataUnreadable = true
        }
        assertTrue("Ciphertext from invalidated key version must be flagged unreadable", oldDataUnreadable)

        // New ciphertext encrypted under v{N+1} works cleanly
        val newPayload = crypto.encryptBytes("Encrypted under new key".toByteArray(), "doc_2".toByteArray())
        assertEquals(
            "Encrypted under new key",
            String(crypto.decryptBytes(newPayload, "doc_2".toByteArray()), Charsets.UTF_8)
        )
    }

    @Test
    fun `verify separated salted PBKDF2 PIN verification and exponential lockout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)

        val salt = crypto.generatePinSalt()
        assertEquals(16, salt.size)

        val hash = crypto.hashPin("4829", salt)
        assertTrue(crypto.verifyPin("4829", salt, hash))
        assertFalse(crypto.verifyPin("1111", salt, hash))

        assertEquals(0L, crypto.calculateLockoutDurationMs(4))
        assertEquals(30_000L, crypto.calculateLockoutDurationMs(5))
        assertEquals(120_000L, crypto.calculateLockoutDurationMs(6))
        assertEquals(600_000L, crypto.calculateLockoutDurationMs(9))
    }

    @Test
    fun `verify separated nativePdfText and ocrRecognizedText in SensitiveDocumentMetadata and DecryptedPdfSessionHandle lifecycle`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)
        val pdfEngine = PdfProcessingEngine(context)
        val accessManager = SecureDocumentAccessManager(context, crypto)

        val metadata = SensitiveDocumentMetadata(
            title = "Tax Return 2026",
            fileName = "Tax_Return_2026.pdf",
            nativePdfText = "Embedded Vector Form 1040 Stream",
            ocrRecognizedText = "ML Kit OCR Recognized Text",
            scanPages = listOf(
                ScanPageRecord(
                    pageId = "p1",
                    pageIndex = 0,
                    originalEncryptedPath = "/files/vault_scans/p1_orig.jpg.enc",
                    enhancedEncryptedPath = "/files/vault_scans/p1_enh.jpg.enc",
                    activeEnhanced = true,
                    filterMode = ScanFilterMode.ENHANCED_COLOR,
                    rotationDegrees = 90
                )
            )
        )

        val docId = "doc_tax_2026"
        val encryptedBlob = crypto.encryptBytes(metadata.toJsonBytes(), docId.toByteArray())
        val restored = SensitiveDocumentMetadata.fromJsonBytes(
            crypto.decryptBytes(encryptedBlob, docId.toByteArray())
        )

        assertEquals("Tax Return 2026", restored.title)
        assertEquals("Embedded Vector Form 1040 Stream", restored.nativePdfText)
        assertEquals("ML Kit OCR Recognized Text", restored.ocrRecognizedText)
        assertEquals(1, restored.scanPages.size)
        assertEquals(90, restored.scanPages.first().rotationDegrees)

        // Create a real PDF, encrypt to vault, open DecryptedPdfSessionHandle, and verify deterministic cleanup
        val samplePdf = File(context.cacheDir, "session_source.pdf")
        val vaultEncFile = File(context.cacheDir, "session_vault.pdf.enc")
        try {
            pdfEngine.createSampleVectorPdf(samplePdf)
            crypto.encryptToFile(samplePdf.readBytes(), vaultEncFile, docId)

            val docModel = PaperworkDocument(
                id = docId,
                title = "Tax Return 2026",
                fileName = "Tax_Return_2026.pdf",
                encryptedPdfPath = vaultEncFile.absolutePath,
                encryptedThumbnailPath = null,
                fileSize = samplePdf.length(),
                pageCount = 3,
                documentType = DocumentType.PDF,
                createdAt = 1000L,
                modifiedAt = 1000L
            )

            val sessionResult = accessManager.openDecryptedSession(docModel, purpose = "test_viewer")
            assertTrue(sessionResult.isSuccess)
            val handle = sessionResult.getOrThrow()
            assertTrue(handle.decryptedFile.exists())

            val pfd = handle.openReadOnlyDescriptor()
            assertNotNull(pfd)

            handle.close()
            assertTrue(handle.isClosed())
            assertFalse("Decrypted session file must be deleted on close()", handle.decryptedFile.exists())
        } finally {
            crypto.deleteFileSafely(samplePdf)
            crypto.deleteFileSafely(vaultEncFile)
        }
    }

    @Test
    fun `verify non-rasterizing PDFBox page reordering and PdfIntegrityValidator`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfEngine = PdfProcessingEngine(context)

        val vectorPdfFile = File(context.cacheDir, "sample_vector_test.pdf")
        val reversedPdfFile = File(context.cacheDir, "sample_reversed_test.pdf")

        try {
            pdfEngine.createSampleVectorPdf(vectorPdfFile)
            assertTrue(vectorPdfFile.exists())

            val inspection = pdfEngine.inspectPdfFile(vectorPdfFile)
            assertEquals(3, inspection.pageCount)
            assertTrue("Vector PDF should preserve native text layer", inspection.hasNativeTextLayer)

            val originalTextByPage = pdfEngine.nativeTextExtractor.extractEmbeddedTextByPage(vectorPdfFile)
            assertTrue(originalTextByPage[0].orEmpty().contains("Page 1 of 3"))
            assertTrue(originalTextByPage[2].orEmpty().contains("Page 3 of 3"))

            // Reverse page ordering (3 -> 2 -> 1) + 90-degree rotation via PDFBox
            val outCount = pdfEngine.createReorderedOrFilteredPdf(
                sourcePdfFile = vectorPdfFile,
                targetPdfFile = reversedPdfFile,
                reverseOrder = true,
                extraRotationDegrees = 90
            )
            assertEquals(3, outCount)

            val report = PdfIntegrityValidator.validateRewrittenPdf(
                sourcePdfFile = vectorPdfFile,
                rewrittenPdfFile = reversedPdfFile,
                expectedPageCount = 3,
                expectedFirstPageRotation = 90
            )
            assertTrue("PdfIntegrityValidator must pass on rewritten PDF", report.isValid)
            assertTrue(report.allPageBoxesValid)
            assertTrue(report.rotationPreserved)

            val reversedTextByPage = pdfEngine.nativeTextExtractor.extractEmbeddedTextByPage(reversedPdfFile)
            assertTrue(
                "First page of reversed PDF should be original Page 3",
                reversedTextByPage[0].orEmpty().contains("Page 3 of 3")
            )
            assertTrue(
                "Last page of reversed PDF should be original Page 1",
                reversedTextByPage[2].orEmpty().contains("Page 1 of 3")
            )
        } finally {
            vectorPdfFile.delete()
            reversedPdfFile.delete()
        }
    }

    @Test
    fun `verify SafeBitmapDecoder downsampling, deskew, and color-preserving vs grayscale scan filters`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfEngine = PdfProcessingEngine(context)

        // Verify power-of-two inSampleSize calculation for 48MP (8000x6000) camera images
        val sampleSize = SafeBitmapDecoder.calculateInSampleSize(8000, 6000, 2000)
        assertTrue("8000x6000 image should be downsampled by >= 2x", sampleSize >= 2)

        val originals = pdfEngine.createSampleScannedBitmaps()
        assertEquals(2, originals.size)

        val firstOriginal = originals.first()
        val enhancedColor = pdfEngine.createEnhancedDocumentBitmap(
            firstOriginal,
            ScanFilterMode.ENHANCED_COLOR
        )
        val grayscale = pdfEngine.createEnhancedDocumentBitmap(
            firstOriginal,
            ScanFilterMode.INK_SAVING_GRAYSCALE
        )
        assertEquals(firstOriginal.width, enhancedColor.width)
        assertEquals(firstOriginal.height, grayscale.height)

        // Verify grayscale has R == G == B on a sample pixel
        val grayPixel = grayscale.getPixel(120, 955)
        assertEquals(Color.red(grayPixel), Color.green(grayPixel))
        assertEquals(Color.green(grayPixel), Color.blue(grayPixel))

        // Verify 4-corner perspective and deskew
        val deskewed = pdfEngine.applyPerspectiveAndDeskew(
            source = firstOriginal,
            quad = NormalizedQuadCorners(
                topLeft = PointF(0.02f, 0.02f),
                topRight = PointF(0.98f, 0.02f),
                bottomRight = PointF(0.98f, 0.98f),
                bottomLeft = PointF(0.02f, 0.98f)
            ),
            deskewAngleDegrees = 2.5f
        )
        assertEquals(firstOriginal.width, deskewed.width)

        val pdfBytes = pdfEngine.buildPdfFromImages(
            pages = originals.map { PageImageSpec(it, 0) },
            pageSize = PageSizeOption.A4,
            orientation = PageOrientation.PORTRAIT,
            margin = MarginSize.SMALL,
            quality = PdfQuality.BALANCED
        )
        assertTrue(pdfBytes.size > 1024)
    }
}
