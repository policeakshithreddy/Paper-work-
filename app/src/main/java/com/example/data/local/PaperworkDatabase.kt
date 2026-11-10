package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import com.example.domain.model.ScanPageRecord

/**
 * Room Entity storing document records.
 *
 * Security Architecture:
 * Sensitive metadata (`title`, `fileName`, `extractedOcrText`, `scanPagesJson`) is never stored
 * in plaintext SQLite columns. Instead, it is serialized to JSON and encrypted with
 * Android Keystore AES-256-GCM inside [encryptedMetadataBlob], binding [id] as GCM AAD.
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
            encryptedMetadataBlob.contentEquals(other.encryptedMetadataBlob)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + modifiedAt.hashCode()
        result = 31 * result + encryptedMetadataBlob.contentHashCode()
        return result
    }
}

/**
 * Sensitive payload encrypted inside [DocumentEntity.encryptedMetadataBlob].
 */
data class SensitiveDocumentMetadata(
    val title: String,
    val fileName: String,
    val extractedOcrText: String,
    val scanPages: List<ScanPageRecord>
) {
    fun toJsonBytes(): ByteArray {
        val root = JSONObject()
        root.put("title", title)
        root.put("fileName", fileName)
        root.put("extractedOcrText", extractedOcrText)
        val pagesArray = JSONArray()
        scanPages.forEach { page ->
            val obj = JSONObject()
            obj.put("pageId", page.pageId)
            obj.put("pageIndex", page.pageIndex)
            obj.put("originalEncryptedPath", page.originalEncryptedPath)
            obj.put("enhancedEncryptedPath", page.enhancedEncryptedPath)
            obj.put("activeEnhanced", page.activeEnhanced)
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
                pages.add(
                    ScanPageRecord(
                        pageId = obj.optString("pageId", "page_$i"),
                        pageIndex = obj.optInt("pageIndex", i),
                        originalEncryptedPath = obj.optString("originalEncryptedPath", ""),
                        enhancedEncryptedPath = obj.optString("enhancedEncryptedPath", ""),
                        activeEnhanced = obj.optBoolean("activeEnhanced", true),
                        rotationDegrees = obj.optInt("rotationDegrees", 0)
                    )
                )
            }
            return SensitiveDocumentMetadata(
                title = root.optString("title", "Untitled document"),
                fileName = root.optString("fileName", "document.pdf"),
                extractedOcrText = root.optString("extractedOcrText", ""),
                scanPages = pages
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

    @Query("DELETE FROM vault_documents")
    suspend fun deleteAllDocuments()
}

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class PaperworkDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
