package dev.savar.regista

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from this repo's GitHub Releases (the same APKs CI publishes).
 *
 * - Checks on every app open (at most every 10 minutes) with an ETag, so an unchanged answer is a tiny 304 that
 *   doesn't count against GitHub's rate limit.
 * - Downloads only when you tap Update, into `files/updates/Regista-X.Y.Z.apk`, resuming a partial file with a
 *   Range request. A finished file is reused (never fetched twice) and old ones are deleted once installed.
 * - Installs with a PackageInstaller session. The first update asks for confirmation (and "Install unknown apps"
 *   once); after that Regista is the installer of record, so on Android 12+ later updates need no prompt.
 */
object Updater {
    private const val REPO = "savar95x/regista"
    private const val CHECK_EVERY_MS = 10 * 60_000L
    const val ACTION_STATUS = "dev.savar.regista.INSTALL_STATUS"

    data class Release(val version: String, val notes: String, val url: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release) : State
        data class Installing(val release: Release) : State
        data class Failed(val release: Release?, val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set
    var lastCheck by mutableStateOf(0L)
        private set

    /** The user tapped Update but first had to allow "Install unknown apps"; resume when they come back. */
    var waitingForPermission = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "updates").apply { mkdirs() }
    private fun apk(ctx: Context, v: String) = File(dir(ctx), "Regista-$v.apk")
    private fun part(ctx: Context, v: String) = File(dir(ctx), "Regista-$v.apk.part")

    fun installedVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

    /** 1.2.10 > 1.2.9; anything unparsable counts as 0. */
    fun newer(a: String, b: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    fun dismissedVersion(ctx: Context): String = prefs(ctx).getString("dismissed", "") ?: ""
    fun dismiss(ctx: Context, v: String) = prefs(ctx).edit().putString("dismissed", v).apply()

    fun checkIfDue(ctx: Context) {
        if (System.currentTimeMillis() - lastCheck < CHECK_EVERY_MS) return
        check(ctx)
    }

    fun check(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { lock.withLock { runCatching { doCheck(app) } } }
    }

    /** Last check's problem, for Settings ("Couldn't check: …"). Null after a successful check. */
    var checkError by mutableStateOf<String?>(null)
        private set

    /**
     * Two independent ways to find the latest release, so one failing (GitHub's API allows only 60 anonymous
     * calls an hour per IP, which a mobile network's shared address can use up) doesn't hide an update:
     * 1. github.com/…/releases/latest redirects to the newest tag: no API, no rate limit. The APK URL follows
     *    from the tag, its size from a HEAD request, the notes from CHANGELOG.md at that tag.
     * 2. The GitHub API (with ETag), as before.
     */
    private suspend fun doCheck(ctx: Context) {
        val installed = installedVersion(ctx)
        cleanup(ctx, installed)
        val rel = runCatching { viaRedirect() }.recoverCatching { viaApi(ctx) }
        withContext(Dispatchers.Main) {
            lastCheck = System.currentTimeMillis()
            checkError = rel.exceptionOrNull()?.let { describe(it) }
        }
        val r = rel.getOrNull() ?: return
        if (state is State.Downloading || state is State.Installing) return
        val next = when {
            r.url.isBlank() || !newer(r.version, installed) -> State.UpToDate
            isComplete(ctx, r) -> State.Ready(r)
            else -> State.Available(r)
        }
        withContext(Dispatchers.Main) { state = next }
    }

    private fun describe(e: Throwable): String = when {
        e is HttpStatusException && (e.code == 403 || e.code == 429) -> "GitHub is limiting requests, will retry later"
        e is java.net.UnknownHostException || e is java.net.ConnectException -> "No connection"
        e is java.net.SocketTimeoutException -> "GitHub didn't answer in time"
        else -> e.message ?: "Couldn't reach GitHub"
    }

    private fun open(url: String, follow: Boolean = true, method: String = "GET") =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = follow
            requestMethod = method
            setRequestProperty("User-Agent", "Regista-updater (+https://github.com/$REPO)")
        }

    private fun viaRedirect(): Release {
        val c = open("https://github.com/$REPO/releases/latest", follow = false)
        val location = try {
            if (c.responseCode !in 300..399) throw HttpStatusException(c.responseCode)
            c.getHeaderField("Location") ?: throw Exception("No redirect")
        } finally {
            c.disconnect()
        }
        val tag = location.substringAfterLast("/tag/", "").substringBefore('?')
        if (!tag.startsWith("v")) throw Exception("Unexpected release URL")
        val version = tag.removePrefix("v")
        val url = "https://github.com/$REPO/releases/download/$tag/Regista-$version.apk"
        val size = runCatching {
            val h = open(url, method = "HEAD")
            try {
                if (h.responseCode != 200) throw HttpStatusException(h.responseCode)
                h.contentLengthLong
            } finally {
                h.disconnect()
            }
        }.getOrElse { e -> if (e is HttpStatusException && e.code == 404) throw Exception("Release has no APK yet") else -1L }
        val notes = runCatching {
            val md = Http.get("https://raw.githubusercontent.com/$REPO/$tag/CHANGELOG.md")
            md.substringAfter("## [$version]", "").substringAfter('\n').substringBefore("\n## [")
        }.getOrDefault("")
        return Release(version, cleanNotes(notes), url, size)
    }

    private fun viaApi(ctx: Context): Release {
        val p = prefs(ctx)
        val conn = open("https://api.github.com/repos/$REPO/releases/latest").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            p.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
        }
        val body: String = try {
            when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }.also { text ->
                    p.edit().putString("etag", conn.getHeaderField("ETag")).putString("release", text).apply()
                }
                304 -> p.getString("release", null) ?: throw Exception("Cache missing")
                else -> throw HttpStatusException(conn.responseCode)
            }
        } finally {
            conn.disconnect()
        }
        val o = JSONObject(body)
        val version = o.optString("tag_name").removePrefix("v")
        val asset = o.optJSONArray("assets").mapObjects { it }.firstOrNull { it.optString("name").endsWith(".apk") }
        return Release(version, cleanNotes(o.optString("body")), asset?.optString("browser_download_url").orEmpty(), asset?.optLong("size") ?: -1L)
    }

    // ---------- prompt once per version

    fun promptedVersion(ctx: Context): String = prefs(ctx).getString("prompted", "") ?: ""
    fun markPrompted(ctx: Context, v: String) = prefs(ctx).edit().putString("prompted", v).apply()

    /** Release notes come from CHANGELOG.md: drop markdown emphasis, keep the bullet text. */
    private fun cleanNotes(md: String): String = md.lines()
        .map { it.replace("**", "").replace("`", "").trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun isComplete(ctx: Context, rel: Release): Boolean {
        val f = apk(ctx, rel.version)
        return f.exists() && f.length() > 0 && (rel.size <= 0 || f.length() == rel.size)
    }

    /** Deletes APKs for versions already installed (or older), and stray partials of other versions. */
    private fun cleanup(ctx: Context, installed: String) {
        dir(ctx).listFiles()?.forEach { f ->
            val v = f.name.removePrefix("Regista-").removeSuffix(".part").removeSuffix(".apk")
            if (!newer(v, installed)) f.delete()
        }
    }

    /** Update button: download if needed (resuming), then install. */
    fun update(ctx: Context) {
        val app = ctx.applicationContext
        val rel = when (val s = state) {
            is State.Available -> s.release
            is State.Ready -> s.release
            is State.Failed -> s.release ?: return
            else -> return
        }
        scope.launch {
            lock.withLock {
                try {
                    if (!isComplete(app, rel)) download(app, rel)
                    install(app, rel)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { state = State.Failed(rel, e.message ?: "Update failed") }
                }
            }
        }
    }

    private suspend fun download(ctx: Context, rel: Release) {
        val tmp = part(ctx, rel.version)
        var have = if (tmp.exists()) tmp.length() else 0L
        if (rel.size in 1 until have) { tmp.delete(); have = 0 }
        withContext(Dispatchers.Main) { state = State.Downloading(rel, if (rel.size > 0) have.toFloat() / rel.size else 0f) }
        if (rel.size <= 0 || have < rel.size) fetch(rel, tmp, have)
        if (rel.size > 0 && tmp.length() != rel.size) throw Exception("Download incomplete, tap to resume")
        if (tmp.length() == 0L) throw Exception("Download failed, tap to retry")
        // Make sure it's really an update of this app before handing it to the installer.
        val info = ctx.packageManager.getPackageArchiveInfo(tmp.absolutePath, 0)
        if (info == null || info.packageName != ctx.packageName) {
            tmp.delete()
            throw Exception("Downloaded file isn't a Regista APK")
        }
        val dest = apk(ctx, rel.version)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        withContext(Dispatchers.Main) { state = State.Ready(rel) }
    }

    private suspend fun fetch(rel: Release, tmp: File, from: Long) {
        var have = from
        val conn = (URL(rel.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Regista-updater")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code != 200 && code != 206) throw HttpStatusException(code)
            if (code == 200) have = 0 // server ignored the range: start over
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.setLength(have)
                raf.seek(have)
                val buf = ByteArray(64 * 1024)
                var done = have
                var lastUi = 0L
                conn.inputStream.use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (rel.size > 0 && now - lastUi > 120) {
                            lastUi = now
                            val pr = done.toFloat() / rel.size
                            withContext(Dispatchers.Main) { state = State.Downloading(rel, pr) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun install(ctx: Context, rel: Release) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            waitingForPermission = true
            withContext(Dispatchers.Main) { state = State.Ready(rel) }
            val i = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { runCatching { ctx.startActivity(i) } }
            return
        }
        waitingForPermission = false
        withContext(Dispatchers.Main) { state = State.Installing(rel) }
        val file = apk(ctx, rel.version)
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out, 256 * 1024) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(
                ctx, id,
                Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS).putExtra("version", rel.version),
                flags,
            )
            session.commit(status.intentSender)
        }
    }

    /** Back in the app after granting "Install unknown apps": carry on with the install. */
    fun resumeAfterPermission(ctx: Context) {
        if (waitingForPermission && ctx.packageManager.canRequestPackageInstalls() && state is State.Ready) {
            waitingForPermission = false
            update(ctx)
        }
    }

    internal fun onStatus(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val rel = (state as? State.Installing)?.release
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { ctx.startActivity(it) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is replaced and restarted by the system
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                state = rel?.let { State.Ready(it) } ?: State.Idle // user cancelled: the APK stays, no re-download
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"
                state = State.Failed(rel, if (status == PackageInstaller.STATUS_FAILURE_CONFLICT) "Signature mismatch: $msg" else msg)
            }
        }
    }

    /** Screenshots only. */
    fun force(s: State) { state = s }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Updater.ACTION_STATUS) Updater.onStatus(context, intent)
    }
}
