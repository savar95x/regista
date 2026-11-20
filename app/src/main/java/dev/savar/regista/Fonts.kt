package dev.savar.regista

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The app font: the system font by default, a handful of picks, or any Google Fonts family (downloaded once at
 * four weights and kept on disk). Catalogue, preview and download code adapted from Minutia/Hiatus.
 */
object Fonts {
    /** Curated picks shown as chips. Any Google Fonts family works through the browser. */
    val picks = listOf("Google Sans Flex", "Inter", "Manrope", "DM Sans", "Outfit", "Space Grotesk", "Plus Jakarta Sans", "Figtree")

    private val WEIGHTS = listOf(400, 500, 600, 700)

    /** Selected Google family, "" for the system font. Observable so the theme swaps instantly. */
    var family by mutableStateOf("")
        private set
    var fontFamily by mutableStateOf<FontFamily?>(null)
        private set
    private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("fonts", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }
    private fun safe(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")
    private fun file(ctx: Context, fam: String, w: Int) = File(dir(ctx), "g_${safe(fam)}_$w.ttf")

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        family = prefs(ctx).getString("family", "") ?: ""
        fontFamily = build(ctx, family)
    }

    fun useSystem(ctx: Context) {
        family = ""
        fontFamily = null
        prefs(ctx).edit().putString("family", "").apply()
    }

    fun isDownloaded(ctx: Context, fam: String) = looksLikeFont(file(ctx, fam, 400))

    /** Downloads (if needed) and applies [fam]. Fails only if even the regular weight can't be fetched. */
    suspend fun use(ctx: Context, fam: String): Result<Unit> = withContext(Dispatchers.IO) {
        val name = catalogueName(ctx, fam)
        for (w in WEIGHTS) {
            val f = file(ctx, name, w)
            if (looksLikeFont(f)) continue
            val ok = download(name, w, f)
            if (!ok && w == 400) {
                return@withContext Result.failure(Exception(if (cachedFamilies(ctx).isEmpty()) "No connection" else "Couldn't download $name"))
            }
        }
        val built = build(ctx, name) ?: return@withContext Result.failure(Exception("Couldn't load $name"))
        withContext(Dispatchers.Main) {
            family = name
            fontFamily = built
            prefs(ctx).edit().putString("family", name).apply()
        }
        Result.success(Unit)
    }

    @OptIn(ExperimentalTextApi::class)
    private fun build(ctx: Context, fam: String): FontFamily? {
        if (fam.isBlank()) return null
        val have = WEIGHTS.filter { looksLikeFont(file(ctx, fam, it)) }
        if (400 !in have) return null
        // Every weight the theme uses gets a file: missing ones borrow the nearest downloaded weight.
        val fonts = (300..800 step 100).map { w ->
            val use = have.minBy { kotlin.math.abs(it - w) }
            Font(file(ctx, fam, use), FontWeight(w))
        }
        return runCatching { FontFamily(fonts) }.getOrNull()
    }

    private fun download(name: String, weight: Int, dest: File): Boolean {
        val enc = URLEncoder.encode(name, "UTF-8")
        for (ua in USER_AGENTS) {
            try {
                val css = httpGetText("https://fonts.googleapis.com/css2?family=$enc:wght@$weight", ua)
                val src = Regex("""src:\s*url\(['"]?([^)'"\s]+)['"]?\)""").find(css)?.groupValues?.get(1) ?: continue
                val tmp = File(dest.parentFile, dest.name + ".part")
                httpDownload(src, tmp, ua)
                if (looksLikeFont(tmp)) {
                    tmp.renameTo(dest)
                    return true
                }
                tmp.delete()
            } catch (e: Exception) {
                // try the next user agent
            }
        }
        return false
    }

    // ---------- catalogue (~1,900 families, most popular first) ----------

    data class Family(val name: String, val category: String)

    @Volatile private var entries: List<Family>? = null
    private fun catalogueFile(ctx: Context) = File(dir(ctx), "catalogue2.tsv")

    fun cachedFamilies(ctx: Context): List<Family> {
        entries?.let { return it }
        val f = catalogueFile(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts[0].isBlank()) null else Family(parts[0], parts.getOrElse(1) { "" })
        }.also { entries = it }
    }

    private fun catalogueName(ctx: Context, typed: String): String =
        cachedFamilies(ctx).firstOrNull { it.name.equals(typed.trim(), ignoreCase = true) }?.name ?: typed.trim()

    suspend fun loadCatalogue(ctx: Context): List<Family> = withContext(Dispatchers.IO) {
        val f = catalogueFile(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 86_400_000L
        if (!fresh) {
            runCatching {
                val text = httpGetText("https://fonts.google.com/metadata/fonts", USER_AGENTS[0])
                val root = org.json.JSONObject(text.substring(text.indexOf('{')))
                val arr = root.getJSONArray("familyMetadataList")
                val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filterNot { it.optBoolean("isBrandFont", false) }
                    .map { Triple(it.optString("family"), it.optString("category"), it.optInt("popularity", Int.MAX_VALUE)) }
                    .filter { it.first.isNotBlank() }
                    .sortedBy { it.third }
                    .map { Family(it.first, it.second) }
                if (list.size > 500) {
                    f.writeText(list.joinToString("\n") { "${it.name}\t${it.category}" })
                    entries = list
                }
            }
        }
        cachedFamilies(ctx)
    }

    // ---------- tiny preview fonts for the browser ----------

    private val previewGate = kotlinx.coroutines.sync.Semaphore(4)
    private val previewCache = HashMap<String, Typeface?>()
    const val PREVIEW_TEXT = "Kick-off 20:45"

    suspend fun previewTypeface(ctx: Context, fam: String): Typeface? = withContext(Dispatchers.IO) {
        synchronized(previewCache) { if (previewCache.containsKey(fam)) return@withContext previewCache[fam] }
        val pdir = File(dir(ctx), "preview").apply { mkdirs() }
        val file = File(pdir, "${safe(fam)}.ttf")
        if (!looksLikeFont(file)) {
            previewGate.acquire()
            try {
                val chars = (PREVIEW_TEXT + fam).toSet().joinToString("")
                val url = "https://fonts.googleapis.com/css2?family=${URLEncoder.encode(fam, "UTF-8")}" +
                    "&text=${URLEncoder.encode(chars, "UTF-8")}"
                for (ua in USER_AGENTS) {
                    try {
                        val css = httpGetText(url, ua)
                        val src = Regex("""src:\s*url\(['"]?([^)'"\s]+)['"]?\)""").find(css)?.groupValues?.get(1) ?: continue
                        httpDownload(src, file, ua)
                        if (looksLikeFont(file)) break else file.delete()
                    } catch (e: Exception) {
                        file.delete()
                    }
                }
            } finally {
                previewGate.release()
            }
        }
        val tf = if (looksLikeFont(file)) runCatching { Typeface.createFromFile(file) }.getOrNull() else null
        synchronized(previewCache) { previewCache[fam] = tf }
        tf
    }

    /** TrueType / OpenType magic numbers. WOFF/WOFF2 aren't supported. */
    private fun looksLikeFont(f: File): Boolean {
        if (!f.exists() || f.length() < 12) return false
        val head = ByteArray(4)
        f.inputStream().use { if (it.read(head) != 4) return false }
        val tag = String(head, Charsets.ISO_8859_1)
        val isTrueType = head[0] == 0.toByte() && head[1] == 1.toByte() && head[2] == 0.toByte() && head[3] == 0.toByte()
        return isTrueType || tag == "OTTO" || tag == "true" || tag == "ttcf"
    }

    // Both get plain TrueType from fonts.googleapis.com (newer UAs get WOFF2), verified in Minutia's CI.
    private val USER_AGENTS = listOf(
        "curl/8.5.0",
        "Mozilla/5.0 (Linux; U; Android 4.0.3; en-us) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30",
    )

    private fun open(url: String, ua: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("User-Agent", ua)
        }

    private fun httpGetText(url: String, ua: String): String {
        val c = open(url, ua)
        try {
            if (c.responseCode != 200) throw HttpStatusException(c.responseCode)
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun httpDownload(url: String, dest: File, ua: String) {
        val c = open(url, ua)
        try {
            if (c.responseCode != 200) throw HttpStatusException(c.responseCode)
            c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        } finally {
            c.disconnect()
        }
    }
}
