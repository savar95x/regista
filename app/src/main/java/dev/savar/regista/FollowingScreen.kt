@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dev.savar.regista

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FollowingScreen(onSearch: () -> Unit, onBigSettings: () -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 104.dp)) {
            PageTitle("Following")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (Store.follows.isEmpty()) {
                    Text(
                        "Follow clubs or national teams to get reminders for their matches. Pick \"Big games only\" for teams you only half follow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    Group("Teams") {
                        Store.follows.sortedBy { it.team.name }.forEach { f ->
                            item { FollowRow(f) }
                        }
                    }
                }
                Group("Big matches") {
                    item {
                        SwitchRow(
                            "Big matches",
                            checked = Store.settings.bigMatchesOn,
                            icon = Icons.Rounded.EmojiEvents,
                            summary = "Reminders for the biggest games, whoever's playing",
                        ) { on -> Store.updateSettings(ctx) { it.copy(bigMatchesOn = on) } }
                    }
                    item { NavRow(Icons.Rounded.Tune, "What counts as big", bigSummary(), onBigSettings) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onSearch,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Follow") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun bigSummary(): String {
    val s = Store.settings
    val parts = mutableListOf("${Store.bigTeams.size} big teams")
    if (s.eloTopN > 0) parts += "Europe's top ${s.eloTopN}"
    if (s.derbiesOn) parts += "derbies"
    if (s.knockoutsOn) parts += "late knockouts"
    return parts.joinToString(", ")
}

@Composable
private fun FollowRow(f: Follow) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { menu = true }.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(f.team, 40.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(f.team.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (f.mode == FollowMode.ALL) "Every match" else "Big games only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Every match") },
                    onClick = { Store.setMode(ctx, f.team, FollowMode.ALL); menu = false },
                    trailingIcon = { if (f.mode == FollowMode.ALL) Icon(Icons.Rounded.Check, null) },
                )
                DropdownMenuItem(
                    text = { Text("Big games only") },
                    onClick = { Store.setMode(ctx, f.team, FollowMode.BIG); menu = false },
                    trailingIcon = { if (f.mode == FollowMode.BIG) Icon(Icons.Rounded.Check, null) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Unfollow") },
                    onClick = { Store.unfollow(ctx, f.team); menu = false },
                )
            }
        }
    }
}

/** Sub-page: the rules behind "big". */
@Composable
fun BigSettingsPage(onBack: () -> Unit, onAddBig: () -> Unit) {
    val ctx = LocalContext.current
    val s = Store.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }
        Text(
            "What counts as big",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        Text(
            "A match is big when both teams are big, it's a derby, or it's late in a knockout. Teams you follow on \"Big games only\" use the same rules from their side.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Group("Always") {
                item {
                    SwitchRow("Derbies and rivalries", s.derbiesOn, summary = "El Clásico, North London derby, Spain v Portugal…") { on ->
                        Store.updateSettings(ctx) { it.copy(derbiesOn = on) }
                    }
                }
                item {
                    SwitchRow(
                        "Late knockout rounds",
                        s.knockoutsOn,
                        summary = "Champions League from the quarter-finals, Europa League semis, World Cup knockouts, cup finals",
                    ) { on -> Store.updateSettings(ctx) { it.copy(knockoutsOn = on) } }
                }
            }
            Group("Strongest clubs") {
                item {
                    Pad {
                        Text("Also count Europe's top clubs by Elo rating", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Updated daily from clubelo.com, so in-form clubs count even if they aren't on your list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Segmented(listOf(0, 10, 20, 30, 50), s.eloTopN, { if (it == 0) "Off" else "Top $it" }) { n ->
                            Store.updateSettings(ctx) { it.copy(eloTopN = n) }
                            if (n > 0 && Store.eloRanks.isEmpty()) Sync.syncNow(ctx)
                        }
                    }
                }
            }
            Group("Big teams") {
                Store.bigTeams.sortedWith(compareBy<Team> { it.nation }.thenBy { it.name }).forEach { t ->
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Crest(t, 32.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { Store.removeBig(ctx, t) }) { Icon(Icons.Rounded.Close, "Remove ${t.name}") }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onAddBig) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add team")
                        }
                        TextButton(onClick = { Store.resetBig(ctx) }) { Text("Reset") }
                    }
                }
            }
        }
    }
}

enum class SearchPurpose { FOLLOW, BIG }

/** Full-height search over clubs and national teams, plus players (follow their club or country). */
@Composable
fun TeamSearchSheet(purpose: SearchPurpose, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var players by remember { mutableStateOf<List<PlayerHit>>(emptyList()) }
    var searchingPlayers by remember { mutableStateOf(false) }
    var loadingTeams by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Store.catalog.isEmpty()) {
            loadingTeams = true
            runCatching { Espn.teams() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { Store.saveCatalog(it) }
            loadingTeams = false
        }
    }
    LaunchedEffect(query) {
        players = emptyList()
        if (purpose != SearchPurpose.FOLLOW || query.trim().length < 3) return@LaunchedEffect
        delay(450)
        searchingPlayers = true
        players = Espn.searchPlayers(query, Store.catalog)
        searchingPlayers = false
    }

    val pool = remember(Store.catalog, Store.follows, Store.matches) {
        (Store.catalog + Store.follows.map { it.team } + Store.matches.flatMap { listOf(it.home, it.away) })
            .filter { it.id.isNotEmpty() }.distinctBy { it.id }
    }
    val results = remember(query, pool) { searchTeams(pool, query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Text(
                if (purpose == SearchPurpose.FOLLOW) "Follow a team" else "Add a big team",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text(if (purpose == SearchPurpose.FOLLOW) "Club, country or player" else "Club or country") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (loadingTeams && pool.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
                if (query.isBlank() && purpose == SearchPurpose.FOLLOW) {
                    item { SectionLabel("Suggestions") }
                    items(Rules.defaultBigTeams.take(12), key = { "s" + it.name }) { t ->
                        val real = pool.firstOrNull { it.key == t.key && it.nation == t.nation } ?: t
                        if (real.id.isNotEmpty()) TeamResult(real, purpose, onDismiss)
                    }
                }
                val clubs = results.filter { !it.nation }
                val nations = results.filter { it.nation }
                if (clubs.isNotEmpty()) {
                    item { SectionLabel("Clubs") }
                    items(clubs, key = { "c" + it.id }) { TeamResult(it, purpose, onDismiss) }
                }
                if (nations.isNotEmpty()) {
                    item { SectionLabel("National teams") }
                    items(nations, key = { "n" + it.id }) { TeamResult(it, purpose, onDismiss) }
                }
                if (purpose == SearchPurpose.FOLLOW && (players.isNotEmpty() || searchingPlayers)) {
                    item { SectionLabel("Players") }
                    if (searchingPlayers && players.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) } }
                    }
                    items(players, key = { "p" + it.id + it.name }) { PlayerResult(it) }
                }
                if (query.isNotBlank() && results.isEmpty() && players.isEmpty() && !searchingPlayers && !loadingTeams) {
                    item {
                        Text(
                            "No teams match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Prefix matches first, then word starts, then anywhere. Accents, case and "FC"-style filler ignored. */
fun searchTeams(pool: List<Team>, query: String, limit: Int = 30): List<Team> {
    val q = Names.norm(query)
    if (q.isBlank()) return emptyList()
    val canon = Names.canonical(query)
    return pool.mapNotNull { t ->
        val n = Names.norm(t.name)
        val s = Names.norm(t.short)
        val score = when {
            t.key == canon || n == q -> 0
            n.startsWith(q) || s.startsWith(q) -> 1
            n.split(' ').any { it.startsWith(q) } || t.abbr.equals(query.trim(), ignoreCase = true) -> 2
            n.contains(q) || t.key.contains(q) -> 3
            else -> return@mapNotNull null
        }
        Triple(t, score, n.length)
    }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }.take(limit)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TeamResult(t: Team, purpose: SearchPurpose, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val active = if (purpose == SearchPurpose.FOLLOW) Store.isFollowing(t) else Store.bigTeams.any { it.key == t.key }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable {
                if (purpose == SearchPurpose.FOLLOW) {
                    if (active) Store.unfollow(ctx, t) else Store.follow(ctx, t)
                } else {
                    if (!active) Store.addBig(ctx, t)
                    onDone()
                }
            }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(t, 36.dp)
        Spacer(Modifier.width(16.dp))
        Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (active) {
            FilledTonalIconButton(onClick = { if (purpose == SearchPurpose.FOLLOW) Store.unfollow(ctx, t) }) {
                Icon(if (purpose == SearchPurpose.FOLLOW) Icons.Rounded.Check else Icons.Rounded.Star, "Added")
            }
        } else {
            IconButton(onClick = {
                if (purpose == SearchPurpose.FOLLOW) Store.follow(ctx, t) else { Store.addBig(ctx, t); onDone() }
            }) { Icon(Icons.Rounded.Add, "Add ${t.name}") }
        }
    }
}

@Composable
private fun PlayerResult(p: PlayerHit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                if (p.subtitle.isNotBlank()) {
                    Text(p.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val options = listOfNotNull(p.club, p.nation)
        if (options.isEmpty()) {
            Text(
                "Couldn't find this player's teams",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        } else {
            FlowRow(Modifier.padding(start = 52.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { t ->
                    val following = Store.isFollowing(t)
                    Chip(selected = following, onClick = { if (following) Store.unfollow(ctx, t) else Store.follow(ctx, t) }) {
                        Text(t.short)
                    }
                }
            }
        }
    }
}
