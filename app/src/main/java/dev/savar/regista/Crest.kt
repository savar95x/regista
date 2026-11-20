package dev.savar.regista

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Tiny crest cache: downloaded once to disk, decoded small, kept in memory. */
object Crests {
    private val mem = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val gate = Semaphore(4)
    private val failed = HashMap<String, Long>() // url -> when it failed; retried after RETRY_MS
    private const val RETRY_MS = 30 * 60_000L

    private fun file(ctx: Context, url: String) = File(File(ctx.cacheDir, "crests").apply { mkdirs() }, "${url.hashCode().toUInt()}.img")

    /** Screenshot tests: never touch the network or decode on background threads. */
    @Volatile var offline = false

    fun cached(url: String): Bitmap? = mem.get(url)

    suspend fun load(ctx: Context, url: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || offline) return@withContext null
        mem.get(url)?.let { return@withContext it }
        val failedAt = synchronized(failed) { failed[url] }
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_MS) return@withContext null
        val f = file(ctx, url)
        var bmp = if (f.exists() && f.length() > 0) runCatching { decode(f, px) }.getOrNull() else null
        if (bmp == null) {
            f.delete() // missing or corrupt: fetch again
            gate.withPermit { runCatching { Http.download(url, f) } }
            bmp = runCatching { decode(f, px) }.getOrNull()
            if (bmp == null) f.delete()
        }
        synchronized(failed) { if (bmp == null) failed[url] = System.currentTimeMillis() else failed.remove(url) }
        if (bmp != null) mem.put(url, bmp)
        bmp
    }

    private fun decode(f: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Team crest (or flag), with a tonal monogram while loading or if there's none. */
@Composable
fun Crest(team: Team, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val urls = Store.logosFor(team)
    val url = urls.joinToString("|")
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() } * 2
    val inPreview = LocalInspectionMode.current
    key(team.id, team.name, url) {
        val image by produceState(urls.firstNotNullOfOrNull { Crests.cached(it) }?.asImageBitmap(), url) {
            value = urls.firstNotNullOfOrNull { Crests.cached(it) }?.asImageBitmap()
            if (value == null && !inPreview) {
                for (u in urls) {
                    val b = Crests.load(ctx, u, px) ?: continue
                    value = b.asImageBitmap()
                    break
                }
            }
        }
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Crossfade(image, label = "crest") { img ->
                if (img != null) {
                    Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
                } else {
                    Monogram(team, size)
                }
            }
        }
    }
}

@Composable
private fun Monogram(team: Team, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    val letters = team.abbr.ifBlank { team.short.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("") }
        .take(3)
    Box(
        Modifier.size(size).clip(CircleShape).background(scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letters,
            color = scheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * if (letters.length > 2) 0.28f else 0.36f).sp,
            maxLines = 1,
        )
    }
}
