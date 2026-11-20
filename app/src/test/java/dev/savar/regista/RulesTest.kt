package dev.savar.regista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Big-match rules, name matching and source parsing. Robolectric only for org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RulesTest {
    private fun t(id: String, name: String, nation: Boolean = false) = Team(id, name, nation = nation)
    private fun m(home: Team, away: Team, comp: String = Comps.EPL, rank: Int = 0, id: String = home.id + away.id) =
        Match(id, comp, Comps.name(comp), System.currentTimeMillis() + 86_400_000L * 2, home, away, stageRank = rank)

    private val liverpool = t("364", "Liverpool")
    private val atletico = t("1068", "Atlético Madrid")
    private val como = t("2572", "Como")
    private val genoa = t("3263", "Genoa")
    private val inter = t("110", "Internazionale")
    private val manUtd = t("360", "Manchester United")
    private val spurs = t("367", "Tottenham Hotspur")
    private val portugal = t("482", "Portugal", true)
    private val spain = t("164", "Spain", true)
    private val bodo = t("5", "Bodø/Glimt")
    private val brann = t("6", "Brann")

    private val cfg = RuleConfig(eloTopN = 0)

    @Test fun names() {
        assertEquals("man united", Names.canonical("Manchester United"))
        assertEquals("man united", Names.canonical("Man United"))
        assertEquals("atletico", Names.canonical("Atlético de Madrid"))
        assertEquals("inter", Names.canonical("Internazionale"))
        assertEquals("milan", Names.canonical("AC Milan"))
        assertEquals("paris sg", Names.canonical("Paris Saint-Germain"))
        assertEquals("bilbao", Names.canonical("Athletic Club"))
        assertEquals("union berlin", Names.canonical("1. FC Union Berlin"))
        assertEquals("inter", Names.canonical("FC Internazionale Milano"))
        assertEquals("benfica", Names.canonical("Sport Lisboa e Benfica"))
        assertEquals("real madrid", Names.canonical("Real Madrid CF"))
    }

    @Test fun bigVsBigIsBig_evenIfNotFollowed() {
        assertEquals(listOf(Reason.BigMatch), Rules.reasons(m(liverpool, atletico, Comps.UCL), cfg))
        assertEquals(listOf(Reason.BigMatch), Rules.reasons(m(manUtd, spurs, Comps.UECL), cfg))
        assertEquals(listOf(Reason.BigMatch), Rules.reasons(m(portugal, spain, Comps.FRIENDLY), cfg.copy(derbiesOn = false)))
    }

    @Test fun derbyNamed() {
        assertEquals(listOf(Reason.Derby("Iberian derby")), Rules.reasons(m(portugal, spain, Comps.FRIENDLY), cfg))
    }

    @Test fun smallGamesIgnored() {
        assertTrue(Rules.reasons(m(como, genoa, Comps.SERIE_A), cfg).isEmpty())
        assertTrue(Rules.reasons(m(bodo, brann, Comps.UECL), cfg).isEmpty())
    }

    @Test fun bigGamesOnlyFollow() {
        val c = cfg.copy(follows = listOf(Follow(como, FollowMode.BIG)), bigMatchesOn = false)
        assertTrue(Rules.reasons(m(como, genoa, Comps.SERIE_A), c).isEmpty())
        assertEquals(listOf(Reason.BigGame(como)), Rules.reasons(m(como, inter, Comps.SERIE_A), c))
    }

    @Test fun everyMatchFollow() {
        val c = cfg.copy(follows = listOf(Follow(como, FollowMode.ALL)))
        assertEquals(listOf(Reason.Following(como)), Rules.reasons(m(como, genoa, Comps.SERIE_A), c))
    }

    @Test fun followMatchesByNameAcrossSources() {
        val c = cfg.copy(follows = listOf(Follow(como, FollowMode.ALL)))
        assertEquals(1, Rules.reasons(m(t("fd:7397", "Como 1907"), genoa, Comps.SERIE_A), c).size)
    }

    @Test fun knockouts() {
        val ucQf = m(bodo, brann, Comps.UCL, rank = 2)
        assertEquals("Champions League quarter-final", (Rules.reasons(ucQf, cfg).single() as Reason.Knockout).text)
        assertTrue(Rules.reasons(m(bodo, brann, Comps.UECL, rank = 3), cfg).isEmpty())
        assertNotNull(Rules.reasons(m(bodo, brann, Comps.UECL, rank = 4), cfg).singleOrNull())
        assertTrue(Rules.reasons(m(bodo, brann, Comps.UCL, rank = 2), cfg.copy(knockoutsOn = false)).isEmpty())
    }

    @Test fun eloTopN() {
        val ranks = mapOf("como" to 15, "genoa" to 60)
        val c = cfg.copy(eloTopN = 20, eloRanks = ranks)
        assertTrue(Rules.isBig(como, c))
        assertTrue(!Rules.isBig(genoa, c))
        assertEquals(listOf(Reason.BigMatch), Rules.reasons(m(como, inter), c))
    }

    @Test fun nationElo() {
        val norway = t("464", "Norway", true)
        val c = cfg.copy(nationTopN = 15, nationRanks = Elo.NATIONS_FALLBACK, bigTeams = emptyList())
        assertTrue(Rules.isBig(norway, c))
        assertTrue(!Rules.isBig(norway, c.copy(nationTopN = 10)))
        assertTrue(!Rules.isBig(t("1", "San Marino", true), c))
        assertEquals("ireland", Names.canonical("Republic of Ireland"))
        assertEquals("ivory coast", Names.canonical("Côte d'Ivoire"))
    }

    @Test fun stageRank() {
        assertEquals(4, Comps.stageRank("Final"))
        assertEquals(3, Comps.stageRank("Semifinals - 1st Leg"))
        assertEquals(2, Comps.stageRank("QUARTER_FINALS"))
        assertEquals(1, Comps.stageRank("Round of 16"))
        assertEquals(0, Comps.stageRank("League Phase"))
        assertEquals(0, Comps.stageRank("Knockout Round Playoffs"))
    }

    @Test fun parseEspn() {
        val json = """
            {"leagues":[{"name":"UEFA Champions League"}],
             "events":[{"id":"401","date":"2026-11-25T20:00Z","season":{"slug":"league-phase"},
               "competitions":[{"venue":{"fullName":"Anfield"},
                 "status":{"type":{"name":"STATUS_SCHEDULED","state":"pre","completed":false}},
                 "notes":[],
                 "competitors":[
                   {"homeAway":"home","team":{"id":"364","displayName":"Liverpool","shortDisplayName":"Liverpool","abbreviation":"LIV","logo":"https://x/364.png"}},
                   {"homeAway":"away","team":{"id":"1068","displayName":"Atlético Madrid","shortDisplayName":"Atlético","abbreviation":"ATM","logo":"https://x/1068.png"}}]}]}]}
        """.trimIndent()
        val list = Espn.parseScoreboard(Comps.UCL, json)
        assertEquals(1, list.size)
        val x = list.single()
        assertEquals("Liverpool", x.home.name)
        assertEquals("Atlético", x.away.short)
        assertEquals("Champions League", x.compName)
        assertEquals(Status.SCHEDULED, x.status)
        assertEquals(0, x.stageRank)
        assertEquals(Espn.parseTime("2026-11-25T20:00:00Z"), x.kickoff)
    }

    @Test fun parseFootballData() {
        val json = """
            {"matches":[{"id":1,"utcDate":"2026-11-29T16:30:00Z","status":"TIMED","stage":"REGULAR_SEASON",
              "competition":{"code":"PL","name":"Premier League"},
              "homeTeam":{"id":66,"name":"Manchester United FC","shortName":"Man United","tla":"MUN","crest":"c"},
              "awayTeam":{"id":73,"name":"Tottenham Hotspur FC","shortName":"Tottenham","tla":"TOT","crest":"c"}}]}
        """.trimIndent()
        val x = FootballData.parse(json).single()
        assertEquals(Comps.EPL, x.comp)
        assertEquals("man united", x.home.key)
        assertEquals(listOf(Reason.BigMatch), Rules.reasons(x, cfg))
    }

    @Test fun elo() {
        val csv = "Rank,Club,Country,Level,Elo,From,To\n1,Liverpool,ENG,1,2010.5,x,y\n2,Man City,ENG,1,1990,x,y\nNone,Atletico,ESP,1,1900,x,y\n"
        val r = Elo.parse(csv)
        assertEquals(1, Elo.rankOf(liverpool, r))
        assertEquals(3, Elo.rankOf(atletico, r))
        assertNull(Elo.rankOf(como, r))
    }

    @Test fun versions() {
        assertTrue(Updater.newer("0.1.10", "0.1.9"))
        assertTrue(Updater.newer("v1.0.0", "0.9.99"))
        assertTrue(!Updater.newer("0.1.1", "0.1.1"))
        assertTrue(!Updater.newer("0.1.0", "0.1.1"))
    }

    @Test fun search() {
        val pool = listOf(liverpool, atletico, inter, manUtd, spurs, portugal, spain)
        assertEquals("Manchester United", searchTeams(pool, "man u").first().name)
        assertEquals("Atlético Madrid", searchTeams(pool, "atletico").first().name)
        assertEquals("Tottenham Hotspur", searchTeams(pool, "spurs").first().name)
    }
}
