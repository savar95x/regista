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
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
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
    Page("Following") { pad ->
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Group("Teams") {
                    item {
                        ListItem(
                            modifier = Modifier.clickable(onClick = onSearch),
                            leadingContent = {
                                RowIcon(Icons.Rounded.Add, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            },
                            headlineContent = { Text("Follow a team", color = MaterialTheme.colorScheme.primary) },
                            supportingContent = { Text("Club, country or player") },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    Store.follows.sortedBy { it.team.name }.forEach { f ->
                        item { FollowRow(f) }
                    }
                }
                if (Store.follows.isEmpty()) {
                    Text(
                        "Get reminders for every match of a team, or pick \"Big games only\" for teams you half follow.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
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
    }
}

private fun bigSummary(): String {
    val s = Store.settings
    val parts = mutableListOf("${Store.bigTeams.size} big teams")
    if (s.eloTopN > 0) parts += "top ${s.eloTopN} clubs"
    if (s.nationTopN > 0) parts += "top ${s.nationTopN} nations"
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
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Team?>(null) }
    confirmRemove?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            icon = { Crest(t, 40.dp) },
            title = { Text("Remove ${t.name}?") },
            text = {
                Text(
                    "${t.short} won't count as big any more" +
                        (if (s.eloTopN > 0 && !t.nation || s.nationTopN > 0 && t.nation) " unless it's among the strongest right now" else "") +
                        ". Their matches against other big teams will stop showing unless you follow them.",
                )
            },
            confirmButton = {
                TextButton(onClick = { Store.removeBig(ctx, t); confirmRemove = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
    if (confirmReset) {
        val added = Store.bigTeams.count { t -> Rules.defaultBigTeams.none { it.key == t.key } }
        val removed = Rules.defaultBigTeams.count { d -> Store.bigTeams.none { it.key == d.key } }
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text("Reset big teams?") },
            text = {
                Text(
                    buildString {
                        append("Your list goes back to the ${Rules.defaultBigTeams.size} default teams.")
                        if (added > 0) append(" $added team${if (added == 1) "" else "s"} you added will be removed.")
                        if (removed > 0) append(" $removed you removed will come back.")
                        append(" This can't be undone.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { Store.resetBig(ctx); confirmReset = false }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
    Page("What counts as big", onBack = onBack) { pad ->
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text(
            "A match is big when both teams are big, it's a derby, or it's late in a knockout. A team is big if it's on your list below or among the strongest right now.",
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
            Group("Strongest right now") {
                item {
                    StrengthRow(
                        title = "Clubs",
                        scope = "in Europe",
                        source = "Club Elo, updated daily",
                        value = s.eloTopN,
                        max = 50,
                        ranks = Store.eloRanks,
                        nation = false,
                    ) { n ->
                        Store.updateSettings(ctx) { it.copy(eloTopN = n) }
                        if (n > 0 && Store.eloRanks.isEmpty()) Sync.syncNow(ctx)
                    }
                }
                item {
                    StrengthRow(
                        title = "National teams",
                        scope = "in the world",
                        source = "World Football Elo, updated weekly",
                        value = s.nationTopN,
                        max = 30,
                        ranks = Store.nationRanks,
                        nation = true,
                    ) { n -> Store.updateSettings(ctx) { it.copy(nationTopN = n) } }
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
                            IconButton(onClick = { confirmRemove = t }) { Icon(Icons.Rounded.Close, "Remove ${t.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        val isDefault = Store.bigTeams.map { it.key }.toSet() == Rules.defaultBigTeams.map { it.key }.toSet()
                        if (!isDefault) {
                            TextButton(onClick = { confirmReset = true }) {
                                Text("Reset to defaults", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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

/** "Top N by Elo" control: a stepped slider (0 = off) and the teams that currently qualify. */
@Composable
private fun StrengthRow(
    title: String,
    scope: String,
    source: String,
    value: Int,
    max: Int,
    ranks: Map<String, Int>,
    nation: Boolean,
    onChange: (Int) -> Unit,
) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    var showList by remember { mutableStateOf(false) }
    val n = (v / 5).roundToInt() * 5
    Pad {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (n == 0) "Off" else "Top $n",
                style = MaterialTheme.typography.titleMedium,
                color = if (n == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (n == 0) "Only your list counts" else "The $n strongest $scope also count as big · $source",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { if (n != value) onChange(n) },
            valueRange = 0f..max.toFloat(),
            steps = max / 5 - 1,
        )
        if (n > 0) {
            val names = remember(ranks, n) { topNames(ranks, n, nation) }
            if (names.isEmpty()) {
                Text("Ratings load on the next update", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = { showList = !showList }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (showList) "Hide list" else "See who's in")
                }
                Reveal(showList) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { i, t ->
                            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(50)) {
                                Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Crest(t, 22.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${i + 1}  ${t.short}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Top [n] teams by rank, as real teams from the catalogue where possible (for names and crests). */
private fun topNames(ranks: Map<String, Int>, n: Int, nation: Boolean): List<Team> {
    val byKey = Store.catalog.filter { it.nation == nation }.associateBy { it.key }
    return ranks.entries.filter { it.value <= n }.sortedBy { it.value }.map { (key, _) ->
        byKey[key] ?: Team(id = "", name = key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }, nation = nation)
    }
}
