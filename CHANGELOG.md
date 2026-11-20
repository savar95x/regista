# Changelog

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
