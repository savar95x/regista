@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.kit

// Shared by Aria, Minutia, Hiatus and Regista. This file is byte-identical in all four repos:
// change it in one, copy it to the others (see KIT.md).

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun Updates.State.release(): Release? = when (this) {
    is Updates.State.Available -> release
    is Updates.State.Downloading -> release
    is Updates.State.Ready -> release
    is Updates.State.Installing -> release
    is Updates.State.Failed -> release
    else -> null
}

private fun mb(bytes: Long) = if (bytes > 0) "%.1f MB".format(bytes / 1_048_576f) else ""

/**
 * Top of the app's home when a newer release is out: what's new, then Update, which downloads (if it isn't
 * already) and installs, all in place. "Later" hides it until the app is next started.
 */
@Composable
fun UpdateCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    buttonShape: Shape = ButtonDefaults.shape,
) {
    val ctx = LocalContext.current
    val s = Updates.state
    val release = s.release() ?: return
    var hidden by remember(release.version) { mutableStateOf(release.version in Updates.later) }
    if (hidden && s !is Updates.State.Downloading && s !is Updates.State.Installing) return
    val scheme = MaterialTheme.colorScheme
    val on = scheme.onSecondaryContainer
    var expanded by remember { mutableStateOf(false) }
    Surface(color = scheme.secondaryContainer, shape = shape, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = on)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${Kit.config.name} ${release.version} is out",
                    style = MaterialTheme.typography.titleMedium, color = on, modifier = Modifier.weight(1f),
                )
            }
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    release.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = on.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp), shape = buttonShape,
                    colors = ButtonDefaults.textButtonColors(contentColor = on),
                ) {
                    Text(if (expanded) "Less" else "What's new")
                }
            } else Spacer(Modifier.height(12.dp))
            when (s) {
                is Updates.State.Downloading -> {
                    LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Downloading ${(s.progress * 100).toInt()}%" + if (release.size > 0) " of ${mb(release.size)}" else "",
                        style = MaterialTheme.typography.bodyMedium, color = on,
                    )
                }
                is Updates.State.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Installing… ${Kit.config.name} restarts when it's done", style = MaterialTheme.typography.bodyMedium, color = on)
                }
                else -> {
                    if (s is Updates.State.Failed) {
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { Updates.update(ctx) }, shape = buttonShape) {
                            Text(
                                when (s) {
                                    is Updates.State.Failed -> "Try again"
                                    is Updates.State.Ready -> "Install"
                                    else -> "Update" + if (release.size > 0) " (${mb(release.size)})" else ""
                                },
                            )
                        }
                        TextButton(
                            onClick = { Updates.later += release.version; hidden = true }, shape = buttonShape,
                            colors = ButtonDefaults.textButtonColors(contentColor = on),
                        ) { Text("Later") }
                    }
                }
            }
        }
    }
}

/**
 * Settings → About: the app and its version; below it, whether it's up to date or what's happening with an update.
 * Tap: check now, or update when one is available.
 */
@Composable
fun UpdateRow(iconShape: Shape = CircleShape) {
    val ctx = LocalContext.current
    val s = Updates.state
    val release = s.release()
    val busy = s is Updates.State.Checking || s is Updates.State.Downloading || s is Updates.State.Installing
    ListItem(
        modifier = Modifier.clickable(enabled = !busy) {
            if (release != null) Updates.update(ctx) else Updates.check(ctx, force = true)
        },
        leadingContent = { KitRowIcon(Icons.Rounded.SystemUpdate, shape = iconShape) },
        headlineContent = { Text("${Kit.config.name} ${Updates.installed(ctx)}") },
        supportingContent = {
            Column {
                Text(
                    when (s) {
                        Updates.State.Checking -> "Checking…"
                        Updates.State.UpToDate -> "You have the latest version"
                        is Updates.State.Available -> "${s.release.version} is out · tap to update"
                        is Updates.State.Downloading -> "Downloading ${s.release.version}… ${(s.progress * 100).toInt()}%"
                        is Updates.State.Ready -> "${s.release.version} downloaded · tap to install"
                        is Updates.State.Installing -> "Installing ${s.release.version}…"
                        is Updates.State.Failed -> s.message + " · tap to retry"
                        Updates.State.Idle -> "Tap to check for updates"
                    },
                    color = when (s) {
                        is Updates.State.Failed -> MaterialTheme.colorScheme.error
                        is Updates.State.Available, is Updates.State.Ready -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    },
                )
                if (s is Updates.State.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
