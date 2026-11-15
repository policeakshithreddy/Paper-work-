package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.PaperworkDatabase
import com.example.data.repository.DocumentRepository
import com.example.core.pdf.PdfProcessingEngine
import com.example.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DownloadsLifecycleTest {
    @Test fun createRenameDuplicateImportAndDeleteInDownloads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PaperworkDatabase::class.java).build()
        val repo = DocumentRepository(context, db.documentDao(), PdfProcessingEngine(context))
        val input = File.createTempFile("test_page_", ".png", context.cacheDir)
        val created = mutableListOf<PaperworkDocument>()
        try {
            val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val doc = repo.savePages(listOf(Uri.fromFile(input)), "Paperwork test", DocumentType.SCANNED,
                ScanFilterMode.ORIGINAL, ScanOutputFormat.PDF, PageSizeOption.A4, PdfQuality.BALANCED)!!
            created.add(doc)
            assertTrue(doc.contentUri.startsWith("content://"))
            assertEquals(1, doc.pageCount)
            val file = repo.copyToCache(doc, "test")
            assertTrue(file.readBytes().take(4).toByteArray().contentEquals("%PDF".toByteArray()))
            file.delete()
            repo.rename(doc, "Renamed test")
            assertEquals("Renamed test", repo.allDocumentsFlow.first().single().title)
            val duplicate = repo.duplicate(doc)
            created.add(duplicate)
            assertNotEquals(doc.contentUri, duplicate.contentUri)
            val imported = repo.importPdf(Uri.parse(doc.contentUri))
            created.add(imported)
            assertEquals(3, repo.allDocumentsFlow.first().size)
            created.forEach { repo.delete(it) }
            created.clear()
            assertTrue(repo.allDocumentsFlow.first().isEmpty())
        } finally {
            created.forEach { runCatching { repo.delete(it) } }
            input.delete()
            db.close()
        }
    }
}
