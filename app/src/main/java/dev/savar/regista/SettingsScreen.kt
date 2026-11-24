@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.regista

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import dev.savar.kit.AppFont
import dev.savar.kit.FontPageContent
import dev.savar.kit.FontRow
import dev.savar.kit.FontSample
import dev.savar.kit.FontSettings
import dev.savar.kit.PureBlackRow
import dev.savar.kit.ThemeRow
import dev.savar.kit.TypefaceContent
import dev.savar.kit.UpdateRow

import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Sub-pages of Settings: the app font, and its typeface list. */
private enum class SettingsPage(val depth: Int) { FONT(1), TYPEFACE(2) }

/** Sample lines in the font preview. */
private val FONT_SAMPLE = FontSample("Real Madrid v Barcelona", "LaLiga · Sat 8:30 PM · in 2 days", "Up next")

@Composable
fun SettingsScreen(
    health: Health,
    onFixNotifications: () -> Unit,
    onFixExact: () -> Unit,
    onFixBattery: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = Store.settings
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val back: () -> Unit = { page = if (page == SettingsPage.TYPEFACE) SettingsPage.FONT else null }
    BackHandler(page != null) { back() }
    var fontMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var sourceDialog by remember { mutableStateOf(false) }
    var tokenDialog by remember { mutableStateOf(false) }
    var aheadDialog by remember { mutableStateOf(false) }
    var iconSheet by remember { mutableStateOf(false) }
    var appIcon by remember { mutableStateOf(AppIcon.current(ctx)) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // Material shared axis X, like the sibling apps: deeper pages come in from the right.
            val dir = if ((targetState?.depth ?: 0) > (initialState?.depth ?: 0)) 1 else -1
            (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> dir * w / 6 } + fadeIn(tween(220, delayMillis = 60)))
                .togetherWith(slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { w -> -dir * w / 6 } + fadeOut(tween(120)))
        },
        label = "settings",
    ) { shown ->
    when (shown) {
        SettingsPage.FONT -> Page("Font", onBack = back, actions = {
            Box {
                IconButton(onClick = { fontMenu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                    DropdownMenuItem(text = { Text("Reset to defaults") }, onClick = { fontMenu = false; confirmReset = true })
                }
            }
        }) { pad ->
            FontPageContent(AppFont.settings, FONT_SAMPLE, onTypeface = { page = SettingsPage.TYPEFACE },
                onChange = { AppFont.set(ctx, it) }, modifier = Modifier.padding(pad))
        }
        SettingsPage.TYPEFACE -> Page("Typeface", onBack = back) { pad ->
            TypefaceContent(AppFont.settings.font, onPick = { AppFont.set(ctx, AppFont.settings.copy(font = it)) },
                modifier = Modifier.padding(pad))
        }
        null -> Page("Settings") { pad ->
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Group("Reminders") {
                item {
                    SwitchRow("A day before", s.leadDay, icon = Icons.Rounded.Today) { on -> Store.updateSettings(ctx) { it.copy(leadDay = on) } }
                }
                item {
                    SwitchRow("An hour before", s.leadHour, icon = Icons.Rounded.Schedule) { on -> Store.updateSettings(ctx) { it.copy(leadHour = on) } }
                }
                item {
                    SwitchRow("5 minutes before", s.leadFive, icon = Icons.Rounded.Timer) { on -> Store.updateSettings(ctx) { it.copy(leadFive = on) } }
                }
                item {
                    NavRow(Icons.Rounded.NotificationsActive, "Sound and vibration", "Set each reminder separately") {
                        runCatching {
                            ctx.startActivity(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }

            Group("Reliability") {
                item {
                    StatusRow(Icons.Rounded.Notifications, "Notifications", health.notifications, "Allowed", "Off: tap to allow", onFixNotifications)
                }
                item {
                    StatusRow(Icons.Rounded.Schedule, "Exact alarms", health.exact, "On time, even in Doze", "Off: reminders may be late", onFixExact)
                }
                item {
                    StatusRow(Icons.Rounded.BatteryFull, "Battery", health.battery, "Unrestricted", "Optimised: OxygenOS may delay reminders", onFixBattery)
                }
            }

            Group("Look") {
                item { ThemeRow(ThemePrefs.mode.ordinal) { ThemePrefs.setMode(ctx, ThemeMode.entries[it]) } }
                item { PureBlackRow(ThemePrefs.pureBlack, enabled = isAppDark()) { ThemePrefs.setPureBlack(ctx, it) } }
                item {
                    FontRow(AppFont.settings) { page = SettingsPage.FONT }
                }
                item { AppIconRow(appIcon) { iconSheet = true } }
            }

            Group("Data") {
                item {
                    NavRow(Icons.Rounded.DateRange, "Look ahead", (Sync.LOOK_AHEAD.firstOrNull { it.first == s.lookAheadDays }?.second ?: "${s.lookAheadDays} days") + " of fixtures") { aheadDialog = true }
                }
                item {
                    NavRow(Icons.Rounded.DataObject, "Fixtures source", sourceSummary(s)) { sourceDialog = true }
                }
                item {
                    NavRow(
                        Icons.Rounded.Key,
                        "football-data.org token",
                        if (s.fdToken.isBlank()) "Not set: the fallback needs a free token" else "Set (${s.fdToken.take(4)}…)",
                    ) { tokenDialog = true }
                }
                item {
                    val st = Store.sync
                    NavRow(
                        Icons.Rounded.Refresh,
                        if (Store.syncing) "Updating…" else "Update now",
                        buildString {
                            append("Last updated ${Fmt.ago(st.at)}")
                            if (st.source.isNotBlank()) append(" from ${st.source}, ${st.count} fixtures")
                            if (st.error != null) append("\nLast try: ${st.error}")
                        },
                    ) { if (!Store.syncing) Sync.syncNow(ctx) }
                }
            }

            Group("About") {
                item { UpdateRow() }
                item {
                    NavRow(Icons.Rounded.Code, "Source code", "github.com/savar95x/regista") {
                        openUrl(ctx, "https://github.com/savar95x/regista")
                    }
                }
                item {
                    Pad {
                        Text(
                            "Fixtures from ESPN, with football-data.org as a fallback. Club ratings from clubelo.com. Regista only sends reminders: no live scores, no feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    }
    }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset font settings?") },
            text = { Text("Typeface, size, weight and letter spacing go back to their defaults.") },
            confirmButton = { TextButton(onClick = { AppFont.set(ctx, FontSettings()); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    if (iconSheet) AppIconSheet(appIcon, onPick = { AppIcon.set(ctx, it); appIcon = it }, onDismiss = { iconSheet = false })
    if (aheadDialog) {
        AlertDialog(
            onDismissRequest = { aheadDialog = false },
            title = { Text("Look ahead") },
            text = {
                Column {
                    Text(
                        "How far ahead Up next and reminders reach. Kick-off times further out are often not fixed yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Sync.LOOK_AHEAD.forEach { (days, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .selectable(selected = s.lookAheadDays == days, role = Role.RadioButton) {
                                    aheadDialog = false
                                    if (s.lookAheadDays != days) {
                                        Store.updateSettings(ctx) { it.copy(lookAheadDays = days) }
                                        Sync.syncNow(ctx)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.lookAheadDays == days, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { aheadDialog = false }) { Text("Done") } },
        )
    }
    if (sourceDialog) {
        AlertDialog(
            onDismissRequest = { sourceDialog = false },
            title = { Text("Fixtures source") },
            text = {
                Column {
                    SourcePref.entries.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = s.source == p, role = Role.RadioButton) {
                                    Store.updateSettings(ctx) { it.copy(source = p) }
                                    sourceDialog = false
                                    Sync.syncNow(ctx)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.source == p, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(p.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when (p) {
                                        SourcePref.AUTO -> "ESPN, falling back to football-data.org"
                                        SourcePref.ESPN -> "No key, every competition"
                                        SourcePref.FOOTBALL_DATA -> "Official API, top leagues + Champions League"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sourceDialog = false }) { Text("Done") } },
        )
    }
    if (tokenDialog) {
        var token by remember { mutableStateOf(s.fdToken) }
        AlertDialog(
            onDismissRequest = { tokenDialog = false },
            title = { Text("football-data.org token") },
            text = {
                Column {
                    Text(
                        "Only used when ESPN is unavailable. Register for free and paste the token from the email.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        singleLine = true,
                        label = { Text("Token") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { openUrl(ctx, "https://www.football-data.org/client/register") }) {
                        Text("Get a free token")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.updateSettings(ctx) { it.copy(fdToken = token) }
                    tokenDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text("Cancel") } },
        )
    }
}

private fun sourceSummary(s: Settings) = when (s.source) {
    SourcePref.AUTO -> if (s.fdToken.isBlank()) "Automatic: ESPN (add a token to enable the fallback)" else "Automatic: ESPN, falls back to football-data.org"
    else -> s.source.label
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    ok: Boolean,
    okText: String,
    badText: String,
    onFix: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !ok, onClick = onFix).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ok) RowIcon(icon) else RowIcon(icon, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ok) okText else badText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (ok) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Launcher-style tile for an app icon: the family's dark tile with the mark, cropped like a launcher does. */
@Composable
fun IconTile(icon: AppIcon, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0xFF1C1C1E)),
        contentAlignment = Alignment.Center,
    ) {
        // Launchers show the middle ~2/3 of the 108dp adaptive canvas.
        Image(painterResource(icon.foreground), contentDescription = null, modifier = Modifier.requiredSize(size * 1.5f))
    }
}

@Composable
private fun AppIconRow(icon: AppIcon, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("App icon", style = MaterialTheme.typography.bodyLarge)
            Text(icon.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Grid of every launcher icon; tapping one switches the home-screen icon straight away. */
@Composable
fun AppIconSheet(current: AppIcon, onPick: (AppIcon) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("App icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
            AppIcon.entries.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { icon ->
                        val selected = icon == current
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .selectable(selected = selected, role = Role.RadioButton) { if (!selected) onPick(icon) }
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconTile(icon, 64.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                icon.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "The home screen can take a few seconds to update. If Regista's icon disappears from it, add it again from the app drawer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
