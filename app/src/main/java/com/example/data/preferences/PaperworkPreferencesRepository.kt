package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfQuality
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.map

private val Context.paperworkDataStore by preferencesDataStore(name = "paperwork_workspace_preferences")

data class UserPreferencesState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColor: Boolean = false,
    val defaultPdfQuality: PdfQuality = PdfQuality.BALANCED,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultScanDpi: Int = 300,
    val isGridView: Boolean = false
)

class PaperworkPreferencesRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val quality = stringPreferencesKey("pdf_quality")
        val pageSize = stringPreferencesKey("page_size")
        val scanDpi = intPreferencesKey("scan_dpi")
        val grid = booleanPreferencesKey("grid_view")
    }

    val preferencesFlow = context.paperworkDataStore.data.map { preferences ->
        UserPreferencesState(
            themePreference = runCatching { ThemePreference.valueOf(preferences[Keys.theme].orEmpty()) }
                .getOrDefault(ThemePreference.SYSTEM),
            useDynamicColor = preferences[Keys.dynamicColor] ?: false,
            defaultPdfQuality = runCatching { PdfQuality.valueOf(preferences[Keys.quality].orEmpty()) }
                .getOrDefault(PdfQuality.BALANCED),
            defaultPageSize = runCatching { PageSizeOption.valueOf(preferences[Keys.pageSize].orEmpty()) }
                .getOrDefault(PageSizeOption.A4),
            defaultScanDpi = preferences[Keys.scanDpi] ?: 300,
            isGridView = preferences[Keys.grid] ?: false
        )
    }

    suspend fun setThemePreference(value: ThemePreference) = context.paperworkDataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.paperworkDataStore.edit { it[Keys.dynamicColor] = value }
    suspend fun setDefaultPdfQuality(value: PdfQuality) = context.paperworkDataStore.edit { it[Keys.quality] = value.name }
    suspend fun setDefaultPageSize(value: PageSizeOption) = context.paperworkDataStore.edit { it[Keys.pageSize] = value.name }
    suspend fun setDefaultScanDpi(value: Int) = context.paperworkDataStore.edit { it[Keys.scanDpi] = value }
    suspend fun setGridView(value: Boolean) = context.paperworkDataStore.edit { it[Keys.grid] = value }
}
