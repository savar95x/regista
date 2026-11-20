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

/**
 * AMOLED variant of a dark scheme (same recipe as Aria, Minutia and Hiatus): page #000, raised containers pulled
 * most of the way to black but keeping a hint of the wallpaper hue.
 */
internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.45f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.55f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.65f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.75f),
    surfaceVariant = lerp(Color.Black, surfaceVariant, 0.7f),
)

@Composable
internal fun isAppDark(): Boolean = when (ThemePrefs.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = f)
    return Typography(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegistaTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    ThemePrefs.load(ctx)
    Fonts.load(ctx)
    val dark = isAppDark()
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && ThemePrefs.pureBlack) base.pureBlack() else base
    val font = Fonts.fontFamily
    val typography = remember(font) { font?.let { Typography().withFont(it) } ?: Typography() }
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
