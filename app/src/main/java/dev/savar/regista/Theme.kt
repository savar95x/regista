package dev.savar.regista

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import dev.savar.kit.AppFont
import dev.savar.kit.kitTypography
import dev.savar.kit.pureBlack

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** App theme preferences (prefs `ui`), observable so the theme switches immediately. */
object ThemePrefs {
    var pureBlack by mutableStateOf(false)
        private set
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    private var loaded = false

    private fun p(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        pureBlack = p(ctx).getBoolean("pure_black", false)
        mode = ThemeMode.entries.firstOrNull { it.name == p(ctx).getString("theme", null) } ?: ThemeMode.SYSTEM
    }

    fun setPureBlack(ctx: Context, on: Boolean) {
        pureBlack = on
        p(ctx).edit().putBoolean("pure_black", on).apply()
    }

    fun setMode(ctx: Context, m: ThemeMode) {
        mode = m
        p(ctx).edit().putString("theme", m.name).apply()
    }

    /** Screenshots only. */
    fun force(mode: ThemeMode, pureBlack: Boolean) {
        loaded = true
        this.mode = mode
        this.pureBlack = pureBlack
    }
}

@Composable
internal fun isAppDark(): Boolean = when (ThemePrefs.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegistaTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    ThemePrefs.load(ctx)
    // App font: the kit's, migrating the Google Font Regista used before (prefs "fonts"/"family").
    AppFont.load(ctx) { ctx.getSharedPreferences("fonts", Context.MODE_PRIVATE).getString("family", null) }
    val dark = isAppDark()
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && ThemePrefs.pureBlack) base.pureBlack() else base
    val font = AppFont.settings
    val typography = remember(font) { kitTypography(font) }
    val activity = ctx as? android.app.Activity
    val view = LocalView.current
    SideEffect {
        activity?.window?.let { w ->
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(scheme.background.toArgb()))
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        content = content,
    )
}
