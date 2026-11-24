@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.kit

// Shared by Aria, Minutia, Hiatus and Regista. This file is byte-identical in all four repos:
// change it in one, copy it to the others (see KIT.md).

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What the shared code needs to know about the app it's in. Set once, before anything else from the kit runs
 * (each app does it in its Application or MainActivity.onCreate).
 */
data class KitApp(
    /** Shown in the update card: "Hiatus 0.12.0 is out". */
    val name: String,
    /** GitHub "owner/repo" whose Releases carry the APKs. */
    val repo: String,
    /** The APK asset's file name for a version ("Hiatus-0.12.0.apk"; Aria's is always "aria.apk"). */
    val apkName: (version: String) -> String,
    /** Release notes from CHANGELOG.md's "## [X.Y.Z]" section; false = the release's own text. */
    val changelogNotes: Boolean = true,
)

object Kit {
    @Volatile private var app: KitApp? = null
    fun init(app: KitApp) { this.app = app }
    val config: KitApp get() = app ?: error("Kit.init() wasn't called")
}

// ------------------------------------------------------------------ theme

/**
 * Pure black (AMOLED) take on a dark scheme, the same recipe in every app: the page goes to #000, raised
 * containers are pulled most of the way to black but keep a hint of the wallpaper's hue.
 */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.45f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.55f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.65f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.75f),
    surfaceVariant = lerp(Color.Black, surfaceVariant, 0.7f),
)

/**
 * Top bar colours for every app: Material's tinted surface once content scrolls under it, except in pure black,
 * where the bar stays black like the page. [flat] = nothing scrolls under it (e.g. a pinned preview).
 */
@Composable
fun kitTopBarColors(pureBlack: Boolean, flat: Boolean = false): TopAppBarColors {
    val surface = MaterialTheme.colorScheme.surface
    return if (pureBlack || flat) {
        TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surface)
    } else {
        TopAppBarDefaults.topAppBarColors(containerColor = surface)
    }
}

// ------------------------------------------------------------------ rows (Android 16 Settings style)

class KitGroupScope {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

/** Corner radius of a group's outer edges, and of the edges between rows. */
val GroupOuter: Dp = 24.dp
val GroupInner: Dp = 6.dp

/** A group's section title. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

/**
 * A titled group of rows (Material 3 Expressive list): each row its own surfaceContainer tile, 2dp apart,
 * big corners outside and small ones inside. Rows added conditionally animate the group's size.
 */
@Composable
fun KitGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    shape: (outer: Dp, inner: Dp, first: Boolean, last: Boolean) -> Shape = ::groupShape,
    build: KitGroupScope.() -> Unit,
) {
    val items = KitGroupScope().apply(build).items
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        if (title != null) GroupTitle(title)
        Column(
            Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEachIndexed { i, content ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = shape(GroupOuter, GroupInner, i == 0, i == items.lastIndex),
                    modifier = Modifier.fillMaxWidth(),
                ) { content() }
            }
        }
    }
}

fun groupShape(outer: Dp, inner: Dp, first: Boolean, last: Boolean): Shape = RoundedCornerShape(
    topStart = if (first) outer else inner, topEnd = if (first) outer else inner,
    bottomStart = if (last) outer else inner, bottomEnd = if (last) outer else inner,
)

/** Free-form content in a group row, with ListItem's insets so it lines up with the other rows. */
@Composable
fun KitPad(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 16.dp, bottom = 16.dp), content = content)
}

/** A row's icon: 40dp tonal circle, the same in every app's settings. */
@Composable
fun KitRowIcon(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: Shape = CircleShape,
) {
    Box(Modifier.size(40.dp).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** A row that opens something: icon, title, current value, chevron. */
@Composable
fun KitNavRow(
    title: String,
    value: String?,
    icon: ImageVector?,
    enabled: Boolean = true,
    iconShape: Shape = CircleShape,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        leadingContent = icon?.let { { KitRowIcon(it, shape = iconShape) } },
        headlineContent = { Text(title) },
        supportingContent = value?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        trailingContent = trailing ?: {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A whole-row switch with a check in the thumb. */
@Composable
fun KitSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconShape: Shape = CircleShape,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        leadingContent = icon?.let { { KitRowIcon(it, shape = iconShape) } },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked, onCheckedChange = null, enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Material segmented buttons with the stock animated check. */
@Composable
fun <T> KitSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape? = null,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = if (shape != null) SegmentedButtonDefaults.itemShape(i, options.size, shape)
                else SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(o), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A row title with its round icon, lined up like a ListItem's; [trailing] sits at the end. */
@Composable
fun KitIconTitle(icon: ImageVector?, title: String, iconShape: Shape = CircleShape, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { KitRowIcon(icon, shape = iconShape); Spacer(Modifier.width(16.dp)) }
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Light / dark choice, the same in every app's settings. [selected]: 0 = follow the system, 1 = light, 2 = dark. */
@Composable
fun ThemeRow(selected: Int, iconShape: Shape = CircleShape, segmentShape: CornerBasedShape? = null, onSelect: (Int) -> Unit) {
    KitPad {
        KitIconTitle(Icons.Rounded.DarkMode, "Theme", iconShape)
        Spacer(Modifier.height(12.dp))
        KitSegmented(listOf(0, 1, 2), selected, { listOf("System", "Light", "Dark")[it] }, shape = segmentShape, onSelect = onSelect)
    }
}

/** The pure black switch, the same in every app's settings (only in dark theme). */
@Composable
fun PureBlackRow(checked: Boolean, enabled: Boolean, iconShape: Shape = CircleShape, onChange: (Boolean) -> Unit) {
    KitSwitchRow(
        "Pure black", checked, summary = "For AMOLED screens, in dark theme", enabled = enabled,
        icon = Icons.Rounded.Contrast, iconShape = iconShape, onChange = onChange,
    )
}
