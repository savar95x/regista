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
    private val failed = HashSet<String>()

    private fun file(ctx: Context, url: String) = File(File(ctx.cacheDir, "crests").apply { mkdirs() }, "${url.hashCode().toUInt()}.img")

    fun cached(url: String): Bitmap? = mem.get(url)

    suspend fun load(ctx: Context, url: String, px: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || synchronized(failed) { url in failed }) return@withContext null
        mem.get(url)?.let { return@withContext it }
        val f = file(ctx, url)
        if (!f.exists() || f.length() == 0L) {
            gate.withPermit { runCatching { Http.download(url, f) } }
        }
        val bmp = runCatching { decode(f, px) }.getOrNull()
        if (bmp == null) synchronized(failed) { failed += url } else mem.put(url, bmp)
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
    val url = Store.logoFor(team)
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() } * 2
    val inPreview = LocalInspectionMode.current
    val image by produceState<ImageBitmap?>(Crests.cached(url)?.asImageBitmap(), url) {
        if (value == null && !inPreview) value = Crests.load(ctx, url, px)?.asImageBitmap()
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
