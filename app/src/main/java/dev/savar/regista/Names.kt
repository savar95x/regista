package dev.savar.regista

import java.text.Normalizer

/**
 * Team-name matching across sources (ESPN, football-data.org, ClubElo) and the built-in lists.
 *
 * [canonical] lowercases, strips accents and punctuation, drops filler tokens (FC, AC, CF…) and then maps the
 * common long forms to the short names ClubElo uses, so "Manchester United", "Man United" and "Manchester
 * United FC" all become "man united", and "Atlético de Madrid" becomes "atletico".
 */
object Names {
    private val FILLER = setOf(
        "fc", "cf", "afc", "ac", "as", "sc", "ssc", "ss", "cp", "sv", "vfb", "vfl", "tsg", "bsc", "rcd", "ud", "cd", "sd",
        "club", "de", "del", "la", "le", "calcio", "1", "04", "05", "1899", "1846", "1907", "1909", "1910", "1913",
        "football", "futbol", "fk", "sk", "nk", "osc", "ogc", "losc", "us", "ca", "rc", "sl",
    )

    // After normalisation; target is ClubElo's spelling, normalised.
    private val ALIASES = mapOf(
        "manchester united" to "man united",
        "manchester utd" to "man united",
        "man utd" to "man united",
        "manchester city" to "man city",
        "atletico madrid" to "atletico",
        "atletico" to "atletico",
        "bayern munich" to "bayern",
        "bayern munchen" to "bayern",
        "fc bayern" to "bayern",
        "paris saint germain" to "paris sg",
        "paris st germain" to "paris sg",
        "psg" to "paris sg",
        "internazionale" to "inter",
        "internazionale milano" to "inter",
        "sport lisboa e benfica" to "benfica",
        "futebol clube porto" to "porto",
        "inter milan" to "inter",
        "milan" to "milan",
        "borussia dortmund" to "dortmund",
        "bayer leverkusen" to "leverkusen",
        "tottenham hotspur" to "tottenham",
        "spurs" to "tottenham",
        "newcastle united" to "newcastle",
        "west ham united" to "west ham",
        "leeds united" to "leeds",
        "leicester city" to "leicester",
        "ipswich town" to "ipswich",
        "nottingham forest" to "forest",
        "nottm forest" to "forest",
        "wolverhampton wanderers" to "wolves",
        "wolverhampton" to "wolves",
        "brighton hove albion" to "brighton",
        "brighton and hove albion" to "brighton",
        "psv eindhoven" to "psv",
        "real sociedad" to "sociedad",
        "athletic" to "bilbao",
        "athletic bilbao" to "bilbao",
        "eintracht frankfurt" to "frankfurt",
        "real betis" to "betis",
        "celta vigo" to "celta",
        "borussia monchengladbach" to "gladbach",
        "monchengladbach" to "gladbach",
        "ajax amsterdam" to "ajax",
        "feyenoord rotterdam" to "feyenoord",
        "olympique lyonnais" to "lyon",
        "olympique lyon" to "lyon",
        "olympique marseille" to "marseille",
        "stade rennais" to "rennes",
        "stade brestois 29" to "brest",
        "stade brestois" to "brest",
        "hellas verona" to "verona",
        "deportivo alaves" to "alaves",
        "sporting lisbon" to "sporting",
        "sporting clube portugal" to "sporting",
        "sl benfica" to "benfica",
        "fc porto" to "porto",
        "rb salzburg" to "salzburg",
        "red bull salzburg" to "salzburg",
        "club brugge kv" to "brugge",
        "glasgow rangers" to "rangers",
        "celtic glasgow" to "celtic",
        "united states" to "usa",
        "korea republic" to "south korea",
        "turkiye" to "turkey",
    )

    private val MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val normCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val canonCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun norm(s: String): String = normCache.getOrPut(s) { computeNorm(s) }

    private fun computeNorm(s: String): String {
        val noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARKS, "")
        return noAccents.lowercase()
            .replace('&', ' ')
            .replace(NON_ALNUM, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() && it !in FILLER }
            .joinToString(" ")
            .ifEmpty { noAccents.lowercase().trim() }
    }

    fun canonical(s: String): String = canonCache.getOrPut(s) {
        val n = norm(s)
        ALIASES[n] ?: n
    }

    fun same(a: String, b: String) = canonical(a) == canonical(b)
}
