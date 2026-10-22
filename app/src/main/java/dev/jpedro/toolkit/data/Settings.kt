package dev.jpedro.toolkit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jpedro.toolkit.core.BackendPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("toolkit_settings")

enum class ThemeName(val label: String) {
    BLUE("Blue"),
    NAVY("Navy"),
    NORD("Nord"),
    MATERIAL_EXPRESSIVE("Dynamic (Material You)")
}

enum class NavStyle(val label: String) {
    LABELS("Labels"),
    ICONS("Icons")
}

enum class NavLayout(val label: String) {
    SINGLE_LINE("Single-Line (Fit All)"),
    CLASSIC("Classic (Scrolling)")
}

enum class NavPosition(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom")
}

enum class UpdateChannel(val label: String) {
    BETA("Beta"),
    STABLE("Stable")
}

enum class UpdateSource(val label: String) {
    GITHUB("GitHub Releases"),
    WEBSITE("Google Drive / Website")
}

data class AppSettings(
    val theme: ThemeName = ThemeName.MATERIAL_EXPRESSIVE,
    val roundness: Float = 12f,
    val cardOpacity: Float = 0.78f,
    val boldText: Boolean = false,
    val navStyle: NavStyle = NavStyle.ICONS,
    val navPosition: NavPosition = NavPosition.BOTTOM,
    val navLayout: NavLayout = NavLayout.SINGLE_LINE,
    val animations: Boolean = true,
    val showDescriptions: Boolean = true,
    val wallpaperUri: String? = null,
    val wallpaperBlur: Float = 0f,
    val wallpaperDim: Float = 0.35f,
    val backend: BackendPreference = BackendPreference.AUTO,
    val confirmDangerous: Boolean = true,
    val rebootAfterFlash: Boolean = true,
    val validateBinaries: Boolean = false,
    val keepAwakeWhileFlashing: Boolean = true,
    val terminalFontSize: Int = 13,
    val terminalMaxLines: Int = 1000,
    val firstRun: Boolean = true,
    val hideImei: Boolean = false,
    val lastVersion: String? = null,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val updateSource: UpdateSource = UpdateSource.GITHUB
)

object Keys {
    val THEME = stringPreferencesKey("theme")
    val ROUNDNESS = floatPreferencesKey("roundness")
    val CARD_OPACITY = floatPreferencesKey("card_opacity")
    val BOLD_TEXT = booleanPreferencesKey("bold_text")
    val NAV_STYLE = stringPreferencesKey("nav_style")
    val NAV_POSITION = stringPreferencesKey("nav_position")
    val NAV_LAYOUT = stringPreferencesKey("nav_layout")
    val ANIMATIONS = booleanPreferencesKey("animations")
    val SHOW_DESCRIPTIONS = booleanPreferencesKey("show_descriptions")
    val WALLPAPER = stringPreferencesKey("wallpaper")
    val WALLPAPER_BLUR = floatPreferencesKey("wallpaper_blur")
    val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
    val BACKEND = stringPreferencesKey("backend")
    val CONFIRM = booleanPreferencesKey("confirm_dangerous")
    val REBOOT_AFTER_FLASH = booleanPreferencesKey("reboot_after_flash")
    val VALIDATE = booleanPreferencesKey("validate")
    val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
    val TERM_FONT = intPreferencesKey("term_font")
    val TERM_LINES = intPreferencesKey("term_lines")
    val FIRST_RUN = booleanPreferencesKey("first_run")
    val HIDE_IMEI = booleanPreferencesKey("hide_imei")
    val LAST_VERSION = stringPreferencesKey("last_version")
    val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    val UPDATE_SOURCE = stringPreferencesKey("update_source")
}

private inline fun <reified E : Enum<E>> Preferences.enumOf(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        theme = enumOf(Keys.THEME, d.theme),
        roundness = this[Keys.ROUNDNESS] ?: d.roundness,
        cardOpacity = this[Keys.CARD_OPACITY] ?: d.cardOpacity,
        boldText = this[Keys.BOLD_TEXT] ?: d.boldText,
        navStyle = enumOf(Keys.NAV_STYLE, d.navStyle),
        navPosition = enumOf(Keys.NAV_POSITION, d.navPosition),
        navLayout = enumOf(Keys.NAV_LAYOUT, d.navLayout),
        animations = this[Keys.ANIMATIONS] ?: d.animations,
        showDescriptions = this[Keys.SHOW_DESCRIPTIONS] ?: d.showDescriptions,
        wallpaperUri = this[Keys.WALLPAPER],
        wallpaperBlur = this[Keys.WALLPAPER_BLUR] ?: d.wallpaperBlur,
        wallpaperDim = this[Keys.WALLPAPER_DIM] ?: d.wallpaperDim,
        backend = enumOf(Keys.BACKEND, d.backend),
        confirmDangerous = this[Keys.CONFIRM] ?: d.confirmDangerous,
        rebootAfterFlash = this[Keys.REBOOT_AFTER_FLASH] ?: d.rebootAfterFlash,
        validateBinaries = this[Keys.VALIDATE] ?: d.validateBinaries,
        keepAwakeWhileFlashing = this[Keys.KEEP_AWAKE] ?: d.keepAwakeWhileFlashing,
        terminalFontSize = this[Keys.TERM_FONT] ?: d.terminalFontSize,
        terminalMaxLines = this[Keys.TERM_LINES] ?: d.terminalMaxLines,
        firstRun = this[Keys.FIRST_RUN] ?: d.firstRun,
        hideImei = this[Keys.HIDE_IMEI] ?: d.hideImei,
        lastVersion = this[Keys.LAST_VERSION],
        updateChannel = enumOf(Keys.UPDATE_CHANNEL, d.updateChannel),
        updateSource = enumOf(Keys.UPDATE_SOURCE, d.updateSource)
    )
}

class SettingsStore(private val context: Context) {

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun remove(key: Preferences.Key<*>) {
        context.dataStore.edit { it.remove(key) }
    }

    suspend fun reset() {
        context.dataStore.edit { it.clear() }
    }
}
