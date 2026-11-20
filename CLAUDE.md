# Regista: handoff for AI agents

Android football **match reminders** app ("narrow push": it pushes exactly what you subscribed to, no feed, no live
scores). Kotlin + Compose, package `dev.savar.regista`, minSdk 29 · targetSdk 36 · compileSdk 37. Owner: Savar
Verma (GitHub `savar95x`), daily driver **OnePlus 13s, OxygenOS 16**, time zone IST. Personal use only.
Sibling projects with the same conventions: `savar95x/hiatus`, `savar95x/minutia`, `savar95x/aria-player`.

## Golden rules
1. **Never change the signing key (`app/release.jks`) or `applicationId`.** Installed copies couldn't update.
2. **Every shipped change = version bump (`version.properties`) + `CHANGELOG.md` entry in the same push.**
   CI publishes `Regista X.Y.Z` with `Regista-X.Y.Z.apk` when the tag doesn't exist yet; otherwise it only build-checks.
3. **Commit as** `Savar Verma <102670483+savar95x@users.noreply.github.com>`.
4. **Reminders only.** Owner said: only "when the game will happen" reminders, not live updates. Don't add scores/feeds.
5. **Material 3 (Expressive), minimal copy.** Stock components; small focused changes.
6. **Don't claim it works on a phone.** You can't run it. Ask for screenshots.

## Working loop (no Android SDK locally, gh may be unauthenticated)
GitHub Actions is the compiler. Work on a branch (e.g. `dev`): `screenshots.yml` runs all unit tests + renders
screens and force-pushes PNGs, `gradle.log` and test XML to the orphan branch `shots`; `scripts/shots.sh` waits
for it via git and prints compile errors / failing tests. Merge to `main` to release; if the release build fails,
`build.yml` pushes its log to branch `ci-log` (`git fetch origin ci-log && git show FETCH_HEAD:build.log`).
Toolchain copied from Hiatus: AGP 9.5.1 (built-in Kotlin), Compose compiler plugin 2.4.21, BOM 2026.09.00,
material3 pinned to 1.5.0-alpha27 for the Expressive APIs.

## Architecture (`app/src/main/java/dev/savar/regista`)
| File | What it does |
|---|---|
| `Model.kt` | `Team`, `Match` (stageRank 1 = last 16 … 4 = final), `Follow` (`ALL` / `BIG`), JSON. |
| `Names.kt` | Name canonicalisation shared by every source + ClubElo ("Manchester United FC" → "man united"). Follows and big teams match by id **or** canonical name, so the football-data.org fallback (different ids) still works. |
| `Comps.kt` | Watched competitions (ESPN slugs), display names, football-data.org code map, `stageRank()` parser. |
| `Rules.kt` | The product logic: `reasons(match)` → Following / BigGame / Derby / Knockout / BigMatch. Default big teams, derby list, per-competition knockout depth. Unit-tested in `RulesTest`. |
| `Sources.kt` | `Espn` (scoreboard per competition with a date range, team lists, best-effort player search), `FootballData` (v4, token, 10-day windows), `Elo` (api.clubelo.com CSV → rank by canonical name). |
| `Store.kt` | All state as Compose state; prefs + JSON files (`matches.json`, `teams.json`, `elo.json`). Every mutation reschedules reminders. |
| `Sync.kt` | Fetch next 14 days (ESPN, fall back to football-data.org), weekly team list, daily Elo, then reschedule. `SyncJob` = JobScheduler every 6 h; also on app open if > 1 h old and on pull-to-refresh. |
| `Reminders.kt` | One alarm per (match, lead) (1 d / 1 h / 5 min), exact if allowed. `ReminderReceiver` re-checks the match before notifying; "Mute this match" action; `BootReceiver` rebuilds after reboot/update/time-zone change. One channel per lead. |
| `UpNextScreen.kt` | Home: next-reminder line, health cards (notifications / exact alarms / battery), day groups, match sheet with mute. |
| `FollowingScreen.kt` | Follows (mode menu), Big matches switch, "What counts as big" page, team/player search sheet. |
| `SettingsScreen.kt` | Reminder leads, reliability status, theme/pure black/font (+ Google Fonts browser), data source + token, update now. |
| `Theme.kt`, `Fonts.kt`, `Components.kt`, `Crest.kt`, `Fmt.kt` | Theme (dynamic colour, pure black), app-wide Google Font at 4 weights, shared rows (from Hiatus), crest loader, IST-aware formatting. |

ESPN is unofficial: parse defensively, keep football-data.org working. Player search (`site.web.api.espn.com`
search + athlete endpoints) is the least certain piece; it fails soft.
