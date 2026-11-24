package dev.savar.regista

import androidx.compose.foundation.layout.RowScope
import dev.savar.kit.KitGroup
import dev.savar.kit.KitGroupScope
import dev.savar.kit.KitNavRow
import dev.savar.kit.KitPad
import dev.savar.kit.KitRowIcon
import dev.savar.kit.KitSegmented
import dev.savar.kit.KitSwitchRow
import dev.savar.kit.kitTopBarColors

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Settings building blocks shared with Minutia and Hiatus, so the apps look and behave alike.

internal typealias GroupScope = KitGroupScope

/** Material 3 Expressive list group (Android 16 Settings style), from the shared kit. */
@Composable
internal fun Group(title: String? = null, modifier: Modifier = Modifier, build: GroupScope.() -> Unit) =
    KitGroup(title, modifier, build = build)

internal fun groupShape(i: Int, count: Int): RoundedCornerShape {
    val big = 24.dp
    val small = 6.dp
    return RoundedCornerShape(
        topStart = if (i == 0) big else small,
        topEnd = if (i == 0) big else small,
        bottomStart = if (i == count - 1) big else small,
        bottomEnd = if (i == count - 1) big else small,
    )
}

/** Padding for free-form content (chips, pickers) inside a group row. */
@Composable
internal fun Pad(content: @Composable ColumnScope.() -> Unit) = KitPad(content)

/** Leading icon in a tonal circle, as in Android 16 Settings. */
@Composable
internal fun RowIcon(
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) = KitRowIcon(icon, container, tint)

/** List row: icon, title, current value, chevron. Opens a sub-page. */
@Composable
internal fun NavRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) =
    KitNavRow(title, value, icon, onClick = onClick)

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    summary: String? = null,
    onChange: (Boolean) -> Unit,
) = KitSwitchRow(label, checked, summary, enabled, icon, onChange = onChange)

/** Material 3 single-choice segmented buttons. */
@Composable
internal fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) = KitSegmented(options, selected, label, modifier, onSelect = onSelect)

/** Things that come and go expand + fade instead of popping in. */
@Composable
internal fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220, delayMillis = 40)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(140)) + shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column { content() }
    }
}

/** Material filter chip: selected chips show a leading check. */
@Composable
internal fun Chip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}

internal fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * A screen with Material 3's large title bar: the title starts big and shrinks into the top bar as you scroll
 * (exit-until-collapsed), like Settings on Android 16 and the sibling apps. [onBack] adds a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    val black = isAppDark() && ThemePrefs.pureBlack
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = scheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
                windowInsets = WindowInsets(0, 0, 0, 0), // the app's scaffold already pads for the status bar
                actions = actions,
                colors = kitTopBarColors(black),
            )
        },
        content = content,
    )
}

/** Big page title used at the top of each tab. */
@Composable
internal fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
    )
}
