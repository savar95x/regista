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

    private suspend fun doCheck(ctx: Context) {
        val busy = state is State.Downloading || state is State.Installing
        val p = prefs(ctx)
        val conn = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Regista-updater")
            p.getString("etag", null)?.let { setRequestProperty("If-None-Match", it) }
        }
        val body: String = try {
            when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().use { it.readText() }.also { text ->
                    p.edit().putString("etag", conn.getHeaderField("ETag")).putString("release", text).apply()
                }
                304 -> p.getString("release", null) ?: return
                else -> return
            }
        } finally {
            conn.disconnect()
        }
        withContext(Dispatchers.Main) { lastCheck = System.currentTimeMillis() }

        val o = JSONObject(body)
        val version = o.optString("tag_name").removePrefix("v")
        val asset = o.optJSONArray("assets").mapObjects { it }.firstOrNull { it.optString("name").endsWith(".apk") }
        val installed = installedVersion(ctx)
        cleanup(ctx, installed)
        if (busy) return
        val next = when {
            asset == null || !newer(version, installed) -> State.UpToDate
            else -> {
                val rel = Release(version, cleanNotes(o.optString("body")), asset.optString("browser_download_url"), asset.optLong("size"))
                if (isComplete(ctx, rel)) State.Ready(rel) else State.Available(rel)
            }
        }
        withContext(Dispatchers.Main) { state = next }
    }

    /** Release notes come from CHANGELOG.md: drop markdown emphasis, keep the bullet text. */
    private fun cleanNotes(md: String): String = md.lines()
        .map { it.replace("**", "").replace("`", "").trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun isComplete(ctx: Context, rel: Release): Boolean {
        val f = apk(ctx, rel.version)
        return f.exists() && (rel.size <= 0 || f.length() == rel.size)
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
