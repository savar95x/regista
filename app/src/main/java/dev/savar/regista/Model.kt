package dev.savar.regista

import org.json.JSONArray
import org.json.JSONObject

/** A club or national team. [id] is the data source's id ("fd:123" for football-data.org, plain for ESPN). */
data class Team(
    val id: String,
    val name: String,
    val short: String = name,
    val abbr: String = "",
    val logo: String = "",
    val nation: Boolean = false,
) {
    /** Name-based identity that survives switching data sources (see [Names.canonical]). */
    val key: String get() = Names.canonical(name)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("short", short).put("abbr", abbr).put("logo", logo).put("nation", nation)

    companion object {
        fun fromJson(o: JSONObject) = Team(
            id = o.optString("id"),
            name = o.optString("name"),
            short = o.optString("short").ifBlank { o.optString("name") },
            abbr = o.optString("abbr"),
            logo = o.optString("logo"),
            nation = o.optBoolean("nation"),
        )
    }
}

enum class Status { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED }

/**
 * One fixture. [stageRank] is how deep into a knockout it is: 1 last 16, 2 quarter-final, 3 semi-final, 4 final
 * (0 for league games and early rounds). [timeTbd] marks fixtures whose kick-off time isn't set yet.
 */
data class Match(
    val id: String,
    val comp: String,
    val compName: String,
    val kickoff: Long,
    val home: Team,
    val away: Team,
    val stage: String = "",
    val stageRank: Int = 0,
    val venue: String = "",
    val status: Status = Status.SCHEDULED,
    val timeTbd: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("comp", comp).put("compName", compName).put("kickoff", kickoff)
        .put("home", home.toJson()).put("away", away.toJson())
        .put("stage", stage).put("stageRank", stageRank).put("venue", venue)
        .put("status", status.name).put("timeTbd", timeTbd)

    companion object {
        fun fromJson(o: JSONObject) = Match(
            id = o.optString("id"),
            comp = o.optString("comp"),
            compName = o.optString("compName"),
            kickoff = o.optLong("kickoff"),
            home = Team.fromJson(o.getJSONObject("home")),
            away = Team.fromJson(o.getJSONObject("away")),
            stage = o.optString("stage"),
            stageRank = o.optInt("stageRank"),
            venue = o.optString("venue"),
            status = Status.entries.firstOrNull { it.name == o.optString("status") } ?: Status.SCHEDULED,
            timeTbd = o.optBoolean("timeTbd"),
        )
    }
}

enum class FollowMode { ALL, BIG }

data class Follow(val team: Team, val mode: FollowMode) {
    fun toJson(): JSONObject = team.toJson().put("mode", mode.name)

    companion object {
        fun fromJson(o: JSONObject) = Follow(
            Team.fromJson(o),
            FollowMode.entries.firstOrNull { it.name == o.optString("mode") } ?: FollowMode.ALL,
        )
    }
}

internal fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        runCatching { f(o) }.getOrNull()?.let(out::add)
    }
    return out
}

internal fun <T> List<T>.toJsonArray(f: (T) -> JSONObject): JSONArray = JSONArray().also { a -> forEach { a.put(f(it)) } }
