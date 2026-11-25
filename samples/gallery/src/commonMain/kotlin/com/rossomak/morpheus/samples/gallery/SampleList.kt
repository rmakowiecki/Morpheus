package com.rossomak.morpheus.samples.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleSection

/**
 * Every sample, with its summary, under its section's heading; an empty section has no heading. Above them sits
 * [header]: the landing page in the narrow layout, the [HomeTitle] in the wide one.
 */
@Composable
internal fun SampleList(samples: List<Sample>, selectedId: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier, header: @Composable () -> Unit) {
    val bySection = samples.groupBy { it.section }
    LazyColumn(modifier = modifier) {
        item { header() }
        SampleSection.entries.forEach { section ->
            val sectionSamples = bySection[section].orEmpty()
            if (sectionSamples.isNotEmpty()) {
                item(key = section) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(sectionSamples, key = { it.id }) { sample ->
                    val selected = sample.id == selectedId
                    ListItem(
                        headlineContent = { Text(sample.title) },
                        supportingContent = { Text(sample.summary) },
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

/** The wide layout's list title, which closes the open sample and shows the landing page again. */
@Composable
internal fun HomeTitle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = "Show the landing page", onClick = onClick).padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = Icons.Outlined.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = GALLERY_TITLE, style = MaterialTheme.typography.titleLarge)
    }
}
