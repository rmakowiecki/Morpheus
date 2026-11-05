@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.parameters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec

/** The jitter row holds its nodes still, so at a jitter of 0 they sit on the cell grid. */
private val layoutRows = listOf(
    parameterRow("density", listOf(30f, 60f, 90f)) { copy(density = it) },
    parameterRow("fillChance", listOf(0.4f, 0.55f, 1f)) { copy(fillChance = it) },
    parameterRow("jitter", listOf(0f, 0.3f, 0.9f)) { copy(jitter = it, driftAmplitude = 0f) },
)

/** The disc at three cell densities, fill chances and jitters. */
@Composable
fun LayoutSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = parameterDiscSpec) {
    ParameterGrid(spec, layoutRows, modifier)
}

internal val layoutSample = parameterSample(
    id = "layout",
    title = "Layout",
    summary = "One disc at three cell densities, fill chances and jitters.",
    description = "Each row changes one setting of the same disc: cell density, fill chance or jitter. " +
        "Cell density sets how many cells span the window, and fill chance how many of them activate. " +
        "Jitter moves each home off its cell's center. Its row has no drift, so a jitter of 0 shows the bare cell grid.",
    rows = layoutRows,
    spec = parameterDiscSpec,
) { LayoutSample(spec = it) }
