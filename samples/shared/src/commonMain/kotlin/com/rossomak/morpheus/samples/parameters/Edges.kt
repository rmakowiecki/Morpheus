@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.parameters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.mapLayers

/** The disc as edges only, at a node size of 0, with wider strokes to make up for the missing dots. */
private val edgesDiscSpec = parameterDiscSpec.mapLayers { it.copy(nodeSizeDp = 0f, strokeWidthDp = 1.5f) }

private val edgeRows = listOf(
    // Without strict triangles, the edges a low keep chance drops leave holes in the mesh.
    parameterRow("edgeKeepChance", listOf(0.4f, 0.7f, 1f)) { copy(edgeKeepChance = it, strictTriangles = false) },
    parameterRow("pulseDepth", listOf(0f, 0.5f, 1f)) { copy(pulseDepth = it) },
    parameterRow("strokeWidthDp", listOf(1f, 2f, 3f)) { copy(strokeWidthDp = it) },
)

/** The disc's edges at three keep chances, pulse depths and stroke widths. */
@Composable
fun EdgesSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = edgesDiscSpec) {
    ParameterGrid(spec, edgeRows, modifier)
}

internal val edgesSample = parameterSample(
    id = "edges",
    title = "Edges",
    summary = "One disc at three edgeKeepChance, pulseDepth and strokeWidthDp values.",
    description = "Each row changes one setting of the same disc's edges: edgeKeepChance, pulseDepth or strokeWidthDp. " +
        "A nodeSizeDp of 0 leaves only the edges. " +
        "Each edge survives by its edgeKeepChance; with strict triangles off, the dropped ones leave holes in the mesh. " +
        "The edge pulse dims every edge toward a floor and back; at a pulseDepth of 0 the edges stay steady.",
    rows = edgeRows,
    spec = edgesDiscSpec,
) { EdgesSample(spec = it) }
