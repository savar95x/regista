@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.regista

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** System states that can stop reminders from arriving, re-read on every resume. */
data class Health(val notifications: Boolean = true, val exact: Boolean = true, val battery: Boolean = true)

@Composable
fun UpNextScreen(
    health: Health,
    batteryDismissed: Boolean,
    onFixNotifications: () -> Unit,
    onFixExact: () -> Unit,
    onFixBattery: () -> Unit,
    onDismissBattery: () -> Unit,
    onFollow: () -> Unit,
    openMatch: String?,
    onMatchOpened: () -> Unit,
) {
    val ctx = LocalContext.current
    // Ticks every minute so "in 12 min" and the "now" cut-off stay current.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val entries = remember(Store.matches, Store.follows, Store.bigTeams, Store.settings, Store.eloRanks, Store.nationRanks, now) { Store.upNext(now) }
    val upcoming = entries.filter { it.first.kickoff > now - 150 * 60_000L }
    var selected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openMatch) { if (openMatch != null) { selected = openMatch; onMatchOpened() } }

    Page("Up next") { pad ->
    PullToRefreshBox(
        isRefreshing = Store.syncing && Store.sync.at > 0,
        onRefresh = { Sync.syncNow(ctx) },
        modifier = Modifier.fillMaxSize().padding(pad),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { NextReminderLine(now) }

            item { UpdateCard() }

            if (!health.notifications) item {
                HealthCard(Icons.Rounded.NotificationsOff, "Notifications are off", "Regista can't remind you without them.", "Allow", onFixNotifications)
            } else if (!health.exact) item {
                HealthCard(Icons.Rounded.Schedule, "Reminders may be late", "Allow exact alarms so the 5-minute reminder lands on time.", "Allow", onFixExact)
            } else if (!health.battery && !batteryDismissed) item {
                HealthCard(Icons.Rounded.BatteryAlert, "Battery saver may block reminders", "Let Regista run unrestricted so OxygenOS doesn't hold alarms back.", "Fix", onFixBattery, onDismissBattery)
            }

            when {
                upcoming.isEmpty() && Store.sync.at == 0L -> item { Loading() }
                upcoming.isEmpty() -> item { Empty(onFollow) }
                else -> {
                    val days = upcoming.groupBy { Fmt.dayKey(it.first.kickoff) }
                    days.forEach { (_, list) ->
                        item(key = "h" + list.first().first.id) {
                            Text(
                                Fmt.dayHeader(list.first().first.kickoff, now),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 32.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                            )
                        }
                        items(list.size, key = { list[it].first.id }) { i ->
                            val (m, reasons) = list[i]
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = groupShape(i, list.size),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp).fillMaxWidth().animateItem(),
                            ) {
                                MatchRow(m, reasons, now, muted = m.id in Store.muted) { selected = m.id }
                            }
                        }
                    }
                }
            }
            item { SyncFooter() }
        }
    }
    }

    val sel = selected?.let { id -> entries.firstOrNull { it.first.id == id } ?: Store.matches.firstOrNull { it.id == id }?.let { it to emptyList() } }
    if (sel != null) MatchSheet(sel.first, sel.second) { selected = null }
}

@Composable
private fun NextReminderLine(now: Long) {
    val next = remember(Store.matches, Store.settings, Store.muted, Store.follows, Store.bigTeams, now) { Reminders.plan(now).firstOrNull() }
    val text = when {
        next != null -> "Next reminder ${Fmt.until(next.third, now)} · ${next.first.home.short} vs ${next.first.away.short}"
        Store.follows.isEmpty() && !Store.settings.bigMatchesOn -> "Follow teams to get reminders"
        else -> "No reminders scheduled"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun MatchRow(m: Match, reasons: List<Reason>, now: Long, muted: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (muted) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(84.dp)) {
            when {
                m.status == Status.LIVE || (m.kickoff <= now && m.status == Status.SCHEDULED) -> LivePill()
                m.status == Status.POSTPONED -> Text("Postponed", style = MaterialTheme.typography.labelLarge, color = scheme.error)
                m.timeTbd -> Text("TBD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                else -> {
                    Text(
                        Fmt.time(ctx, m.kickoff),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (m.status == Status.POSTPONED) TextDecoration.LineThrough else null,
                    )
                    if (m.kickoff - now < 24 * 3_600_000L) {
                        Text(Fmt.untilShort(m.kickoff, now), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(m.home)
            TeamLine(m.away)
            Text(
                listOfNotNull(m.compName, reasons.firstOrNull()?.label).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (muted) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
            contentDescription = if (muted) "Muted" else "Reminders on",
            tint = if (muted) scheme.onSurfaceVariant else scheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TeamLine(t: Team) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(t, 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LivePill() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
        Text(
            "Live",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun HealthCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(start = 36.dp, top = 4.dp, end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val on = MaterialTheme.colorScheme.onTertiaryContainer
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Not now", color = on) }
                TextButton(onClick = onAction) { Text(action, color = on, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Shown when GitHub has a newer release, until installed or dismissed for that version. */
@Composable
fun UpdateCard() {
    val ctx = LocalContext.current
    val s = Updater.state
    val rel = when (s) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.Failed -> s.release
        else -> null
    } ?: return
    var dismissed by remember(rel.version) { mutableStateOf(Updater.dismissedVersion(ctx) == rel.version) }
    if (dismissed && (s is Updater.State.Available || s is Updater.State.Ready)) return
    var notes by remember { mutableStateOf(false) }
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = on)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Regista ${rel.version}", style = MaterialTheme.typography.titleMedium, color = on)
                    Text(
                        when (s) {
                            is Updater.State.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                            is Updater.State.Installing -> "Installing…"
                            is Updater.State.Ready -> "Downloaded, ready to install"
                            is Updater.State.Failed -> s.message
                            else -> "Update available" + if (rel.size > 0) " · ${"%.1f".format(rel.size / 1_048_576f)} MB" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s is Updater.State.Failed) MaterialTheme.colorScheme.error else on,
                    )
                }
            }
            if (s is Updater.State.Downloading) {
                LinearWavyProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            if (s is Updater.State.Installing) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(start = 36.dp, end = 8.dp, top = 12.dp, bottom = 4.dp))
            }
            if (notes && rel.notes.isNotBlank()) {
                Text(
                    rel.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = on,
                    modifier = Modifier.padding(start = 36.dp, top = 8.dp, end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (rel.notes.isNotBlank()) TextButton(onClick = { notes = !notes }) { Text(if (notes) "Hide" else "What's new", color = on) }
                if (s is Updater.State.Available || s is Updater.State.Ready) {
                    TextButton(onClick = { Updater.dismiss(ctx, rel.version); dismissed = true }) { Text("Later", color = on) }
                }
                if (s !is Updater.State.Downloading && s !is Updater.State.Installing) {
                    TextButton(onClick = { Updater.update(ctx) }) {
                        Text(
                            when (s) {
                                is Updater.State.Failed -> "Retry"
                                is Updater.State.Ready -> "Install"
                                else -> "Update"
                            },
                            color = on,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pops up once per new version, over whatever tab is open: the release, its size and what's new, with Update
 * and Later. "Later" leaves the card on Up next and the row in Settings; it won't pop up again for that version.
 */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val rel = when (val s = Updater.state) {
        is Updater.State.Available -> s.release
        is Updater.State.Ready -> s.release
        else -> null
    } ?: return
    var shown by remember(rel.version) { mutableStateOf(Updater.promptedVersion(ctx) != rel.version) }
    if (!shown) return
    fun close() { Updater.markPrompted(ctx, rel.version); shown = false }
    AlertDialog(
        onDismissRequest = { close() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Regista ${rel.version} is out") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    buildString {
                        append("You have ${Updater.installedVersion(ctx)}.")
                        if (rel.size > 0) append(" The update is ${"%.1f".format(rel.size / 1_048_576f)} MB.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (rel.notes.isNotBlank()) {
                    Text("What's new", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(rel.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { close(); Updater.update(ctx) }) { Text("Update") } },
        dismissButton = { TextButton(onClick = { close() }) { Text("Later") } },
    )
}

@Composable
private fun Loading() {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LoadingIndicator(Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Fetching fixtures…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Store.sync.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Empty(onFollow: () -> Unit) {
    val nothing = Store.follows.isEmpty() && !Store.settings.bigMatchesOn
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = MaterialShapes.Cookie9Sided.toShape()
        Box(
            Modifier.size(112.dp).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
            Icon(Icons.Rounded.SportsSoccer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            if (nothing) "Nothing to remind you about" else "No matches in the next two weeks",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (nothing) "Follow a team, or turn on big matches." else "Follow more teams or widen what counts as a big match.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFollow) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Follow a team")
        }
    }
}

@Composable
private fun SyncFooter() {
    val s = Store.sync
    val text = buildString {
        append(if (Store.syncing) "Updating…" else "Updated ${Fmt.ago(s.at)}")
        if (s.source.isNotBlank()) append(" · ${s.source}")
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.error != null && s.at > 0) {
            Text(
                "Last try failed: ${s.error}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
    }
}

/** Match details: the face-off, why it's here, when each reminder fires, and a mute switch. */
@Composable
fun MatchSheet(m: Match, reasons: List<Reason>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val muted = m.id in Store.muted
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).animateContentSize()) {
            Text(
                listOfNotNull(m.compName, m.stage.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                FaceOffTeam(m.home, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        if (m.timeTbd) "TBD" else Fmt.time(ctx, m.kickoff),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        Fmt.dayWord(ctx, m.kickoff).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FaceOffTeam(m.away, Modifier.weight(1f))
            }
            if (m.venue.isNotBlank()) {
                Text(
                    m.venue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 24.dp, end = 24.dp),
                )
            }
            if (reasons.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    reasons.take(3).forEach { r ->
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                            Text(
                                r.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                Group("Reminders") {
                    val s = Store.settings
                    Lead.entries.filter { it.enabled(s) }.forEach { lead ->
                        item {
                            val at = m.kickoff - lead.ms
                            val past = at <= System.currentTimeMillis()
                            ReminderLine(
                                lead.channelName.replaceFirstChar { it.uppercase() },
                                when {
                                    m.timeTbd -> "When the time is set"
                                    past -> "Passed"
                                    else -> "${Fmt.dayWord(ctx, at).replaceFirstChar { it.uppercase() }}, ${Fmt.time(ctx, at)}"
                                },
                                active = !past && !muted,
                            )
                        }
                    }
                    item {
                        SwitchRow(
                            "Remind me about this match",
                            checked = !muted,
                            icon = Icons.Rounded.Notifications,
                        ) { on -> Store.setMuted(ctx, m.id, !on) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceOffTeam(t: Team, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(t, 64.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            t.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReminderLine(title: String, value: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
