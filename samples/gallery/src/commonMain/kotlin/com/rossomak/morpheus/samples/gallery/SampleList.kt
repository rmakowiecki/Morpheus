package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.ViewerNotice

/** Every sample under its category's heading; an empty category has no heading. */
@Composable
internal fun SampleList(samples: List<Sample>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val byCategory = samples.groupBy { it.category }
    LazyColumn(modifier = modifier) {
        item {
            Text(
                text = GALLERY_TITLE,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
        item { ViewerNoticeCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        SampleCategory.entries.forEach { category ->
            val categorySamples = byCategory[category].orEmpty()
            if (categorySamples.isNotEmpty()) {
                item(key = category) {
                    Text(
                        text = category.heading,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(categorySamples, key = { it.id }) { sample ->
                    val selected = sample.id == selectedId
                    ListItem(
                        headlineContent = { Text(sample.title) },
                        supportingContent = { Text(sample.description) },
                        colors = if (selected) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier.clickable { onSelect(sample.id) },
                    )
                }
            }
        }
    }
}

/** The wide layout's detail pane before a sample is picked. */
@Composable
internal fun NoSampleSelected(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Pick a sample from the list to start",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** The [ViewerNotice] for viewers sensitive to flashing lights, at the top of the list so it is seen before any sample plays. */
@Composable
internal fun ViewerNoticeCard(modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("${ViewerNotice.HEADLINE}. ") }
        append("${ViewerNotice.RISK} ${ViewerNotice.PAUSE_HINT}")
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private val SampleCategory.heading: String
    get() = when (this) {
        SampleCategory.StartHere -> "Start here"
        SampleCategory.Showcase -> "Showcase"
        SampleCategory.BasicShapes -> "Basic shapes"
        SampleCategory.Fills -> "Fills"
        SampleCategory.Morphs -> "Morphs"
    }
