package com.rossomak.morpheus.samples.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.morpheusGraphBackground
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.TunableSpec

/** The smallest integration: one modifier and a preset, colored by the inherited fill. */
@Composable
fun MinimalSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = MorpheusGraphPresets.baseline) {
    Box(modifier.fillMaxSize().morpheusGraphBackground(spec))
}

private const val BASELINE = "Baseline"

internal val minimalSample = Sample(
    id = "minimal",
    title = "Minimal",
    description = "One modifier and a preset, colored by the inherited fill.",
    category = SampleCategory.Catalog,
    specs = listOf(TunableSpec(BASELINE, MorpheusGraphPresets.baseline)),
    content = { specs -> MinimalSample(spec = specs[BASELINE]) },
)
