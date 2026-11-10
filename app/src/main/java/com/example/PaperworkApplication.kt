package com.example

import android.app.Application
import androidx.room.Room
import com.example.core.pdf.PdfProcessingEngine
import com.example.core.pdf.SecureDocumentAccessManager
import com.example.core.security.AppAccessLockManager
import com.example.core.security.VaultCryptoManager
import com.example.data.local.PaperworkDatabase
import com.example.data.preferences.PaperworkPreferencesRepository
import com.example.data.repository.DocumentRepository
import com.example.features.printing.PaperworkPrintHelper

class PaperworkContainer(context: Application) {
    val cryptoManager = VaultCryptoManager(context)
    val accessLockManager = AppAccessLockManager(context)
    val secureDocumentAccessManager = SecureDocumentAccessManager(context, cryptoManager)

    private val database: PaperworkDatabase = Room.databaseBuilder(
        context,
        PaperworkDatabase::class.java,
        "paperwork_vault.db"
    )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    val pdfEngine = PdfProcessingEngine(context)

    val preferencesRepository = PaperworkPreferencesRepository(context)

    val documentRepository = DocumentRepository(
        context = context,
        documentDao = database.documentDao(),
        cryptoManager = cryptoManager,
        pdfEngine = pdfEngine,
        secureDocumentAccessManager = secureDocumentAccessManager
    )

    val printHelper = PaperworkPrintHelper(
        context = context,
        cryptoManager = cryptoManager,
        pdfEngine = pdfEngine,
        secureAccessManager = secureDocumentAccessManager
    )
}

class PaperworkApplication : Application() {

    lateinit var container: PaperworkContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PaperworkContainer(this)
        // Clean up any orphaned decrypted temporary session files from previous runs
        container.secureDocumentAccessManager.cleanupAbandonedSessions()
    }
}
