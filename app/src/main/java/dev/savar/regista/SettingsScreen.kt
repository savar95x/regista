@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.regista

import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
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
    var fontSheet by remember { mutableStateOf(false) }
    var fontBusy by remember { mutableStateOf<String?>(null) }
    var fontError by remember { mutableStateOf<String?>(null) }
    var sourceDialog by remember { mutableStateOf(false) }
    var tokenDialog by remember { mutableStateOf(false) }

    fun pickFont(name: String) {
        fontBusy = name
        fontError = null
        scope.launch {
            Fonts.use(ctx, name).onFailure { fontError = it.message }
            fontBusy = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        PageTitle("Settings")
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
                item {
                    Pad {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Segmented(ThemeMode.entries, ThemePrefs.mode, { it.label }) { ThemePrefs.setMode(ctx, it) }
                    }
                }
                item {
                    SwitchRow(
                        "Pure black",
                        ThemePrefs.pureBlack,
                        enabled = isAppDark(),
                        icon = Icons.Rounded.Contrast,
                        summary = "For AMOLED screens, in dark theme",
                    ) { ThemePrefs.setPureBlack(ctx, it) }
                }
                item {
                    Pad {
                        Text("Font", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (Fonts.family.isBlank()) "System font" else Fonts.family,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(selected = Fonts.family.isBlank(), onClick = { Fonts.useSystem(ctx) }) { Text("System") }
                            Fonts.picks.forEach { name ->
                                Chip(selected = Fonts.family.equals(name, true), onClick = { pickFont(name) }) {
                                    Text(if (fontBusy == name) "$name…" else name)
                                }
                            }
                            val custom = Fonts.family.isNotBlank() && Fonts.picks.none { it.equals(Fonts.family, true) }
                            if (custom) Chip(selected = true, onClick = { fontSheet = true }) { Text(Fonts.family) }
                            Chip(selected = false, onClick = { fontSheet = true }) {
                                Icon(Icons.Rounded.Search, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("All Google Fonts")
                            }
                        }
                        fontError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }

            Group("Data") {
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
                item {
                    val version = remember {
                        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
                    }
                    NavRow(Icons.Rounded.Code, "Regista $version", "github.com/savar95x/regista") {
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

    if (fontSheet) {
        FontBrowserSheet(selected = Fonts.family, onPick = { pickFont(it); fontSheet = false }, onDismiss = { fontSheet = false })
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

private val CATEGORIES = listOf(
    "All" to null,
    "Sans" to "Sans Serif",
    "Serif" to "Serif",
    "Display" to "Display",
    "Mono" to "Monospace",
    "Handwriting" to "Handwriting",
)

/** Every Google Fonts family, searchable and filterable, each previewed in its own face. */
@Composable
fun FontBrowserSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val families by produceState(Fonts.cachedFamilies(ctx)) { value = Fonts.loadCatalogue(ctx) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val shown = remember(families, query, category) {
        val q = query.trim().lowercase().replace(" ", "")
        families.filter { f ->
            (category == null || f.category == category) && (q.isEmpty() || f.name.lowercase().replace(" ", "").contains(q))
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, category) { listState.scrollToItem(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text("Google Fonts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search ${if (families.isEmpty()) "" else "${families.size} "}fonts") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES) { (label, value) -> Chip(selected = category == value, onClick = { category = value }) { Text(label) } }
            }
            HorizontalDivider()
            if (families.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(shown, key = { it.name }) { fam -> FontRow(fam, fam.name.equals(selected, ignoreCase = true)) { onPick(fam.name) } }
            }
        }
    }
}

@Composable
private fun FontRow(fam: Fonts.Family, isSelected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val tf by produceState<Typeface?>(null, fam.name) { value = Fonts.previewTypeface(ctx, fam.name) }
    val family = remember(tf) { tf?.let { FontFamily(it) } }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                fam.name,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = family,
                color = if (family == null) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Fonts.PREVIEW_TEXT,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = family,
                color = scheme.onSurfaceVariant,
            )
        }
        if (isSelected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = scheme.primary)
    }
}
