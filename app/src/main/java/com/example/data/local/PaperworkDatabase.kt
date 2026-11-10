package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.domain.model.ScanFilterMode
import com.example.domain.model.ScanPageRecord
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room Entity storing document records.
 *
 * Security Architecture:
 * Sensitive metadata (`title`, `fileName`, `nativePdfText`, `ocrRecognizedText`, `scanPagesJson`)
 * is never stored in plaintext SQLite columns. Instead, it is serialized to JSON and encrypted with
 * Android Keystore AES-256-GCM inside [encryptedMetadataBlob], binding [id] as GCM AAD and
 * storing the 19-byte `PWKV` versioned header.
 */
@Entity(tableName = "vault_documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val encryptedPdfPath: String,
    val encryptedThumbnailPath: String?,
    val fileSize: Long,
    val pageCount: Int,
    val documentType: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedPage: Int,
    val hasNativeTextLayer: Boolean,
    val currentlyUsingEnhanced: Boolean,
    val keyVersion: Int = 1,
    val encryptedMetadataBlob: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentEntity) return false
        return id == other.id &&
            modifiedAt == other.modifiedAt &&
            pageCount == other.pageCount &&
            fileSize == other.fileSize &&
            currentlyUsingEnhanced == other.currentlyUsingEnhanced &&
            lastOpenedPage == other.lastOpenedPage &&
            keyVersion == other.keyVersion &&
            encryptedMetadataBlob.contentEquals(other.encryptedMetadataBlob)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + modifiedAt.hashCode()
        result = 31 * result + keyVersion
        result = 31 * result + encryptedMetadataBlob.contentHashCode()
        return result
    }
}

/**
 * Sensitive payload encrypted inside [DocumentEntity.encryptedMetadataBlob].
 * Keeps [nativePdfText] (embedded PDF text streams) and [ocrRecognizedText] (ML Kit OCR output)
 * strictly separate.
 */
data class SensitiveDocumentMetadata(
    val title: String,
    val fileName: String,
    val nativePdfText: String,
    val ocrRecognizedText: String,
    val scanPages: List<ScanPageRecord>,
    val activeScanFilterMode: ScanFilterMode = ScanFilterMode.ENHANCED_COLOR,
    val hasDigitalSignatures: Boolean = false
) {
    fun toJsonBytes(): ByteArray {
        val root = JSONObject()
        root.put("title", title)
        root.put("fileName", fileName)
        root.put("nativePdfText", nativePdfText)
        root.put("ocrRecognizedText", ocrRecognizedText)
        root.put("activeScanFilterMode", activeScanFilterMode.name)
        root.put("hasDigitalSignatures", hasDigitalSignatures)
        val pagesArray = JSONArray()
        scanPages.forEach { page ->
            val obj = JSONObject()
            obj.put("pageId", page.pageId)
            obj.put("pageIndex", page.pageIndex)
            obj.put("originalEncryptedPath", page.originalEncryptedPath)
            obj.put("enhancedEncryptedPath", page.enhancedEncryptedPath)
            obj.put("activeEnhanced", page.activeEnhanced)
            obj.put("filterMode", page.filterMode.name)
            obj.put("rotationDegrees", page.rotationDegrees)
            pagesArray.put(obj)
        }
        root.put("scanPages", pagesArray)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): SensitiveDocumentMetadata {
            val root = JSONObject(String(bytes, Charsets.UTF_8))
            val pagesArray = root.optJSONArray("scanPages") ?: JSONArray()
            val pages = mutableListOf<ScanPageRecord>()
            for (i in 0 until pagesArray.length()) {
                val obj = pagesArray.getJSONObject(i)
                val filterMode = runCatching {
                    ScanFilterMode.valueOf(obj.optString("filterMode", ScanFilterMode.ENHANCED_COLOR.name))
                }.getOrDefault(ScanFilterMode.ENHANCED_COLOR)
                pages.add(
                    ScanPageRecord(
                        pageId = obj.optString("pageId", "page_$i"),
                        pageIndex = obj.optInt("pageIndex", i),
                        originalEncryptedPath = obj.optString("originalEncryptedPath", ""),
                        enhancedEncryptedPath = obj.optString("enhancedEncryptedPath", ""),
                        activeEnhanced = obj.optBoolean("activeEnhanced", true),
                        filterMode = filterMode,
                        rotationDegrees = obj.optInt("rotationDegrees", 0)
                    )
                )
            }
            val legacyExtracted = root.optString("extractedOcrText", "")
            val nativePdfText = root.optString("nativePdfText", "")
            val ocrText = root.optString("ocrRecognizedText", legacyExtracted)
            val activeFilter = runCatching {
                ScanFilterMode.valueOf(
                    root.optString("activeScanFilterMode", ScanFilterMode.ENHANCED_COLOR.name)
                )
            }.getOrDefault(ScanFilterMode.ENHANCED_COLOR)

            return SensitiveDocumentMetadata(
                title = root.optString("title", "Untitled document"),
                fileName = root.optString("fileName", "document.pdf"),
                nativePdfText = nativePdfText,
                ocrRecognizedText = ocrText,
                scanPages = pages,
                activeScanFilterMode = activeFilter,
                hasDigitalSignatures = root.optBoolean("hasDigitalSignatures", false)
            )
        }
    }
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM vault_documents ORDER BY modifiedAt DESC")
    fun observeAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM vault_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(entity: DocumentEntity)

    @Query("UPDATE vault_documents SET lastOpenedPage = :pageIndex, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateLastOpenedPage(id: String, pageIndex: Int, modifiedAt: Long)

    @Query("DELETE FROM vault_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM vault_documents WHERE keyVersion != :activeKeyVersion")
    suspend fun deleteDocumentsWithOldKeyVersions(activeKeyVersion: Int)

    @Query("DELETE FROM vault_documents")
    suspend fun deleteAllDocuments()
}

@Database(entities = [DocumentEntity::class], version = 2, exportSchema = false)
abstract class PaperworkDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
