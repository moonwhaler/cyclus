package com.nordinary.cyclus.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.nordinary.cyclus.presenter.Appearance
import com.nordinary.cyclus.presenter.AppearanceMode
import com.nordinary.cyclus.widget.CyclusWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/**
 * The chosen appearance (Settings › Erscheinungsbild). A device setting in SharedPreferences, not health data: it is
 * readable before the encrypted database opens, so the lock screen and the first frame already use it. Not exported,
 * not in backups (the backup rules are include-only); reset by „Alles löschen“.
 */
object AppearanceStore {
    private const val PREFS = "appearance"
    private const val KEY = "v"
    private val _state = MutableStateFlow(Appearance.DEFAULT)
    val state: StateFlow<Appearance> = _state

    /** Process start: load the choice and re-apply the app's night mode (the system keeps its own copy on 12+). */
    fun init(ctx: Context) {
        _state.value = Appearance.decode(prefs(ctx).getString(KEY, null))
        applyNightMode(ctx, _state.value.mode)
    }

    /**
     * Android 10–11 (no per-app night mode): activities wrap their base context with the chosen night bit, so resources
     * (window background), dialogs, sheets and the system-bar style all agree; a change recreates the activity.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return base
        val mode = Appearance.decode(prefs(base).getString(KEY, null)).mode
        if (mode == AppearanceMode.SYSTEM) return base
        val config = android.content.res.Configuration(base.resources.configuration)
        val night = if (mode == AppearanceMode.DARK) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }

    /** Before Android 12 the activity has to be recreated for a mode change (12+ does that itself). */
    val needsRecreateOnModeChange: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun set(ctx: Context, appearance: Appearance) {
        val modeChanged = appearance.mode != _state.value.mode
        prefs(ctx).edit().putString(KEY, appearance.encode()).apply()
        _state.value = appearance
        if (modeChanged) applyNightMode(ctx, appearance.mode)
        refreshWidgets(ctx)
    }

    private fun refreshWidgets(ctx: Context) {
        val app = ctx.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { CyclusWidgets.refresh(app) } }
    }

    /** Erase everything: back to Cyclus Rose, following the system. */
    fun reset(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        _state.value = Appearance.DEFAULT
        applyNightMode(ctx, AppearanceMode.SYSTEM)
        refreshWidgets(ctx)
    }

    /**
     * Android 12+: the app's night mode itself (system bars, window background, splash and widgets' day/night follow);
     * before that the theme alone switches and MainActivity sets the bar icons.
     */
    private fun applyNightMode(ctx: Context, mode: AppearanceMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val m = when (mode) {
            AppearanceMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            AppearanceMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            AppearanceMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        runCatching { ctx.getSystemService(UiModeManager::class.java).setApplicationNightMode(m) }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
