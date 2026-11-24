# Shared kit (`dev.savar.kit`)

Aria, Minutia, Hiatus and Regista share a small set of files in `app/src/main/java/dev/savar/kit/`.
They are **byte-identical in all four repos** (same package everywhere, no `R`, no app classes), so the apps
look and behave the same wherever these pieces appear, and a fix is written once.

| File | What |
|---|---|
| `Kit.kt` | `Kit.init(KitApp(...))` (app name, repo, APK name, notes source); `pureBlack()`; `kitTopBarColors()` (scrolled bar tints, except in pure black, where it stays black); rows: `KitGroup`, `GroupTitle`, `KitNavRow`, `KitSwitchRow`, `KitRowIcon` (40dp tonal circle), `KitPad`, `KitSegmented`. |
| `Type.kt` | `kitTypography(FontSettings)` = Aria's tuned text styles (headlines 700, titles 600, primary 500, secondary 400, letter spacing 0, 0.92× size) for every app; `googleFontFamily()` via Play Services downloadable fonts; `AppFont` (prefs `kit_font`, used by Hiatus/Minutia/Regista; Aria keeps its DataStore); `FontCatalog`. |
| `FontPages.kt` | `FontRow`, `FontPageContent` (preview, typeface, size/weight/letter spacing) and `TypefaceContent` (search + category chips over every Google Font). Content only: each app hosts them in its own page with its own top bar ("Font" with Reset in ⋮, "Typeface"). |
| `Updater.kt` | `Updates`: check on every resume (≤ every 10 min) via the `releases/latest` redirect (no API rate limit), API + ETag fallback; resumable download into `noBackupFilesDir/kit-updates`, prefetched on unmetered, verified (same package, newer), deleted once installed; PackageInstaller with `USER_ACTION_NOT_REQUIRED`; `UpdateReceiver`. |
| `UpdateUi.kt` | `UpdateCard` (top of home, secondaryContainer, What's new, Update/Install, Later = until next start) and `UpdateRow` (Settings → About). No pop-up dialogs. |

Each app: `Kit.init` in `onCreate`, `Updates.onResume(ctx)` on every resume, `<receiver android:name="dev.savar.kit.UpdateReceiver" android:exported="false" />`
in the manifest, `REQUEST_INSTALL_PACKAGES`, and the `androidx.compose.ui:ui-text-google-fonts` dependency.

**Changing the kit:** edit it in one repo, copy the files to the other three unchanged, ship all four.
`scripts/kit-check.sh` diffs this repo's kit against the others' `main` on GitHub.
