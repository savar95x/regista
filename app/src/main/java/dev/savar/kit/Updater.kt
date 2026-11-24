package dev.savar.kit

// Shared by Aria, Minutia, Hiatus and Regista. This file is byte-identical in all four repos:
// change it in one, copy it to the others (see KIT.md).

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** A GitHub release: version ("0.12.0"), its notes, and the APK ([size] -1 when unknown). */
data class Release(val version: String, val notes: String, val url: String, val size: Long)

/**
 * In-app updates from the app's GitHub Releases, the same in all four apps.
 * - Checks every time the app comes to the front (at most every 10 minutes; Settings can force one). The first
 *   look is github.com/…/releases/latest, which redirects to the newest tag: no API, so no rate limit. Only if
 *   that fails does it ask the API (with an ETag, so an unchanged answer is free).
 * - Each APK is downloaded once into app storage, resumed if interrupted, reused if already there, and deleted
 *   once that version is installed. On unmetered networks it's fetched ahead, so Update installs at once.
 * - Installs through Android's PackageInstaller after checking the file is this app and newer. Once the app has
 *   installed itself this way, Android 12+ installs later updates without asking.
 * - Shown as a card on the app's home ([UpdateCard]) and a row in Settings → About ([UpdateRow]), never a pop-up.
 */
object Updates {
    private const val CHECK_EVERY_MS = 10 * 60_000L

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release) : State
        data class Installing(val release: Release) : State
        data class Failed(val release: Release?, val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    /** When the last check finished (0 = not this session). */
    var lastCheck by mutableStateOf(0L)
        private set

    /** Versions put off with "Later": hidden from the home card until the app is next started. */
    internal val later = mutableSetOf<String>()

    private var waitingForPermission = false
    private var cleanedLegacy = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("kit_updates", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.noBackupFilesDir, "kit-updates").apply { mkdirs() }
    private fun apk(ctx: Context, v: String) = File(dir(ctx), "update-$v.apk")
    private fun part(ctx: Context, v: String) = File(dir(ctx), "update-$v.apk.part")

    fun installed(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    /** "0.10.0" > "0.9.3"; a leading "v" is ignored. */
    fun newer(a: String, b: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split('.', '-').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun online(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun unmetered(ctx: Context): Boolean = runCatching {
        !ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
    }.getOrDefault(false)

    /** Call whenever the app comes to the front: checks if due, and installs if we were waiting on permission. */
    fun onResume(ctx: Context) {
        val app = ctx.applicationContext
        if (waitingForPermission && canInstall(app) && state is State.Ready) {
            waitingForPermission = false
            install(app)
            return
        }
        if (System.currentTimeMillis() - lastCheck >= CHECK_EVERY_MS) check(app)
    }

    /** [force] = asked for in Settings: always asks GitHub and reports "up to date" or the problem. */
    fun check(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        val s = state
        if (s is State.Checking || s is State.Downloading || s is State.Installing) return
        scope.launch {
            lock.withLock {
                cleanup(app)
                if (!online(app)) {
                    if (force) setState(State.Failed(null, "No connection"))
                    return@withLock
                }
                if (force) setState(State.Checking)
                val installed = installed(app)
                val found = runCatching { viaRedirect(app, installed) }.recoverCatching { viaApi(app) }
                withContext(Dispatchers.Main) { lastCheck = System.currentTimeMillis() }
                val r = found.getOrElse { e ->
                    if (force || state is State.Checking) setState(State.Failed(null, describe(e)))
                    return@withLock
                }
                val now = state
                if (now is State.Downloading || now is State.Installing) return@withLock
                val next = when {
                    r == null || r.url.isBlank() || !newer(r.version, installed) -> if (force) State.UpToDate else State.Idle
                    complete(app, r) -> State.Ready(r)
                    else -> State.Available(r)
                }
                setState(next)
                if (next is State.Available && unmetered(app)) runCatching { download(app, next.release) }
            }
        }
    }

    private suspend fun setState(s: State) = withContext(Dispatchers.Main) { state = s }

    private fun describe(e: Throwable): String = when {
        e is HttpError && (e.code == 403 || e.code == 429) -> "GitHub is limiting requests, try again later"
        e is java.net.UnknownHostException || e is java.net.ConnectException -> "No connection"
        e is java.net.SocketTimeoutException -> "GitHub didn't answer in time"
        else -> "Couldn't reach GitHub"
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    private fun open(ctx: Context, url: String, follow: Boolean = true) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = follow
            setRequestProperty("User-Agent", Kit.config.name + "/" + installed(ctx) + " (+https://github.com/" + Kit.config.repo + ")")
        }

    /** The latest tag from the releases/latest redirect; null when it's not newer than [installed]. */
    private fun viaRedirect(ctx: Context, installed: String): Release? {
        val repo = Kit.config.repo
        val c = open(ctx, "https://github.com/$repo/releases/latest", follow = false)
        val location = try {
            if (c.responseCode !in 300..399) throw HttpError(c.responseCode)
            c.getHeaderField("Location") ?: throw IOException("No redirect")
        } finally { c.disconnect() }
        val tag = location.substringAfterLast("/tag/", "").substringBefore('?')
        if (tag.isBlank()) throw IOException("Unexpected release URL")
        val version = tag.removePrefix("v")
        if (!newer(version, installed)) return null
        val url = "https://github.com/$repo/releases/download/$tag/" + Kit.config.apkName(version)
        // Size from a 1-byte ranged GET ("Content-Range: bytes 0-0/2738139"): GitHub's storage refuses HEAD.
        val size = runCatching {
            val h = open(ctx, url).apply { setRequestProperty("Range", "bytes=0-0") }
            try {
                when (h.responseCode) {
                    206 -> h.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                    200 -> h.contentLengthLong
                    else -> throw HttpError(h.responseCode)
                }
            } finally { h.disconnect() }
        }.getOrElse { e -> if (e is HttpError && e.code == 404) throw IOException("Release has no APK yet") else -1L }
        val notes = runCatching {
            if (Kit.config.changelogNotes) {
                val md = get(ctx, "https://raw.githubusercontent.com/$repo/$tag/CHANGELOG.md")
                md.substringAfter("## [$version]", "").substringAfter('\n').substringBefore("\n## [")
            } else {
                JSONObject(get(ctx, "https://api.github.com/repos/$repo/releases/tags/$tag")).optString("body")
            }
        }.getOrDefault("")
        return Release(version, cleanNotes(notes), url, size)
    }

    private fun viaApi(ctx: Context): Release? {
        val p = prefs(ctx)
        val c = open(ctx, "https://api.github.com/repos/${Kit.config.repo}/releases/latest").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            p.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
        }
        val body = try {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.also { text ->
                    p.edit().putString("etag", c.getHeaderField("ETag")).putString("release", text).apply()
                }
                304 -> p.getString("release", null) ?: throw IOException("Cache missing")
                else -> throw HttpError(c.responseCode)
            }
        } finally { c.disconnect() }
        val o = JSONObject(body)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.optString("tag_name").removePrefix("v")
        val assets = o.optJSONArray("assets")
        val asset = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
        var notes = o.optString("body")
        if (Kit.config.changelogNotes) notes = notes.substringAfter("## [$version]", notes).substringBefore("\n## [")
        return Release(version, cleanNotes(notes), asset?.optString("browser_download_url").orEmpty(), asset?.optLong("size") ?: -1L)
    }

    private fun get(ctx: Context, url: String): String {
        val c = open(ctx, url)
        try {
            if (c.responseCode !in 200..299) throw HttpError(c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** Notes as plain bullets: markdown emphasis and code marks dropped, "- " / "* " shown as "• ". */
    private fun cleanNotes(md: String): String = md.lines()
        .map { it.replace("**", "").replace("`", "").trimEnd() }
        .map { it.replaceFirst(Regex("^(\\s*)[-*] "), "$1• ") }
        .filter { !it.startsWith("#") }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun complete(ctx: Context, r: Release): Boolean {
        val f = apk(ctx, r.version)
        return f.exists() && f.length() > 0 && (r.size <= 0 || f.length() == r.size)
    }

    /** Deletes APKs that are no longer newer than the installed app, and the old per-app update folders. */
    private fun cleanup(ctx: Context) {
        val current = installed(ctx)
        dir(ctx).listFiles()?.forEach { f ->
            val v = f.name.removePrefix("update-").removeSuffix(".part").removeSuffix(".apk")
            if (!newer(v, current)) f.delete()
        }
        if (!cleanedLegacy) {
            cleanedLegacy = true
            listOf(File(ctx.filesDir, "updates"), File(ctx.cacheDir, "updates"), File(ctx.noBackupFilesDir, "updates"))
                .forEach { runCatching { it.deleteRecursively() } }
        }
    }

    /** The Update button: download if needed (resuming a partial file), then install. */
    fun update(ctx: Context) {
        val app = ctx.applicationContext
        val r = when (val s = state) {
            is State.Available -> s.release
            is State.Ready -> s.release
            is State.Failed -> s.release ?: return check(app, force = true)
            else -> return
        }
        scope.launch {
            lock.withLock {
                val ok = runCatching { if (!complete(app, r)) download(app, r) }
                ok.exceptionOrNull()?.let { e ->
                    setState(State.Failed(r, e.message?.takeIf { e !is IOException || it.startsWith("Download") } ?: "Download paused, try again to resume"))
                    return@withLock
                }
            }
            withContext(Dispatchers.Main) { install(app) }
        }
    }

    /** Downloads (or resumes) [r] into app storage and checks it's really an update of this app. */
    private suspend fun download(ctx: Context, r: Release) {
        val tmp = part(ctx, r.version)
        var have = if (tmp.exists()) tmp.length() else 0L
        if (r.size in 1 until have) { tmp.delete(); have = 0 }
        setState(State.Downloading(r, if (r.size > 0) have.toFloat() / r.size else 0f))
        if (r.size <= 0 || have < r.size) {
            val c = open(ctx, r.url).apply {
                readTimeout = 30_000
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                when (c.responseCode) {
                    206 -> {}
                    200 -> have = 0 // the server ignored the range: start over
                    else -> throw HttpError(c.responseCode)
                }
                val total = if (r.size > 0) r.size else have + c.contentLengthLong
                RandomAccessFile(tmp, "rw").use { out ->
                    out.setLength(have); out.seek(have)
                    c.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var shown = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            have += n
                            if (have - shown > 128 * 1024 && total > 0) {
                                shown = have
                                setState(State.Downloading(r, (have.toFloat() / total).coerceIn(0f, 1f)))
                            }
                        }
                    }
                }
            } finally { c.disconnect() }
        }
        if (r.size > 0 && tmp.length() != r.size) throw IOException("Download incomplete, try again to resume")
        if (tmp.length() == 0L) throw IOException("Download failed, try again")
        @Suppress("DEPRECATION")
        val info = runCatching { ctx.packageManager.getPackageArchiveInfo(tmp.path, 0) }.getOrNull()
        if (info == null || info.packageName != ctx.packageName || !newer(info.versionName ?: "", installed(ctx))) {
            tmp.delete()
            throw IllegalStateException("That download wasn't a valid update")
        }
        val dest = apk(ctx, r.version)
        dest.delete()
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        setState(State.Ready(r))
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Installs the downloaded APK. The first time, Android asks to allow "Install unknown apps"; we carry on after. */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val s = state as? State.Ready ?: return
        if (!canInstall(app)) {
            waitingForPermission = true
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val file = apk(app, s.release.version)
        state = State.Installing(s.release)
        scope.launch {
            val err = runCatching {
                val pi = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    setSize(file.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = pi.createSession(params)
                pi.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(app, id, Intent(app, UpdateReceiver::class.java), flags)
                    session.commit(pending.intentSender)
                }
            }.exceptionOrNull()
            if (err != null) setState(State.Failed(s.release, "Couldn't start the install"))
        }
    }

    internal fun installResult(ctx: Context, ok: Boolean, message: String?) {
        val r = (state as? State.Installing)?.release ?: return
        state = when {
            ok -> State.Idle
            complete(ctx, r) && message == null -> State.Ready(r) // cancelled: the APK stays, no re-download
            else -> State.Failed(r, message ?: "Install failed")
        }
    }

    /** Screenshots only. */
    fun force(s: State) { state = s }
}

/** PackageInstaller's answer: show Android's confirmation if it needs one, else report the result. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updates.installResult(ctx, true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updates.installResult(ctx, false, null)
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Updates.installResult(
                    ctx, false,
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) "Signature mismatch" + (msg?.let { ": $it" } ?: "")
                    else msg ?: "Install failed",
                )
            }
        }
    }
}
