package com.alpha.screentranslator.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alpha.screentranslator.model.AppPreferences
import com.alpha.screentranslator.model.SourceScript
import com.alpha.screentranslator.model.TranslationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alpha_translator_prefs")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val TRANSLATION_MODE = stringPreferencesKey("translation_mode")
        val SOURCE_SCRIPT = stringPreferencesKey("source_script")
        val BUTTON_SIZE = floatPreferencesKey("button_size")
        val BUTTON_OPACITY = floatPreferencesKey("button_opacity")
        val SPEED_MS = longPreferencesKey("speed_ms")
        val PRESERVE_LAYOUT = booleanPreferencesKey("preserve_layout")
        val AUTO_COPY = booleanPreferencesKey("auto_copy")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
            selectedMode = prefs[Keys.TRANSLATION_MODE]?.let {
                runCatching { TranslationMode.valueOf(it) }.getOrNull()
            } ?: TranslationMode.FULL_SCREEN,
            selectedScript = prefs[Keys.SOURCE_SCRIPT]?.let {
                runCatching { SourceScript.valueOf(it) }.getOrNull()
            } ?: SourceScript.AUTO_LATIN,
            floatingButtonSizeDp = prefs[Keys.BUTTON_SIZE] ?: 56f,
            floatingButtonOpacity = prefs[Keys.BUTTON_OPACITY] ?: 0.92f,
            translationSpeedMs = prefs[Keys.SPEED_MS] ?: 2500L,
            preserveLayoutOverlay = prefs[Keys.PRESERVE_LAYOUT] ?: true,
            autoCopyHindi = prefs[Keys.AUTO_COPY] ?: false
        )
    }

    suspend fun updateTheme(isDark: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }

    suspend fun updateMode(mode: TranslationMode) {
        context.dataStore.edit { it[Keys.TRANSLATION_MODE] = mode.name }
    }

    suspend fun updateScript(script: SourceScript) {
        context.dataStore.edit { it[Keys.SOURCE_SCRIPT] = script.name }
    }

    suspend fun updateButtonCustomization(sizeDp: Float, opacity: Float) {
        context.dataStore.edit {
            it[Keys.BUTTON_SIZE] = sizeDp
            it[Keys.BUTTON_OPACITY] = opacity
        }
    }

    suspend fun updateSpeed(speedMs: Long) {
        context.dataStore.edit { it[Keys.SPEED_MS] = speedMs }
    }

    suspend fun updateLayoutToggle(preserve: Boolean) {
        context.dataStore.edit { it[Keys.PRESERVE_LAYOUT] = preserve }
    }
}
