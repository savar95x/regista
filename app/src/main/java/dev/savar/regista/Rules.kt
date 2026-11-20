package dev.savar.regista

/** Why a match is in Up next. The first reason is the one shown. */
sealed interface Reason {
    val label: String

    data class Following(val team: Team) : Reason {
        override val label get() = "Following ${team.short}"
    }

    data class BigGame(val team: Team) : Reason {
        override val label get() = "${team.short} · big game"
    }

    data class Derby(val name: String) : Reason {
        override val label get() = name
    }

    data class Knockout(val text: String) : Reason {
        override val label get() = text
    }

    data object BigMatch : Reason {
        override val label get() = "Big match"
    }
}

/** Everything the rules need, gathered from [Store] (kept plain so it can be unit-tested). */
data class RuleConfig(
    val follows: List<Follow> = emptyList(),
    val bigTeams: List<Team> = Rules.defaultBigTeams,
    val bigMatchesOn: Boolean = true,
    val derbiesOn: Boolean = true,
    val knockoutsOn: Boolean = true,
    val eloTopN: Int = 20,
    val eloRanks: Map<String, Int> = emptyMap(),
    val muted: Set<String> = emptySet(),
)

object Rules {

    /** The built-in "always big" list. Editable in the app; matched by name so it works with any source. */
    val defaultBigTeams: List<Team> = listOf(
        "Real Madrid", "Barcelona", "Atlético Madrid", "Manchester United", "Manchester City", "Liverpool",
        "Arsenal", "Chelsea", "Tottenham Hotspur", "Bayern Munich", "Borussia Dortmund", "Paris Saint-Germain",
        "Juventus", "Internazionale", "AC Milan", "Napoli", "Benfica", "FC Porto", "Ajax",
    ).map { Team(id = "", name = it, short = shortOf(it)) } + listOf(
        "Spain", "France", "England", "Argentina", "Brazil", "Portugal", "Germany", "Netherlands", "Italy",
        "Belgium", "Croatia", "Uruguay",
    ).map { Team(id = "", name = it, nation = true) }

    private fun shortOf(name: String) = when (name) {
        "Atlético Madrid" -> "Atlético"
        "Manchester United" -> "Man United"
        "Manchester City" -> "Man City"
        "Tottenham Hotspur" -> "Spurs"
        "Bayern Munich" -> "Bayern"
        "Borussia Dortmund" -> "Dortmund"
        "Paris Saint-Germain" -> "PSG"
        "Internazionale" -> "Inter"
        "AC Milan" -> "Milan"
        "FC Porto" -> "Porto"
        else -> name
    }

    /** Rivalries that are always worth a reminder, by canonical names. */
    private val DERBIES: List<Triple<String, String, String>> = listOf(
        Triple("Real Madrid", "Barcelona", "El Clásico"),
        Triple("Real Madrid", "Atlético Madrid", "Madrid derby"),
        Triple("Manchester United", "Manchester City", "Manchester derby"),
        Triple("Manchester United", "Liverpool", "North West derby"),
        Triple("Liverpool", "Everton", "Merseyside derby"),
        Triple("Arsenal", "Tottenham Hotspur", "North London derby"),
        Triple("Internazionale", "AC Milan", "Derby della Madonnina"),
        Triple("Juventus", "Internazionale", "Derby d'Italia"),
        Triple("Roma", "Lazio", "Derby della Capitale"),
        Triple("Borussia Dortmund", "Bayern Munich", "Der Klassiker"),
        Triple("Paris Saint-Germain", "Marseille", "Le Classique"),
        Triple("Benfica", "FC Porto", "O Clássico"),
        Triple("Benfica", "Sporting CP", "Lisbon derby"),
        Triple("Ajax", "Feyenoord", "De Klassieker"),
        Triple("Sevilla", "Real Betis", "Seville derby"),
        Triple("Celtic", "Rangers", "Old Firm"),
        Triple("Argentina", "Brazil", "Superclásico de las Américas"),
        Triple("Spain", "Portugal", "Iberian derby"),
        Triple("England", "Germany", "England v Germany"),
        Triple("Germany", "Netherlands", "Germany v Netherlands"),
    ).map { (a, b, n) -> Triple(Names.canonical(a), Names.canonical(b), n) }

    fun derbyName(m: Match): String? {
        val h = m.home.key
        val a = m.away.key
        return DERBIES.firstOrNull { (x, y, _) -> (h == x && a == y) || (h == y && a == x) }?.third
    }

    /** How deep a knockout must be before it's big on its own, per competition. Absent = never on its own. */
    private val KNOCKOUT_MIN = mapOf(
        Comps.UCL to 2, Comps.UEL to 3, Comps.UECL to 4, Comps.SUPER_CUP to 0,
        Comps.WORLD_CUP to 1, Comps.EURO to 2, Comps.COPA_AMERICA to 3, Comps.NATIONS to 3,
        Comps.CLUB_WC to 2,
        Comps.FA_CUP to 4, Comps.LEAGUE_CUP to 4, Comps.COPA_DEL_REY to 4, Comps.COPPA_ITALIA to 4,
        Comps.DFB_POKAL to 4, Comps.COUPE_DE_FRANCE to 4,
    )

    fun knockoutLabel(m: Match): String? {
        val min = KNOCKOUT_MIN[m.comp] ?: return null
        if (m.comp == Comps.SUPER_CUP) return m.compName
        if (m.stageRank < maxOf(min, 1)) return null
        val stage = when (m.stageRank) {
            4 -> "final"
            3 -> "semi-final"
            2 -> "quarter-final"
            else -> "last 16"
        }
        return "${Comps.shortName(m.comp, m.compName)} $stage"
    }

    fun isBig(t: Team, cfg: RuleConfig): Boolean {
        val key = t.key
        if (cfg.bigTeams.any { (it.id.isNotEmpty() && it.id == t.id) || it.key == key }) return true
        if (!t.nation && cfg.eloTopN > 0) {
            val rank = Elo.rankOf(t, cfg.eloRanks)
            if (rank != null && rank <= cfg.eloTopN) return true
        }
        return false
    }

    private fun follows(f: Follow, t: Team) = (f.team.id.isNotEmpty() && f.team.id == t.id) || f.team.key == t.key

    /** Reasons this match is in Up next, most specific first. Empty = not relevant. Muting is handled by callers. */
    fun reasons(m: Match, cfg: RuleConfig): List<Reason> {
        if (m.status == Status.CANCELLED || m.status == Status.FINISHED) return emptyList()
        val out = ArrayList<Reason>(3)
        val derby = if (cfg.derbiesOn) derbyName(m) else null
        val knockout = if (cfg.knockoutsOn) knockoutLabel(m) else null
        val bothBig = isBig(m.home, cfg) && isBig(m.away, cfg)

        for (side in listOf(m.home, m.away)) {
            val f = cfg.follows.firstOrNull { follows(it, side) } ?: continue
            val opponent = if (side === m.home) m.away else m.home
            when (f.mode) {
                FollowMode.ALL -> out += Reason.Following(f.team)
                FollowMode.BIG -> if (isBig(opponent, cfg) || derby != null || knockout != null) out += Reason.BigGame(f.team)
            }
        }
        if (cfg.bigMatchesOn || out.isNotEmpty()) {
            if (derby != null) out += Reason.Derby(derby)
            if (knockout != null) out += Reason.Knockout(knockout)
            if (cfg.bigMatchesOn && bothBig && derby == null && knockout == null) out += Reason.BigMatch
        }
        // A derby or knockout alone only counts when big matches are on; with them off it's just a label.
        if (!cfg.bigMatchesOn && out.none { it is Reason.Following || it is Reason.BigGame }) return emptyList()
        return out
    }

    fun relevant(matches: List<Match>, cfg: RuleConfig): List<Pair<Match, List<Reason>>> =
        matches.mapNotNull { m -> reasons(m, cfg).takeIf { it.isNotEmpty() }?.let { m to it } }
            .sortedBy { it.first.kickoff }
}
