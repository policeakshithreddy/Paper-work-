package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.security.VaultCryptoManager
import com.example.data.local.SensitiveDocumentMetadata
import com.example.domain.model.MarginSize
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfQuality
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
    fun `verify app name is Paperwork`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Paperwork", appName)
    }

    @Test
    fun `verify AES-256-GCM encryption and AAD tamper detection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)

        val secretPayload = "Sensitive Contract #PW-90210 & OCR Text Cache".toByteArray(Charsets.UTF_8)
        val aad = "doc_uuid_12345".toByteArray(Charsets.UTF_8)

        val encrypted = crypto.encryptBytes(secretPayload, aad)
        assertFalse(encrypted.contentEquals(secretPayload))

        val decrypted = crypto.decryptBytes(encrypted, aad)
        assertArrayEquals(secretPayload, decrypted)

        // Verify GCM Associated Authenticated Data (AAD) rejects row-swapping tampering
        var tamperDetected = false
        try {
            crypto.decryptBytes(encrypted, "wrong_doc_uuid".toByteArray(Charsets.UTF_8))
        } catch (_: SecurityException) {
            tamperDetected = true
        }
        assertTrue("Expected SecurityException on mismatched GCM AAD", tamperDetected)
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
    fun `verify sensitive metadata encryption and temporary file zeroing cleanup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = VaultCryptoManager(context)

        val metadata = SensitiveDocumentMetadata(
            title = "Tax Return 2026",
            fileName = "Tax_Return_2026.pdf",
            extractedOcrText = "Form 1040 Internal Revenue Service",
            scanPages = listOf(
                ScanPageRecord(
                    pageId = "p1",
                    pageIndex = 0,
                    originalEncryptedPath = "/files/vault_scans/p1_orig.jpg.enc",
                    enhancedEncryptedPath = "/files/vault_scans/p1_enh.jpg.enc",
                    activeEnhanced = true,
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
        assertEquals("Form 1040 Internal Revenue Service", restored.extractedOcrText)
        assertEquals(1, restored.scanPages.size)
        assertEquals(90, restored.scanPages.first().rotationDegrees)

        // Materialize temp file and verify secure deletion
        val vaultEncFile = File(context.cacheDir, "test_vault_doc.pdf.enc")
        crypto.encryptToFile("PDF-1.7 test content".toByteArray(), vaultEncFile, docId)
        val tempDecrypted = crypto.materializeDecryptedTempFile(vaultEncFile, "session_view.pdf", docId)
        assertTrue(tempDecrypted.exists())
        assertEquals("PDF-1.7 test content", tempDecrypted.readText())

        val purgedCount = crypto.purgeAllTempFiles()
        assertTrue(purgedCount >= 1)
        assertFalse(tempDecrypted.exists())
        crypto.secureDeleteFile(vaultEncFile)
    }

    @Test
    fun `verify vector PDF generation and reverse page ordering via PDFBox`() = runBlocking {
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

            val originalTextByPage = pdfEngine.extractNativeTextByPage(vectorPdfFile)
            assertTrue(originalTextByPage[0].orEmpty().contains("Page 1 of 3"))
            assertTrue(originalTextByPage[2].orEmpty().contains("Page 3 of 3"))

            // Reverse page ordering (3 -> 2 -> 1) using PDFBox without rasterizing vector text
            val outCount = pdfEngine.createReorderedOrFilteredPdf(
                sourcePdfFile = vectorPdfFile,
                targetPdfFile = reversedPdfFile,
                reverseOrder = true
            )
            assertEquals(3, outCount)

            val reversedTextByPage = pdfEngine.extractNativeTextByPage(reversedPdfFile)
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
    fun `verify non-destructive Original and Enhanced scan pipeline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfEngine = PdfProcessingEngine(context)

        val originals = pdfEngine.createSampleScannedBitmaps()
        assertEquals(2, originals.size)

        val firstOriginal = originals.first()
        val enhanced = pdfEngine.createEnhancedDocumentBitmap(firstOriginal)
        assertNotNull(enhanced)
        assertEquals(firstOriginal.width, enhanced.width)
        assertEquals(firstOriginal.height, enhanced.height)

        val pdfBytes = pdfEngine.buildPdfFromImages(
            pages = originals.map { com.example.core.pdf.PageImageSpec(it, 0) },
            pageSize = PageSizeOption.A4,
            orientation = PageOrientation.PORTRAIT,
            margin = MarginSize.SMALL,
            quality = PdfQuality.BALANCED
        )
        assertTrue(pdfBytes.size > 1024)
    }
}
