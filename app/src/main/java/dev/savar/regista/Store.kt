package dev.savar.regista

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SourcePref(val label: String) { AUTO("Automatic"), ESPN("ESPN only"), FOOTBALL_DATA("football-data.org only") }

data class Settings(
    val leadDay: Boolean = true,
    val leadHour: Boolean = true,
    val leadFive: Boolean = true,
    val bigMatchesOn: Boolean = true,
    val derbiesOn: Boolean = true,
    val knockoutsOn: Boolean = true,
    val eloTopN: Int = 20,
    val source: SourcePref = SourcePref.AUTO,
    val fdToken: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("leadDay", leadDay).put("leadHour", leadHour).put("leadFive", leadFive)
        .put("bigMatchesOn", bigMatchesOn).put("derbiesOn", derbiesOn).put("knockoutsOn", knockoutsOn)
        .put("eloTopN", eloTopN).put("source", source.name).put("fdToken", fdToken)

    companion object {
        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            val d = Settings()
            return Settings(
                leadDay = o.optBoolean("leadDay", d.leadDay),
                leadHour = o.optBoolean("leadHour", d.leadHour),
                leadFive = o.optBoolean("leadFive", d.leadFive),
                bigMatchesOn = o.optBoolean("bigMatchesOn", d.bigMatchesOn),
                derbiesOn = o.optBoolean("derbiesOn", d.derbiesOn),
                knockoutsOn = o.optBoolean("knockoutsOn", d.knockoutsOn),
                eloTopN = o.optInt("eloTopN", d.eloTopN),
                source = SourcePref.entries.firstOrNull { it.name == o.optString("source") } ?: d.source,
                fdToken = o.optString("fdToken"),
            )
        }
    }
}

/** Result of the last sync, shown in Settings and Up next. */
data class SyncState(val at: Long = 0, val source: String = "", val error: String? = null, val count: Int = 0)

/**
 * All app state: small things in SharedPreferences, fixture/team caches as JSON files. Every field is Compose
 * state, so screens update the moment a sync or a setting lands. Safe to [load] from receivers and jobs.
 */
object Store {
    var matches by mutableStateOf<List<Match>>(emptyList()); private set
    var follows by mutableStateOf<List<Follow>>(emptyList()); private set
    var bigTeams by mutableStateOf(Rules.defaultBigTeams); private set
    var muted by mutableStateOf<Set<String>>(emptySet()); private set
    var settings by mutableStateOf(Settings()); private set
    var catalog by mutableStateOf<List<Team>>(emptyList()); private set
    var eloRanks by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var sync by mutableStateOf(SyncState()); private set
    var syncing by mutableStateOf(false)
    var catalogAt = 0L; private set
    var eloAt = 0L; private set

    @Volatile private var loaded = false
    private lateinit var app: Context

    private fun prefs() = app.getSharedPreferences("store", Context.MODE_PRIVATE)
    private fun file(name: String) = File(app.filesDir, name)

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        app = ctx.applicationContext
        val p = prefs()
        settings = Settings.fromJson(p.getString("settings", null)?.let { runCatching { JSONObject(it) }.getOrNull() })
        follows = arr(p.getString("follows", null)).mapObjects(Follow::fromJson)
        p.getString("big", null)?.let { s -> bigTeams = arr(s).mapObjects(Team::fromJson) }
        muted = p.getStringSet("muted", emptySet())!!.toSet()
        sync = SyncState(p.getLong("syncAt", 0), p.getString("syncSource", "")!!, p.getString("syncError", null), p.getInt("syncCount", 0))
        catalogAt = p.getLong("catalogAt", 0)
        eloAt = p.getLong("eloAt", 0)
        matches = readArr("matches.json").mapObjects(Match::fromJson)
        catalog = readArr("teams.json").mapObjects(Team::fromJson)
        eloRanks = runCatching {
            val o = JSONObject(file("elo.json").readText())
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
        loaded = true
    }

    private fun arr(s: String?): JSONArray = s?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
    private fun readArr(name: String): JSONArray = runCatching { JSONArray(file(name).readText()) }.getOrDefault(JSONArray())
    private fun writeAtomic(name: String, text: String) {
        val tmp = file("$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file(name))) { file(name).writeText(text); tmp.delete() }
    }

    fun config(): RuleConfig = RuleConfig(
        follows = follows,
        bigTeams = bigTeams,
        bigMatchesOn = settings.bigMatchesOn,
        derbiesOn = settings.derbiesOn,
        knockoutsOn = settings.knockoutsOn,
        eloTopN = settings.eloTopN,
        eloRanks = eloRanks,
        muted = muted,
    )

    /** Upcoming relevant matches with reasons (also what reminders are scheduled from). */
    fun upNext(now: Long = System.currentTimeMillis()): List<Pair<Match, List<Reason>>> =
        Rules.relevant(matches.filter { it.kickoff > now - 2 * 3_600_000L }, config())

    // ---------------------------------------------------------------- mutations (persist + reschedule)

    private fun changed(ctx: Context) = Reminders.reschedule(ctx)

    fun updateSettings(ctx: Context, f: (Settings) -> Settings) {
        settings = f(settings)
        prefs().edit().putString("settings", settings.toJson().toString()).apply()
        changed(ctx)
    }

    fun follow(ctx: Context, team: Team, mode: FollowMode = FollowMode.ALL) {
        follows = follows.filterNot { it.team.id == team.id || it.team.key == team.key } + Follow(team, mode)
        saveFollows(); changed(ctx)
    }

    fun setMode(ctx: Context, team: Team, mode: FollowMode) {
        follows = follows.map { if (it.team.id == team.id) it.copy(mode = mode) else it }
        saveFollows(); changed(ctx)
    }

    fun unfollow(ctx: Context, team: Team) {
        follows = follows.filterNot { it.team.id == team.id }
        saveFollows(); changed(ctx)
    }

    fun isFollowing(team: Team) = follows.any { (it.team.id.isNotEmpty() && it.team.id == team.id) || it.team.key == team.key }

    private fun saveFollows() = prefs().edit().putString("follows", follows.toJsonArray { it.toJson() }.toString()).apply()

    fun addBig(ctx: Context, team: Team) {
        bigTeams = bigTeams.filterNot { it.key == team.key } + team
        saveBig(); changed(ctx)
    }

    fun removeBig(ctx: Context, team: Team) {
        bigTeams = bigTeams.filterNot { it.key == team.key }
        saveBig(); changed(ctx)
    }

    fun resetBig(ctx: Context) {
        bigTeams = Rules.defaultBigTeams
        prefs().edit().remove("big").apply(); changed(ctx)
    }

    private fun saveBig() = prefs().edit().putString("big", bigTeams.toJsonArray { it.toJson() }.toString()).apply()

    fun setMuted(ctx: Context, matchId: String, mute: Boolean) {
        muted = if (mute) muted + matchId else muted - matchId
        prefs().edit().putStringSet("muted", muted).apply()
        changed(ctx)
    }

    // ---------------------------------------------------------------- sync results

    fun saveMatches(list: List<Match>, source: String) {
        matches = list.sortedBy { it.kickoff }
        writeAtomic("matches.json", matches.toJsonArray { it.toJson() }.toString())
        // Forget mutes for matches that are gone.
        val ids = list.mapTo(HashSet()) { it.id }
        if (muted.any { it !in ids }) {
            muted = muted.filterTo(HashSet()) { it in ids }
            prefs().edit().putStringSet("muted", muted).apply()
        }
        recordSync(SyncState(System.currentTimeMillis(), source, null, list.size))
    }

    fun recordSync(s: SyncState) {
        sync = s
        prefs().edit().putLong("syncAt", s.at).putString("syncSource", s.source).putString("syncError", s.error)
            .putInt("syncCount", s.count).apply()
    }

    fun saveCatalog(list: List<Team>) {
        catalog = list
        catalogAt = System.currentTimeMillis()
        writeAtomic("teams.json", list.toJsonArray { it.toJson() }.toString())
        prefs().edit().putLong("catalogAt", catalogAt).apply()
    }

    fun saveElo(ranks: Map<String, Int>) {
        eloRanks = ranks
        eloAt = System.currentTimeMillis()
        writeAtomic("elo.json", JSONObject(ranks as Map<*, *>).toString())
        prefs().edit().putLong("eloAt", eloAt).apply()
    }

    /** Best crest for a team that may have been stored without one (e.g. the built-in big list). */
    fun logoFor(t: Team): String {
        if (t.logo.isNotEmpty()) return t.logo
        val k = t.key
        return catalog.firstOrNull { it.key == k && it.nation == t.nation }?.logo
            ?: matches.asSequence().flatMap { sequenceOf(it.home, it.away) }.firstOrNull { it.key == k }?.logo
            ?: ""
    }

    /** For tests and screenshots. */
    fun seed(
        matches: List<Match> = emptyList(),
        follows: List<Follow> = emptyList(),
        catalog: List<Team> = emptyList(),
        settings: Settings = Settings(),
        sync: SyncState = SyncState(),
    ) {
        this.matches = matches
        this.follows = follows
        this.catalog = catalog
        this.settings = settings
        this.sync = sync
        this.bigTeams = Rules.defaultBigTeams
        this.muted = emptySet()
        loaded = true
    }

    fun attach(ctx: Context) { app = ctx.applicationContext }
}
