package dev.savar.regista

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A player found by search, with the teams you could follow through them. */
data class PlayerHit(val id: String, val name: String, val subtitle: String, val club: Team?, val nation: Team?)

class SourceException(msg: String) : Exception(msg)

/**
 * ESPN's public site API: no key, every competition Regista cares about (including friendlies and the
 * Conference League), team ids shared across competitions. Unofficial, so everything is parsed defensively and
 * football-data.org stands by as the fallback.
 */
object Espn {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"
    private val DAY = DateTimeFormatter.BASIC_ISO_DATE

    fun parseTime(s: String): Long? =
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s.replace("Z", ":00Z")).toInstant().toEpochMilli() }.getOrNull()

    private fun team(o: JSONObject?, nation: Boolean): Team? {
        if (o == null) return null
        val id = o.optString("id")
        val name = o.optString("displayName").ifBlank { o.optString("name") }
        if (id.isBlank() || name.isBlank()) return null
        val logo = o.optString("logo").ifBlank {
            o.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty()
        }
        return Team(
            id = id,
            name = name,
            short = o.optString("shortDisplayName").ifBlank { name },
            abbr = o.optString("abbreviation"),
            logo = logo,
            nation = nation,
        )
    }

    /** Parses one scoreboard response. Public for the unit tests. */
    fun parseScoreboard(slug: String, json: String): List<Match> {
        val root = JSONObject(json)
        val leagueName = root.optJSONArray("leagues")?.optJSONObject(0)?.optString("name").orEmpty()
        val nation = Comps.isInternational(slug)
        return root.optJSONArray("events").mapObjects { e ->
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapObjects null
            val competitors = comp.optJSONArray("competitors").mapObjects { it }
            val homeO = competitors.firstOrNull { it.optString("homeAway") == "home" } ?: competitors.getOrNull(0)
            val awayO = competitors.firstOrNull { it.optString("homeAway") == "away" } ?: competitors.getOrNull(1)
            val home = team(homeO?.optJSONObject("team"), nation) ?: return@mapObjects null
            val away = team(awayO?.optJSONObject("team"), nation) ?: return@mapObjects null
            val kickoff = parseTime(e.optString("date").ifBlank { comp.optString("date") }) ?: return@mapObjects null
            val type = (comp.optJSONObject("status") ?: e.optJSONObject("status"))?.optJSONObject("type")
            val typeName = type?.optString("name").orEmpty().uppercase()
            val status = when {
                typeName.contains("POSTPONED") || typeName.contains("DELAYED") -> Status.POSTPONED
                typeName.contains("CANCEL") || typeName.contains("ABANDON") || typeName.contains("FORFEIT") -> Status.CANCELLED
                type?.optString("state") == "in" -> Status.LIVE
                type?.optString("state") == "post" || type?.optBoolean("completed") == true -> Status.FINISHED
                else -> Status.SCHEDULED
            }
            val notes = comp.optJSONArray("notes").mapObjects { it.optString("headline").takeIf { h -> h.isNotBlank() } }
            val seasonSlug = e.optJSONObject("season")?.optString("slug").orEmpty()
            val stageText = notes.firstOrNull().orEmpty()
            val rank = maxOf(Comps.stageRank(stageText), Comps.stageRank(seasonSlug))
            Match(
                id = e.optString("id").ifBlank { return@mapObjects null },
                comp = slug,
                compName = Comps.name(slug, leagueName.ifBlank { slug }),
                kickoff = kickoff,
                home = home,
                away = away,
                stage = stageText.ifBlank { seasonSlug.replace('-', ' ').replaceFirstChar { it.uppercase() } },
                stageRank = rank,
                venue = comp.optJSONObject("venue")?.optString("fullName").orEmpty(),
                status = status,
                timeTbd = comp.has("timeValid") && !comp.optBoolean("timeValid", true),
            )
        }
    }

    suspend fun matches(fromDay: LocalDate, toDay: LocalDate): List<Match> = withContext(Dispatchers.IO) {
        val range = "${fromDay.format(DAY)}-${toDay.format(DAY)}"
        val gate = Semaphore(4)
        var coreOk = 0
        val results = coroutineScope {
            Comps.all.map { c ->
                async {
                    gate.withPermit {
                        try {
                            val list = parseScoreboard(c.slug, Http.get("$BASE/${c.slug}/scoreboard?dates=$range&limit=500"))
                            if (c.core) synchronized(this@Espn) { coreOk++ }
                            list
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
        }
        // If even the big leagues didn't answer, ESPN is down or has changed: let the caller fall back.
        if (coreOk == 0) throw SourceException("ESPN didn't answer")
        results.flatten().distinctBy { it.id }
    }

    /** Teams for search: club league rosters plus national-team lists. */
    suspend fun teams(): List<Team> = withContext(Dispatchers.IO) {
        val gate = Semaphore(4)
        val lists = coroutineScope {
            (Comps.clubLeagues.map { it to false } + Comps.nationLists.map { it to true }).map { (slug, nation) ->
                async {
                    gate.withPermit {
                        runCatching {
                            val root = JSONObject(Http.get("$BASE/$slug/teams?limit=500"))
                            val leagues = root.optJSONArray("sports")?.optJSONObject(0)?.optJSONArray("leagues")
                            leagues?.optJSONObject(0)?.optJSONArray("teams").mapObjects { team(it.optJSONObject("team"), nation) }
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll()
        }
        lists.flatten().distinctBy { it.id }
    }

    /** Player search. ESPN's search endpoint isn't documented, so this is best-effort and never throws. */
    suspend fun searchPlayers(query: String, catalog: List<Team>): List<PlayerHit> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val hits = runCatching {
            val root = JSONObject(Http.get("https://site.web.api.espn.com/apis/search/v2?query=$q&limit=10&type=player"))
            root.optJSONArray("results").mapObjects { r ->
                if (!r.optString("type").equals("player", true)) null else r.optJSONArray("contents").mapObjects { it }
            }.flatten()
        }.getOrDefault(emptyList())
            .filter { c ->
                val sport = (c.optString("sport") + " " + c.optString("defaultLeagueSlug") + " " + c.optString("uid") +
                    " " + c.optJSONObject("link")?.optString("web").orEmpty()).lowercase()
                sport.contains("soccer") || sport.contains("s:600")
            }
            .take(6)

        coroutineScope {
            hits.map { c ->
                async {
                    val id = Regex("a:(\\d+)").find(c.optString("uid"))?.groupValues?.get(1)
                        ?: Regex("/id/(\\d+)").find(c.optJSONObject("link")?.optString("web").orEmpty())?.groupValues?.get(1)
                        ?: c.optString("id")
                    val name = c.optString("displayName").ifBlank { c.optString("name") }
                    val subtitle = c.optString("subtitle")
                    var club: Team? = catalog.firstOrNull { !it.nation && subtitle.isNotBlank() && Names.same(it.name, subtitle) }
                    var nation: Team? = null
                    if (id.isNotBlank()) {
                        runCatching {
                            val a = JSONObject(Http.get("https://site.web.api.espn.com/apis/common/v3/sports/soccer/athletes/$id"))
                                .optJSONObject("athlete")
                            val t = a?.optJSONObject("team")
                            if (t != null) {
                                val tid = t.optString("id")
                                club = catalog.firstOrNull { it.id == tid && !it.nation }
                                    ?: team(t, false)?.let { found -> catalog.firstOrNull { !it.nation && Names.same(it.name, found.name) } ?: found }
                                    ?: club
                            }
                            val country = a?.optString("citizenship").orEmpty()
                                .ifBlank { a?.optJSONObject("flag")?.optString("alt").orEmpty() }
                                .ifBlank { a?.optJSONObject("citizenshipCountry")?.optString("name").orEmpty() }
                            if (country.isNotBlank()) nation = catalog.firstOrNull { it.nation && Names.same(it.name, country) }
                        }
                    }
                    if (name.isBlank()) null else PlayerHit(id, name, subtitle, club, nation)
                }
            }.awaitAll().filterNotNull()
        }
    }
}

/**
 * football-data.org v4, the official fallback: free token, 10 calls/minute, top leagues + Champions League +
 * World Cup/Euro. Its team ids differ from ESPN's, so follows match it by name.
 */
object FootballData {
    private const val BASE = "https://api.football-data.org/v4"

    fun parse(json: String): List<Match> {
        val root = JSONObject(json)
        return root.optJSONArray("matches").mapObjects { m ->
            val comp = m.optJSONObject("competition")
            val code = comp?.optString("code").orEmpty()
            val slug = Comps.footballData[code] ?: "fd.$code"
            val nation = Comps.isInternational(slug)
            fun t(o: JSONObject?): Team? {
                if (o == null || o.isNull("id")) return null
                val name = o.optString("name").ifBlank { return null }
                return Team(
                    id = "fd:" + o.optString("id"),
                    name = name,
                    short = o.optString("shortName").ifBlank { name },
                    abbr = o.optString("tla"),
                    logo = o.optString("crest"),
                    nation = nation,
                )
            }
            val st = m.optString("status")
            val stage = m.optString("stage")
            Match(
                id = "fd:" + m.optString("id"),
                comp = slug,
                compName = Comps.name(slug, comp?.optString("name").orEmpty()),
                kickoff = Espn.parseTime(m.optString("utcDate")) ?: return@mapObjects null,
                home = t(m.optJSONObject("homeTeam")) ?: return@mapObjects null,
                away = t(m.optJSONObject("awayTeam")) ?: return@mapObjects null,
                stage = stage.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                stageRank = Comps.stageRank(stage),
                venue = m.optString("venue").takeIf { it != "null" }.orEmpty(),
                status = when (st) {
                    "IN_PLAY", "PAUSED", "LIVE" -> Status.LIVE
                    "FINISHED", "AWARDED" -> Status.FINISHED
                    "POSTPONED", "SUSPENDED" -> Status.POSTPONED
                    "CANCELLED" -> Status.CANCELLED
                    else -> Status.SCHEDULED
                },
            )
        }
    }

    /** Fetches in 10-day windows (the API's maximum range). */
    suspend fun matches(token: String, fromDay: LocalDate, toDay: LocalDate): List<Match> = withContext(Dispatchers.IO) {
        if (token.isBlank()) throw SourceException("No football-data.org token")
        val out = ArrayList<Match>()
        var start = fromDay
        while (!start.isAfter(toDay)) {
            val end = minOf(start.plusDays(9), toDay)
            val json = Http.get("$BASE/matches?dateFrom=$start&dateTo=$end", mapOf("X-Auth-Token" to token.trim()))
            out += parse(json)
            start = end.plusDays(1)
        }
        out.distinctBy { it.id }
    }
}

/**
 * ClubElo ratings (api.clubelo.com): one CSV of every European club's current Elo. Used for "top N clubs count
 * as big". Ranks are keyed by [Names.canonical] so ESPN's and football-data.org's names both find them.
 */
object Elo {
    fun parse(csv: String): Map<String, Int> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyMap()
        val header = lines.first().split(',').map { it.trim().lowercase() }
        val iClub = header.indexOf("club")
        val iElo = header.indexOf("elo")
        if (iClub < 0 || iElo < 0) return emptyMap()
        val rows = lines.drop(1).mapNotNull { line ->
            val p = line.split(',')
            val club = p.getOrNull(iClub)?.trim().orEmpty()
            val elo = p.getOrNull(iElo)?.trim()?.toDoubleOrNull()
            if (club.isEmpty() || elo == null) null else club to elo
        }.sortedByDescending { it.second }
        val out = LinkedHashMap<String, Int>()
        rows.forEachIndexed { i, (club, _) -> out.putIfAbsent(Names.canonical(club), i + 1) }
        return out
    }

    suspend fun fetch(): Map<String, Int> = withContext(Dispatchers.IO) {
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        val csv = runCatching { Http.get("https://api.clubelo.com/$day") }
            .getOrElse { Http.get("http://api.clubelo.com/$day") }
        parse(csv).also { if (it.size < 50) throw SourceException("ClubElo returned too little") }
    }

    /**
     * Built-in national-team order (World Football Elo, approx. late 2026) used until eloratings.net answers, and
     * kept if it never does. Only the top ~40 matter for the "top N" rule.
     */
    val NATIONS_FALLBACK: Map<String, Int> = listOf(
        "Spain", "Argentina", "France", "England", "Brazil", "Portugal", "Netherlands", "Colombia", "Germany",
        "Croatia", "Italy", "Belgium", "Uruguay", "Norway", "Japan", "Switzerland", "Ecuador", "Denmark", "Mexico",
        "Morocco", "Turkey", "Austria", "United States", "Senegal", "Iran", "Paraguay", "South Korea", "Serbia",
        "Ukraine", "Poland", "Australia", "Canada", "Sweden", "Hungary", "Scotland", "Czechia", "Greece",
        "Wales", "Nigeria", "Egypt", "Algeria", "Ivory Coast", "Peru", "Chile", "Venezuela", "Ireland",
    ).mapIndexed { i, n -> Names.canonical(n) to i + 1 }.toMap()

    /**
     * eloratings.net's data files: `en.teams.tsv` maps team codes to names, `World.tsv` lists teams best first
     * with their code and rating. The format isn't documented, so the code and rating columns are found by
     * shape, and anything odd throws (the caller keeps the previous ranks).
     */
    suspend fun fetchNations(): Map<String, Int> = withContext(Dispatchers.IO) {
        val names = HashMap<String, String>()
        Http.get("https://www.eloratings.net/en.teams.tsv").lineSequence().forEach { line ->
            val p = line.split('\t')
            if (p.size >= 2 && p[0].isNotBlank() && p[1].isNotBlank()) names[p[0].trim()] = p[1].trim()
        }
        val rows = Http.get("https://www.eloratings.net/World.tsv").lineSequence().mapNotNull { line ->
            val p = line.split('\t').map { it.trim() }
            val ci = p.indexOfFirst { it in names }
            if (ci < 0) return@mapNotNull null
            val rating = p.drop(ci + 1).firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { r -> r in 500..3000 } }
                ?: return@mapNotNull null
            names.getValue(p[ci]) to rating
        }.toList()
        if (rows.size < 100) throw SourceException("eloratings.net format changed")
        val out = LinkedHashMap<String, Int>()
        rows.sortedByDescending { it.second }.forEachIndexed { i, (name, _) -> out.putIfAbsent(Names.canonical(name), i + 1) }
        out
    }

    fun rankOf(t: Team, ranks: Map<String, Int>): Int? {
        if (ranks.isEmpty()) return null
        return ranks[t.key] ?: ranks[Names.canonical(t.short)]
    }
}
