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

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val contentUri: String,
    val fileSize: Long,
    val pageCount: Int,
    val documentType: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val lastOpenedPage: Int = 0,
    val nativePdfText: String = "",
    val ocrRecognizedText: String = ""
)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE documents SET title = :title, fileName = :fileName, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, fileName: String, modifiedAt: Long)

    @Query("UPDATE documents SET lastOpenedPage = :page, modifiedAt = :modifiedAt WHERE id = :id")
    suspend fun updateLastOpenedPage(id: String, page: Int, modifiedAt: Long)
}

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class PaperworkDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
