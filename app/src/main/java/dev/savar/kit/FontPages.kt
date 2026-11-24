@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.kit

// Shared by Aria, Minutia, Hiatus and Regista. This file is byte-identical in all four repos:
// change it in one, copy it to the others (see KIT.md).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Two lines of the app's own kind of text for the font preview, e.g. "Song title" / "Artist · Album · 3:45". */
data class FontSample(val primary: String, val secondary: String, val heading: String)

/** The "Font" row for an app's settings: opens the font page. */
@Composable
fun FontRow(s: FontSettings, iconShape: Shape = CircleShape, onClick: () -> Unit) {
    KitNavRow("Font", s.font ?: "System default", Icons.Rounded.FontDownload, iconShape = iconShape, onClick = onClick)
}

/**
 * The font page's content (the host app supplies the page and its top bar, titled "Font", with Reset in ⋮):
 * a live preview, the typeface (opens the typeface page), then size, weight and letter spacing.
 * Sliders are live while dragging and saved ([onChange]) when let go.
 */
@Composable
fun FontPageContent(
    s: FontSettings,
    sample: FontSample,
    onTypeface: () -> Unit,
    onChange: (FontSettings) -> Unit,
    modifier: Modifier = Modifier,
    previewShape: Shape = RoundedCornerShape(GroupOuter),
    groupShape: (outer: androidx.compose.ui.unit.Dp, inner: androidx.compose.ui.unit.Dp, first: Boolean, last: Boolean) -> Shape = ::groupShape,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FontPreview(s.font, sample, previewShape)
        KitGroup("Typeface", shape = groupShape) {
            item { KitNavRow(s.font ?: "System default", "Any Google Font", Icons.Rounded.FontDownload, onClick = onTypeface) }
        }
        KitGroup("Text style", shape = groupShape) {
            item { TextStyleControls(s, onChange) }
        }
    }
}

/** What the app looks like in the chosen font: uses the live theme, so it updates as you pick. */
@Composable
private fun FontPreview(current: String?, sample: FontSample, shape: Shape) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Aa", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                Text(
                    current ?: "System default",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(sample.primary, style = MaterialTheme.typography.bodyLarge)
            Text(sample.secondary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(sample.heading, style = MaterialTheme.typography.headlineSmall)
            Text("0123456789 · The quick brown fox", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextStyleControls(s: FontSettings, onChange: (FontSettings) -> Unit) {
    var size by remember(s.textScale) { mutableFloatStateOf(s.textScale) }
    var weight by remember(s.weightDelta) { mutableFloatStateOf(s.weightDelta.toFloat()) }
    var spacing by remember(s.letterSpacing) { mutableFloatStateOf(s.letterSpacing) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        StyleSlider("Size", "${(size * 100).roundToInt()}%", size, 0.8f..1.3f, steps = 9, onChange = { size = it }) {
            onChange(s.copy(textScale = size))
        }
        StyleSlider("Weight", weightLabel(weight.roundToInt()), weight, -300f..300f, steps = 5, onChange = { weight = it }) {
            onChange(s.copy(weightDelta = weight.roundToInt()))
        }
        StyleSlider("Letter spacing", spacingLabel(spacing), spacing, -5f..5f, steps = 0, onChange = { spacing = it }) {
            // Snap to "default" near zero so it's easy to get back to the font's own spacing.
            spacing = if (abs(spacing) < 0.15f) 0f else (spacing * 10).roundToInt() / 10f
            onChange(s.copy(letterSpacing = spacing))
        }
    }
}

private fun weightLabel(delta: Int) = when {
    delta == 0 -> "Default"
    delta < 0 -> "Lighter ${-delta / 100}"
    else -> "Bolder ${delta / 100}"
}

private fun spacingLabel(px: Float) = if (abs(px) < 0.15f) "Default" else "%+.1f px".format(px)

@Composable
private fun StyleSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value = current, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, steps = steps)
}

/**
 * The typeface page's content (host supplies the page, titled "Typeface"): search and style filters stay pinned
 * at the top, the rest is every Google Font, each previewed in itself. Tapping one applies it (a tick marks it).
 */
@Composable
fun TypefaceContent(current: String?, onPick: (String?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val catalog = remember { FontCatalog(context) }
    val fonts by produceState<List<FontEntry>?>(null) { value = catalog.load() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<FontCategory?>(null) }
    val q = query.trim()
    val shown = remember(fonts, q, category) {
        fonts.orEmpty().filter { (category == null || it.category == category) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
    }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search all Google Fonts") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
            items(FontCategory.entries) { c ->
                FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        ) {
            if (q.isEmpty() && category == null) {
                item(key = "system") { TypefaceRow("System default", FontFamily.Default, "Your phone's font", current == null) { onPick(null) } }
            }
            when {
                fonts == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                shown.isEmpty() -> item(key = "none") {
                    Text(
                        "No Google Font matches “$q”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> items(shown, key = { it.name }) { f ->
                    TypefaceRow(f.name, googleFontFamily(f.name), f.category.label, current == f.name) { onPick(f.name) }
                }
            }
        }
    }
}

@Composable
private fun TypefaceRow(name: String, family: FontFamily, caption: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = family), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
