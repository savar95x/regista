package dev.savar.regista

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Not real tests: renders the main screens to PNG (build/shots) so they can be reviewed in CI without a phone.
 * Run by .github/workflows/screenshots.yml; fetch with scripts/shots.sh.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class Screenshots {
    @get:Rule val compose = createComposeRule()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val out = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun t(id: String, name: String, short: String = name, abbr: String = "", nation: Boolean = false) =
        Team(id, name, short, abbr, "", nation)

    private val real = t("86", "Real Madrid", abbr = "RMA")
    private val barca = t("83", "Barcelona", abbr = "BAR")
    private val liverpool = t("364", "Liverpool", abbr = "LIV")
    private val atletico = t("1068", "Atlético Madrid", "Atlético", "ATM")
    private val como = t("2572", "Como", abbr = "COM")
    private val inter = t("110", "Internazionale", "Inter", "INT")
    private val manUtd = t("360", "Manchester United", "Man United", "MUN")
    private val spurs = t("367", "Tottenham Hotspur", "Spurs", "TOT")
    private val france = t("478", "France", abbr = "FRA", nation = true)
    private val germany = t("481", "Germany", abbr = "GER", nation = true)
    private val arsenal = t("359", "Arsenal", abbr = "ARS")
    private val chelsea = t("363", "Chelsea", abbr = "CHE")

    private fun at(daysFromToday: Long, hour: Int, minute: Int = 0): Long =
        LocalDate.now().plusDays(daysFromToday).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun seed() {
        Store.seed(
            matches = listOf(
                Match("1", Comps.SERIE_A, "Serie A", at(0, 23, 0), como, inter, venue = "Stadio Giuseppe Sinigaglia"),
                Match("2", Comps.UCL, "Champions League", at(1, 1, 30), liverpool, atletico, stage = "League phase", venue = "Anfield"),
                Match("3", Comps.LALIGA, "LaLiga", at(2, 20, 45), real, barca, venue = "Santiago Bernabéu"),
                Match("4", Comps.EPL, "Premier League", at(2, 22, 0), arsenal, chelsea),
                Match("5", Comps.UECL, "Conference League", at(4, 23, 30), manUtd, spurs),
                Match("6", Comps.FRIENDLY, "International friendly", at(6, 1, 15), france, germany),
            ),
            follows = listOf(Follow(real, FollowMode.ALL), Follow(france, FollowMode.ALL), Follow(como, FollowMode.BIG)),
            catalog = listOf(real, barca, liverpool, atletico, como, inter, manUtd, spurs, france, germany, arsenal, chelsea),
            sync = SyncState(System.currentTimeMillis() - 12 * 60_000L, "ESPN", null, 214),
        )
        Store.attach(ctx)
    }

    private fun shot(name: String, mode: ThemeMode, black: Boolean = false, content: @Composable () -> Unit) {
        ThemePrefs.force(mode, black)
        compose.mainClock.autoAdvance = false
        compose.setContent { RegistaTheme { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val healthy = Health()

    @Test fun upNextLight() { seed(); shot("upnext_light", ThemeMode.LIGHT) { RegistaRoot(null, {}, preview = true, previewHealth = healthy) } }

    @Test fun upNextDark() { seed(); shot("upnext_dark", ThemeMode.DARK) { RegistaRoot(null, {}, preview = true, previewHealth = healthy) } }

    @Test fun upNextBlackWarning() {
        seed()
        shot("upnext_black_exact", ThemeMode.DARK, black = true) {
            RegistaRoot(null, {}, preview = true, previewHealth = Health(exact = false))
        }
    }

    @Test fun empty() {
        Store.seed(sync = SyncState(System.currentTimeMillis(), "ESPN", null, 0), settings = Settings(bigMatchesOn = false))
        Store.attach(ctx)
        shot("upnext_empty", ThemeMode.LIGHT) { RegistaRoot(null, {}, preview = true, previewHealth = healthy) }
    }

    @Test fun following() { seed(); shot("following_dark", ThemeMode.DARK) { RegistaRoot(null, {}, preview = true, startTab = Tab.FOLLOWING, previewHealth = healthy) } }

    @Test fun big() { seed(); shot("big_light", ThemeMode.LIGHT) { RegistaRoot(null, {}, preview = true, startTab = Tab.FOLLOWING, startBig = true, previewHealth = healthy) } }

    @Test fun settings() { seed(); shot("settings_light", ThemeMode.LIGHT) { RegistaRoot(null, {}, preview = true, startTab = Tab.SETTINGS, previewHealth = Health(battery = false)) } }
}
