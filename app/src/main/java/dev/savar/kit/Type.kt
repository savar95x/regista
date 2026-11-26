package dev.savar.kit

// Shared by Aria, Minutia, Hiatus and Regista. This file is byte-identical in all four repos:
// change it in one, copy it to the others (see KIT.md).

import android.content.Context
import android.util.Base64
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app's own UI font (not a widget's): a Google Font by name (null = the phone's font) and the text-style
 * sliders. Same meaning and ranges in every app: size 0.8–1.3×, weight −300..+300, letter spacing −5..+5 px.
 */
data class FontSettings(
    val font: String? = null,
    val textScale: Float = 1f,
    val weightDelta: Int = 0,
    val letterSpacing: Float = 0f,
)

/** Downloadable fonts through Google Play Services: any Google Font, every weight, cached by the system. */
private val provider by lazy {
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = CERTS.map { listOf(Base64.decode(it, Base64.DEFAULT)) },
    )
}

private val fontCache = HashMap<String, FontFamily>()

/** [name] as a font family with weights 100–900, so the weight slider works (missing weights fall back). */
fun googleFontFamily(name: String): FontFamily = synchronized(fontCache) {
    fontCache.getOrPut(name) {
        val font = GoogleFont(name)
        FontFamily((100..900 step 100).map { Font(googleFont = font, fontProvider = provider, weight = FontWeight(it)) })
    }
}

/**
 * The text styles all four apps share: Material 3's own type scale, untouched by default (sizes, weights,
 * line heights and letter spacing as Google ships them). The owner tried Aria's bolder, tighter, 8% smaller
 * styles in all four apps (Nov 2026) and found them off at normal screen density, so stock Material it is.
 * The font settings only apply on top: a Google Font, size ×, weight ± and letter spacing (when not 0).
 */
fun kitTypography(s: FontSettings): Typography {
    val base = Typography()
    if (s == FontSettings()) return base
    val family = s.font?.let { googleFontFamily(it) }
    fun TextStyle.f() = copy(
        fontFamily = family ?: fontFamily,
        fontSize = fontSize * s.textScale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * s.textScale else lineHeight,
        fontWeight = FontWeight(((fontWeight ?: FontWeight.Normal).weight + s.weightDelta).coerceIn(100, 900)),
        letterSpacing = if (s.letterSpacing == 0f) letterSpacing else s.letterSpacing.sp,
    )
    return Typography(
        displayLarge = base.displayLarge.f(), displayMedium = base.displayMedium.f(), displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(), headlineMedium = base.headlineMedium.f(), headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(), titleMedium = base.titleMedium.f(), titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(), bodyMedium = base.bodyMedium.f(), bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(), labelMedium = base.labelMedium.f(), labelSmall = base.labelSmall.f(),
    )
}

/**
 * The app font for Hiatus, Minutia and Regista (Aria keeps it in its own settings store): observable, in prefs
 * "kit_font", so the theme switches the moment it changes.
 */
object AppFont {
    var settings by mutableStateOf(FontSettings())
        private set
    private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("kit_font", Context.MODE_PRIVATE)

    /** [legacyFont]: the Google Font the app used before the kit, read once if nothing is saved yet. */
    fun load(ctx: Context, legacyFont: () -> String? = { null }) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        if (!p.contains("v")) {
            val old = runCatching(legacyFont).getOrNull()?.takeIf { it.isNotBlank() }
            set(ctx, FontSettings(font = old))
            return
        }
        settings = FontSettings(
            font = p.getString("font", null)?.takeIf { it.isNotBlank() },
            textScale = p.getFloat("scale", 1f).coerceIn(0.8f, 1.3f),
            weightDelta = p.getInt("weight", 0).coerceIn(-300, 300),
            letterSpacing = p.getFloat("spacing", 0f).coerceIn(-5f, 5f),
        )
    }

    fun set(ctx: Context, s: FontSettings) {
        settings = s
        prefs(ctx).edit().putInt("v", 1).putString("font", s.font).putFloat("scale", s.textScale)
            .putInt("weight", s.weightDelta).putFloat("spacing", s.letterSpacing).apply()
    }

    /** Screenshots only. */
    fun force(s: FontSettings) { loaded = true; settings = s }
}

// ------------------------------------------------------------------ catalogue

/** One Google Fonts family. [popularity]: 1 = most used. */
data class FontEntry(val name: String, val category: FontCategory, val popularity: Int)

enum class FontCategory(val label: String) {
    Sans("Sans"), Serif("Serif"), Display("Display"), Handwriting("Handwriting"), Mono("Mono");

    companion object {
        fun of(raw: String): FontCategory = when (raw.lowercase()) {
            "serif" -> Serif
            "display" -> Display
            "handwriting" -> Handwriting
            "monospace" -> Mono
            else -> Sans
        }
    }
}

/** Shown when the full catalogue can't be fetched yet (offline on first use). */
val FeaturedFonts = listOf(
    "Inter", "Manrope", "Outfit", "DM Sans", "Plus Jakarta Sans", "Figtree",
    "Space Grotesk", "Sora", "Lexend", "Urbanist", "Rubik", "Poppins",
    "Nunito", "Work Sans", "IBM Plex Sans", "JetBrains Mono", "Fragment Mono", "Fraunces", "Playfair Display",
)

/**
 * The whole Google Fonts catalogue (about 1,900 families) from Google's public font list, no API key. Cached
 * and refreshed weekly; offline before the first fetch it falls back to [FeaturedFonts].
 */
class FontCatalog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "kit_fonts.json")

    suspend fun load(): List<FontEntry> = withContext(Dispatchers.IO) {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS
        if (!fresh) runCatching { file.writeText(download()) }
        runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback()
    }

    private fun download(): String {
        val conn = URL("https://fonts.google.com/metadata/fonts").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text).also { require(it.size > 100) } // sanity check before caching
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): List<FontEntry> {
        // The response starts with an anti-JSON-hijacking prefix: skip to the first '{'.
        val json = JSONObject(text.substring(text.indexOf('{')))
        val list = json.getJSONArray("familyMetadataList")
        return (0 until list.length()).map { list.getJSONObject(it) }
            .map { FontEntry(it.getString("family"), FontCategory.of(it.optString("category")), it.optInt("popularity", Int.MAX_VALUE)) }
            .sortedBy { it.popularity }
    }

    private fun fallback() = FeaturedFonts.mapIndexed { i, name ->
        val cat = when (name) {
            "Fraunces", "Playfair Display" -> FontCategory.Serif
            "JetBrains Mono", "Fragment Mono" -> FontCategory.Mono
            else -> FontCategory.Sans
        }
        FontEntry(name, cat, i)
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** Google Play Services' font provider certificates (debug, then release), as Android's font_certs.xml has them. */
private val CERTS = listOf(
    "" +
        "MIIEqDCCA5CgAwIBAgIJANWFuGx90071MA0GCSqGSIb3DQEBBAUAMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5p" +
        "YTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5k" +
        "cm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTAeFw0wODA0MTUyMzM2NTZaFw0zNTA5MDEyMzM2NTZaMIGU" +
        "MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9p" +
        "ZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTCC" +
        "ASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBANbOLggKv+IxTdGNs8/TGFy0PTP6DHThvbbR24kT9ixcOd9W+EaBPWW+wPPK" +
        "QmsHxajtWjmQwWfna8mZuSeJS48LIgAZlKkpFeVyxW0qMBujb8X8ETrWy550NaFtI6t9+u7hZeTfHwqNvacKhp1RbE6dBRGWynwM" +
        "VX8XW8N1+UjFaq6GCJukT4qmpN2afb8sCjUigq0GuMwYXrFVee74bQgLHWGJwPmvmLHC69EH6kWr22ijx4OKXlSIx2xT1AsSHee7" +
        "0w5iDBiK4aph27yH3TxkXy9V89TDdexAcKk/cVHYNnDBapcavl7y0RiQ4biu8ymM8Ga/nmzhRKya6G0cGw8CAQOjgfwwgfkwHQYD" +
        "VR0OBBYEFI0cxb6VTEM8YYY6FbBMvAPyT+CyMIHJBgNVHSMEgcEwgb6AFI0cxb6VTEM8YYY6FbBMvAPyT+CyoYGapIGXMIGUMQsw" +
        "CQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQ" +
        "MA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbYIJANWF" +
        "uGx90071MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEEBQADggEBABnTDPEF+3iSP0wNfdIjIz1AlnrPzgAIHVvXxunW7SBrDhEg" +
        "lQZBbKJEk5kT0mtKoOD1JMrSu1xuTKEBahWRbqHsXclaXjoBADb0kkjVEJu/Lh5hgYZnOjvlba8Ld7HCKePCVePoTJBdI4fvugnL" +
        "8TsgK05aIskyY0hKI9L8KfqfGTl1lzOv2KoWD0KWwtAWPoGChZxmQ+nBli+gwYMzM1vAkP+aayLe0a1EQimlOalO762r0GXO0ks+" +
        "UeXde2Z4e+8S/pf7pITEI/tP+MxJTALw9QUWEv9lKTk+jkbqxbsh8nfBUapfKqYn0eidpwq2AzVp3juYl7//fKnaPhJD9gs=",
    "" +
        "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlh" +
        "MRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMH" +
        "QW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlh" +
        "MRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMH" +
        "QW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZA" +
        "pxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVK" +
        "ZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE" +
        "28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOj" +
        "gdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXik" +
        "djB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29v" +
        "Z2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqG" +
        "SIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjw" +
        "yxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd" +
        "239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KR" +
        "kEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK",
)
