package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory

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
        Text(text = "Pick a sample", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val SampleCategory.heading: String
    get() = when (this) {
        SampleCategory.Showcase -> "Showcase"
        SampleCategory.Catalog -> "Catalog"
    }
