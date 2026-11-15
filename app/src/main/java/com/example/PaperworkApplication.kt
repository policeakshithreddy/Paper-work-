package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.core.pdf.PdfProcessingEngine
import com.example.data.local.PaperworkDatabase
import com.example.data.preferences.PaperworkPreferencesRepository
import com.example.data.repository.DocumentRepository
import java.io.File
import java.security.KeyStore

class LegacyVaultResetManager(private val context: Context) {
    private val legacyDatabase = "paperwork_vault.db"

    fun needsReset(): Boolean = context.getDatabasePath(legacyDatabase).exists() ||
        listOf("vault_pdfs", "vault_scans", "vault_thumbs").any { File(context.filesDir, it).exists() }

    fun clearLegacyData() {
        context.deleteDatabase(legacyDatabase)
        listOf("vault_pdfs", "vault_scans", "vault_thumbs").forEach { name ->
            File(context.filesDir, name).deleteRecursively()
        }
        File(context.cacheDir, "active_session_tmp").deleteRecursively()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.aliases().toList()
                .filter { it.startsWith("paperwork_vault_aes_256_gcm_v") }
                .forEach { alias -> keyStore.deleteEntry(alias) }
        }
        context.getSharedPreferences("paperwork_keystore_version_meta", Context.MODE_PRIVATE).edit().clear().apply()
        File(context.filesDir, "datastore/paperwork_preferences.preferences_pb").delete()
    }
}

class PaperworkContainer(context: Application) {
    private val database = Room.databaseBuilder(context, PaperworkDatabase::class.java, "paperwork_workspace.db").build()
    val pdfEngine = PdfProcessingEngine(context)
    val preferencesRepository = PaperworkPreferencesRepository(context)
    val documentRepository = DocumentRepository(context, database.documentDao(), pdfEngine)
    val legacyResetManager = LegacyVaultResetManager(context)
}

class PaperworkApplication : Application() {
    lateinit var container: PaperworkContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = PaperworkContainer(this)
    }
}
