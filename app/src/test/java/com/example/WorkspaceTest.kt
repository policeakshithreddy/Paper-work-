package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DocumentEntity
import com.example.data.local.PaperworkDatabase
import com.example.core.image.SafeBitmapDecoder
import com.example.core.pdf.PdfProcessingEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkspaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cataloguePreservesUriThroughRenameAndPageChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PaperworkDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.documentDao()
            dao.upsert(DocumentEntity("a", "Invoice", "Invoice.pdf", "content://media/external/downloads/42",
                1024, 3, "PDF", 1, 2))
            dao.rename("a", "Paid invoice", "Paid invoice.pdf", 3)
            dao.updateLastOpenedPage("a", 2, 4)
            val row = dao.observeAll().first().single()
            assertEquals("Paid invoice", row.title)
            assertEquals("content://media/external/downloads/42", row.contentUri)
            assertEquals(2, row.lastOpenedPage)
            dao.delete("a")
            assertTrue(dao.observeAll().first().isEmpty())
        } finally { db.close() }
    }

    @Test fun legacyResetOnlyRemovesLegacyLocations() {
        val legacy = File(context.filesDir, "vault_pdfs").apply { mkdirs() }
        File(legacy, "old.enc").writeText("old")
        val unrelated = File(context.filesDir, "keep.txt").apply { writeText("keep") }
        val reset = LegacyVaultResetManager(context)
        assertTrue(reset.needsReset())
        reset.clearLegacyData()
        assertFalse(legacy.exists())
        assertTrue(unrelated.exists())
        unrelated.delete()
    }

    @Test fun malformedImportDoesNotAddCatalogueEntry() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PaperworkDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = com.example.data.repository.DocumentRepository(context, db.documentDao(), PdfProcessingEngine(context))
            val input = File(context.cacheDir, "invalid.pdf").apply { writeText("not a PDF") }
            assertTrue(runCatching { repository.importPdf(android.net.Uri.fromFile(input)) }.isFailure)
            assertTrue(db.documentDao().observeAll().first().isEmpty())
            input.delete()
            Unit
        } finally { db.close() }
    }

    @Test fun highResolutionImagesAreDownsampled() {
        assertTrue(SafeBitmapDecoder.calculateInSampleSize(12000, 9000, 1800) >= 4)
        assertEquals(1, SafeBitmapDecoder.calculateInSampleSize(800, 600, 1800))
    }

    @Test fun pageSelectionHandlesRangesAndDuplicates() {
        assertEquals(listOf(1, 2, 3, 5), PdfProcessingEngine(context).parsePageRange("1-3, 3, 5", 5))
    }
}
