# Changelog

## [0.4.1] - 2026-11-20

- Page titles are Material 3's large collapsing title bar: big at the top, shrinking into the bar as you
  scroll (same as Hiatus, Minutia and Aria). "What counts as big" gets a back arrow in the bar.
- Removing a team from Big teams asks first and says what changes.

## [0.4.0] - 2026-11-20

- **New icon**: a referee's whistle, drawn in the same colours and line weight as Aria and Minutia. The
  notification icon matches.
- **Updates pop up**: when a new version is out, opening the app shows it with what's new and an Update button
  (once per version; the card on Up next and the row in Settings stay). The check no longer depends on GitHub's
  rate-limited API, which could silently fail on mobile data; if both ways fail, Settings says why.
- **Look ahead** (Settings → Data): fixtures and reminders for the next 1, 2, 4 or 8 weeks. Default 2 weeks.
- Resetting the big teams list now asks first, says what will change, and only appears once you've edited it.

## [0.3.0] - 2026-11-20

- **National teams by Elo.** "What counts as big" now has two sliders under *Strongest right now*: clubs
  (Europe's top N, Club Elo, daily) and national teams (world top N, World Football Elo, weekly; a built-in
  ranking is used until the first download, and kept if the site ever stops answering). "See who's in" lists
  the teams that currently qualify.
- **Follow a team** is now the first row of Following instead of a floating button.
- **Crests**: each team tries several sources in turn (its own crest, ESPN's by id, any crest seen for that
  name, ESPN's standard path) before falling back to initials. Failed downloads retry after 30 minutes and
  broken cached files are re-downloaded, so a flaky connection no longer leaves a team without a badge.

## [0.2.1] - 2026-11-20

- Crests no longer stick to the wrong team after removing one from Big teams (Real Madrid showing PSG's badge).
- Crests and national flags now load whichever fixtures source is selected: the team list always comes from
  ESPN (football-data.org's crests are SVGs Android can't draw, and it has no national teams).
- football-data.org's full club names (FC Internazionale Milano, Sport Lisboa e Benfica…) match their teams.

## [0.2.0] - 2026-11-20

- **In-app updates.** Opening the app checks GitHub for a new release (a tiny request when nothing changed).
  An update card on Up next shows the version, size and what's new; Update downloads and installs it.
  Each version is downloaded once: an interrupted download resumes, a downloaded one is reused if you
  cancel the install, and old APKs are deleted after updating.
- The first in-app update asks you to allow "Install unknown apps" and to confirm; from then on Android 12+
  can update Regista without the extra prompt.
- Settings → About shows the update status; tap to check now.

## [0.1.1] - 2026-11-20

- **Snappier**: switches, tabs and follows respond instantly. Reminder alarms are now rebuilt in the background
  and only the ones that changed are touched; team-name matching and crest lookups are cached.
- **Font** is one row in Settings now: System default, a short list shown in each font's own face, and
  "More fonts" for all of Google Fonts. Picking one downloads it with a spinner on that row.
- Elo choices read Off / 10 / 20 / 30 / 50 so they fit on any font size.

## [0.1.0] - 2026-11-20

First release.

- **Up next**: the matches you'll be reminded about, by day, each with the reason it's there. Tap one for the
  details, when each reminder fires, and a switch to mute that match.
- **Reminders** a day, an hour and 5 minutes before kick-off (each can be turned off and has its own
  notification channel). Exact alarms when allowed; kick-off time changes move the reminders with them.
  "Mute this match" right from the notification.
- **Following**: clubs and national teams, each on "Every match" or "Big games only". Find teams by name or
  by player.
- **Big matches**: both teams big (built-in editable list plus Europe's top clubs by Elo), derbies and
  rivalries, late knockout rounds.
- Fixtures from ESPN for the big five leagues, Champions/Europa/Conference League, domestic cups and
  internationals (friendlies included), with football-data.org as an optional fallback.
- Material 3 Expressive, dynamic colour, pure black option, system font or any Google Font.
