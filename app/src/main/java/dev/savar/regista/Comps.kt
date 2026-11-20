package dev.savar.regista

/**
 * Competitions Regista watches. Keys are ESPN league slugs; football-data.org codes map onto the same keys so
 * the rules don't care which source a fixture came from.
 */
object Comps {
    const val EPL = "eng.1"
    const val LALIGA = "esp.1"
    const val SERIE_A = "ita.1"
    const val BUNDESLIGA = "ger.1"
    const val LIGUE_1 = "fra.1"
    const val PRIMEIRA = "por.1"
    const val EREDIVISIE = "ned.1"
    const val SCOTLAND = "sco.1"
    const val UCL = "uefa.champions"
    const val UEL = "uefa.europa"
    const val UECL = "uefa.europa.conf"
    const val SUPER_CUP = "uefa.super_cup"
    const val FA_CUP = "eng.fa"
    const val LEAGUE_CUP = "eng.league_cup"
    const val COPA_DEL_REY = "esp.copa_del_rey"
    const val COPPA_ITALIA = "ita.coppa_italia"
    const val DFB_POKAL = "ger.dfb_pokal"
    const val COUPE_DE_FRANCE = "fra.coupe_de_france"
    const val SUPERCOPA = "esp.super_cup"
    const val CLUB_WC = "fifa.cwc"
    const val CLUB_FRIENDLY = "club.friendly"
    const val FRIENDLY = "fifa.friendly"
    const val NATIONS = "uefa.nations"
    const val WORLD_CUP = "fifa.world"
    const val WC_QUAL_UEFA = "fifa.worldq.uefa"
    const val WC_QUAL_CONMEBOL = "fifa.worldq.conmebol"
    const val EURO = "uefa.euro"
    const val EURO_QUAL = "uefa.euroq"
    const val COPA_AMERICA = "conmebol.america"

    data class Comp(val slug: String, val name: String, val short: String, val international: Boolean = false, val core: Boolean = false)

    val all: List<Comp> = listOf(
        Comp(EPL, "Premier League", "Premier League", core = true),
        Comp(LALIGA, "LaLiga", "LaLiga", core = true),
        Comp(SERIE_A, "Serie A", "Serie A", core = true),
        Comp(BUNDESLIGA, "Bundesliga", "Bundesliga", core = true),
        Comp(LIGUE_1, "Ligue 1", "Ligue 1", core = true),
        Comp(UCL, "Champions League", "Champions League", core = true),
        Comp(UEL, "Europa League", "Europa League"),
        Comp(UECL, "Conference League", "Conference League"),
        Comp(SUPER_CUP, "UEFA Super Cup", "Super Cup"),
        Comp(PRIMEIRA, "Primeira Liga", "Primeira Liga"),
        Comp(EREDIVISIE, "Eredivisie", "Eredivisie"),
        Comp(SCOTLAND, "Scottish Premiership", "Premiership"),
        Comp(FA_CUP, "FA Cup", "FA Cup"),
        Comp(LEAGUE_CUP, "Carabao Cup", "Carabao Cup"),
        Comp(COPA_DEL_REY, "Copa del Rey", "Copa del Rey"),
        Comp(COPPA_ITALIA, "Coppa Italia", "Coppa Italia"),
        Comp(DFB_POKAL, "DFB-Pokal", "DFB-Pokal"),
        Comp(COUPE_DE_FRANCE, "Coupe de France", "Coupe de France"),
        Comp(SUPERCOPA, "Supercopa de España", "Supercopa"),
        Comp(CLUB_WC, "Club World Cup", "Club World Cup"),
        Comp(CLUB_FRIENDLY, "Club friendly", "Friendly"),
        Comp(FRIENDLY, "International friendly", "Friendly", international = true),
        Comp(NATIONS, "Nations League", "Nations League", international = true),
        Comp(WORLD_CUP, "World Cup", "World Cup", international = true),
        Comp(WC_QUAL_UEFA, "World Cup qualifier", "WC qualifier", international = true),
        Comp(WC_QUAL_CONMEBOL, "World Cup qualifier", "WC qualifier", international = true),
        Comp(EURO, "Euro", "Euro", international = true),
        Comp(EURO_QUAL, "Euro qualifier", "Euro qualifier", international = true),
        Comp(COPA_AMERICA, "Copa América", "Copa América", international = true),
    )

    private val bySlug = all.associateBy { it.slug }

    fun name(slug: String, fallback: String = slug) = bySlug[slug]?.name ?: fallback
    fun shortName(slug: String, fallback: String = slug) = bySlug[slug]?.short ?: fallback
    fun isInternational(slug: String) = bySlug[slug]?.international == true

    /** Leagues whose team lists seed the search (clubs). */
    val clubLeagues = listOf(EPL, LALIGA, SERIE_A, BUNDESLIGA, LIGUE_1, PRIMEIRA, EREDIVISIE, SCOTLAND, UCL, UEL, UECL)

    /** Competitions whose team lists seed the search (national teams). */
    val nationLists = listOf(NATIONS, WORLD_CUP, FRIENDLY, COPA_AMERICA)

    /** football-data.org free-tier codes → our keys. */
    val footballData = mapOf(
        "PL" to EPL, "PD" to LALIGA, "SA" to SERIE_A, "BL1" to BUNDESLIGA, "FL1" to LIGUE_1, "CL" to UCL,
        "PPL" to PRIMEIRA, "DED" to EREDIVISIE, "WC" to WORLD_CUP, "EC" to EURO, "ELC" to "eng.2", "BSA" to "bra.1",
    )

    /** Knockout depth from a stage description in any source ("Quarterfinals - 1st Leg", "SEMI_FINALS"…). */
    fun stageRank(text: String): Int {
        val t = text.lowercase().replace('_', ' ').replace('-', ' ')
        return when {
            t.contains("semi") -> 3
            t.contains("quarter") -> 2
            t.contains("round of 16") || t.contains("last 16") || t.contains("eighth") || t.contains("1/8") -> 1
            Regex("\\bfinals?\\b").containsMatchIn(t) && !t.contains("qualif") && !t.contains("play off") &&
                !t.contains("playoff") && !t.contains("group") && !t.contains("league") -> 4
            else -> 0
        }
    }
}
