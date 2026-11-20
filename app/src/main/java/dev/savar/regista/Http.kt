package dev.savar.regista

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** Plain HttpURLConnection: the app makes a few dozen small GETs every few hours, no need for a client library. */
object Http {
    private const val UA = "Regista/1.0 (Android; personal match reminders)"

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, text/csv, */*")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = open(url, headers)
        try {
            if (c.responseCode != 200) throw HttpStatusException(c.responseCode)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, dest: File) {
        val c = open(url, emptyMap())
        try {
            if (c.responseCode != 200) throw HttpStatusException(c.responseCode)
            val tmp = File(dest.parentFile, dest.name + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            c.disconnect()
        }
    }
}
