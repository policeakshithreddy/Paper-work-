package com.example.data.preferences

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.PageSizeOption
import com.example.domain.model.PdfQuality
import com.example.domain.model.PrintPageOrder
import com.example.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.paperworkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "paperwork_preferences"
)

data class UserPreferencesState(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val defaultPdfQuality: PdfQuality = PdfQuality.BALANCED,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultPrintOrder: PrintPageOrder = PrintPageOrder.NORMAL,
    val defaultEnhancedScan: Boolean = true,
    val isGridView: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinSaltBase64: String = "",
    val pinHashBase64: String = "",
    val failedPinAttempts: Int = 0,
    val lockoutUntilEpochMs: Long = 0L
)

class PaperworkPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val PDF_QUALITY = stringPreferencesKey("default_pdf_quality")
        val PAGE_SIZE = stringPreferencesKey("default_page_size")
        val PRINT_ORDER = stringPreferencesKey("default_print_order")
        val DEFAULT_ENHANCED = booleanPreferencesKey("default_enhanced_scan")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt_b64")
        val PIN_HASH = stringPreferencesKey("pin_hash_b64")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        val LOCKOUT_UNTIL = longPreferencesKey("lockout_until_epoch_ms")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.paperworkDataStore.data.map { prefs ->
        UserPreferencesState(
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: ThemePreference.SYSTEM.name)
            }.getOrDefault(ThemePreference.SYSTEM),
            defaultPdfQuality = runCatching {
                PdfQuality.valueOf(prefs[Keys.PDF_QUALITY] ?: PdfQuality.BALANCED.name)
            }.getOrDefault(PdfQuality.BALANCED),
            defaultPageSize = runCatching {
                PageSizeOption.valueOf(prefs[Keys.PAGE_SIZE] ?: PageSizeOption.A4.name)
            }.getOrDefault(PageSizeOption.A4),
            defaultPrintOrder = runCatching {
                PrintPageOrder.valueOf(prefs[Keys.PRINT_ORDER] ?: PrintPageOrder.NORMAL.name)
            }.getOrDefault(PrintPageOrder.NORMAL),
            defaultEnhancedScan = prefs[Keys.DEFAULT_ENHANCED] ?: true,
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: false,
            pinEnabled = prefs[Keys.PIN_ENABLED] ?: false,
            pinSaltBase64 = prefs[Keys.PIN_SALT] ?: "",
            pinHashBase64 = prefs[Keys.PIN_HASH] ?: "",
            failedPinAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockoutUntilEpochMs = prefs[Keys.LOCKOUT_UNTIL] ?: 0L
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.paperworkDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setDefaultPdfQuality(quality: PdfQuality) {
        context.paperworkDataStore.edit { it[Keys.PDF_QUALITY] = quality.name }
    }

    suspend fun setDefaultPageSize(pageSize: PageSizeOption) {
        context.paperworkDataStore.edit { it[Keys.PAGE_SIZE] = pageSize.name }
    }

    suspend fun setDefaultPrintOrder(order: PrintPageOrder) {
        context.paperworkDataStore.edit { it[Keys.PRINT_ORDER] = order.name }
    }

    suspend fun setDefaultEnhancedScan(enabled: Boolean) {
        context.paperworkDataStore.edit { it[Keys.DEFAULT_ENHANCED] = enabled }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.paperworkDataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun configurePin(salt: ByteArray, hash: ByteArray) {
        context.paperworkDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = true
            prefs[Keys.PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[Keys.PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun disablePin() {
        context.paperworkDataStore.edit { prefs ->
            prefs[Keys.PIN_ENABLED] = false
            prefs[Keys.PIN_SALT] = ""
            prefs[Keys.PIN_HASH] = ""
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun recordFailedPinAttempt(newCount: Int, lockoutUntilMs: Long) {
        context.paperworkDataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = newCount
            prefs[Keys.LOCKOUT_UNTIL] = lockoutUntilMs
        }
    }

    suspend fun resetFailedPinAttempts() {
        context.paperworkDataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKOUT_UNTIL] = 0L
        }
    }
}
