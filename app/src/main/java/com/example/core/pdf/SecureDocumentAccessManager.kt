package com.example.core.pdf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.core.security.VaultCryptoManager
import com.example.domain.model.PaperworkDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents a controlled, temporary decrypted PDF session handle.
 *
 * Lifecycle Guarantee:
 * Encrypted vault files (`/files/vault_pdfs/{id}.pdf.enc`) begin with the 19-byte `PWKV`
 * binary header + random IV + AES-GCM ciphertext and are NEVER passed directly to `PdfRenderer`
 * or `SandboxedPdfLoader`.
 *
 * Instead:
 * `Encrypted Vault File -> Controlled Decryption -> DecryptedPdfSessionHandle -> Selected Viewer Engine -> Deterministic Cleanup`
 */
class DecryptedPdfSessionHandle(
    val sessionId: String,
    val documentId: String,
    val decryptedFile: File,
    private val onRelease: (DecryptedPdfSessionHandle) -> Unit
) : Closeable {

    val decryptedPdfFile: File
        get() = decryptedFile

    private val openDescriptors = mutableListOf<ParcelFileDescriptor>()
    @Volatile
    private var closedState: Boolean = false

    val fileUri: Uri
        get() = Uri.fromFile(decryptedFile)

    fun isClosed(): Boolean = closedState

    @Synchronized
    fun openReadOnlyDescriptor(): ParcelFileDescriptor? {
        if (closedState || !decryptedFile.exists()) return null
        return try {
            ParcelFileDescriptor.open(decryptedFile, ParcelFileDescriptor.MODE_READ_ONLY)?.also {
                openDescriptors.add(it)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openReadOnlyParcelFileDescriptor(): ParcelFileDescriptor? = openReadOnlyDescriptor()

    @Synchronized
    override fun close() {
        if (closedState) return
        closedState = true
        openDescriptors.forEach { pfd ->
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
        openDescriptors.clear()
        onRelease(this)
    }
}

/**
 * Manages active decrypted PDF viewing and printing sessions and performs deterministic
 * session cleanup as well as abandoned-session recovery.
 */
class SecureDocumentAccessManager(
    private val context: Context,
    private val cryptoManager: VaultCryptoManager
) {

    private val sessionDir = File(context.cacheDir, "active_session_tmp").apply { mkdirs() }
    private val activeHandles = ConcurrentHashMap<String, DecryptedPdfSessionHandle>()

    /**
     * Decrypts [document]'s encrypted `.enc` vault file into an isolated owner-only temporary
     * PDF file (`0600`) and returns a [DecryptedPdfSessionHandle] retained for the complete
     * viewer or print session.
     */
    suspend fun openDecryptedSession(
        document: PaperworkDocument,
        purpose: String = "viewer"
    ): Result<DecryptedPdfSessionHandle> = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(document.encryptedPdfPath)
            if (!encryptedFile.exists()) {
                return@withContext Result.failure(
                    IllegalStateException("Encrypted PDF file is missing from the vault.")
                )
            }

            val decryptedBytes = cryptoManager.decryptFromFile(
                sourceEncryptedFile = encryptedFile,
                aad = document.id
            )

            // Verify PDF magic header ("%PDF-") before passing to any PDF engine
            if (decryptedBytes.size < 5 ||
                String(decryptedBytes, 0, 5, Charsets.US_ASCII) != "%PDF-"
            ) {
                return@withContext Result.failure(
                    IllegalStateException("Decrypted stream does not contain a valid %PDF- header.")
                )
            }

            val sessionId = UUID.randomUUID().toString().take(8)
            sessionDir.mkdirs()
            val tempFile = File(sessionDir, "${purpose}_${sessionId}_${document.id}.pdf")
            FileOutputStream(tempFile).use { out ->
                out.write(decryptedBytes)
                out.fd.sync()
            }
            tempFile.setReadable(false, false)
            tempFile.setReadable(true, true)
            tempFile.setWritable(false, false)
            tempFile.setWritable(true, true)

            val handle = DecryptedPdfSessionHandle(
                sessionId = sessionId,
                documentId = document.id,
                decryptedFile = tempFile,
                onRelease = { released ->
                    activeHandles.remove(released.sessionId)
                    cryptoManager.deleteFileSafely(released.decryptedFile)
                }
            )
            activeHandles[sessionId] = handle
            Result.success(handle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Cleans up any abandoned session files that are not currently held by an active handle.
     */
    fun cleanupAbandonedSessions(): Int {
        if (!sessionDir.exists()) return 0
        val activeFiles = activeHandles.values.map { it.decryptedFile.absolutePath }.toSet()
        var purged = 0
        sessionDir.listFiles()?.forEach { file ->
            if (!activeFiles.contains(file.absolutePath)) {
                if (file.delete()) purged++
            }
        }
        return purged
    }

    fun recoverAndPurgeAbandonedSessions(): Int = cleanupAbandonedSessions()

    fun closeAllSessions() {
        activeHandles.values.toList().forEach { it.close() }
        activeHandles.clear()
        cryptoManager.purgeAllTempFiles()
    }
}
