@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.parameters

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.mapLayers

/** The disc as nodes only, at an edge alpha of 0. */
private val nodesDiscSpec = parameterDiscSpec.mapLayers { it.copy(edgeAlpha = 0f) }

private val nodeRows = listOf(
    parameterRow("nodeSizeVariance", listOf(0f, 0.3f, 0.8f)) { copy(nodeSizeVariance = it) },
    parameterRow("depthDimming", listOf(0f, 0.5f, 1f)) { copy(depthDimming = it) },
    parameterRow("hotNodeShare", listOf(0f, 0.25f, 0.5f)) { copy(hotNodeShare = it) },
)

/** The disc's nodes at three size variances, depth dimmings and hot node shares. */
@Composable
fun NodesSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = nodesDiscSpec) {
    ParameterGrid(spec, nodeRows, modifier)
}

internal val nodesSample = parameterSample(
    id = "nodes",
    title = "Nodes",
    summary = "One disc at three nodeSizeVariance, depthDimming and hotNodeShare values.",
    description = "Each row changes one setting of the same disc's nodes, and an edgeAlpha of 0 leaves only the nodes. " +
        "The nodeSizeVariance row spreads node sizes around nodeSizeDp. " +
        "The depthDimming row gives each node a depth in its mesh, and dims and shrinks the far ones. " +
        "The hotNodeShare row makes some nodes hot: larger, opaque and glowing.",
    rows = nodeRows,
    spec = nodesDiscSpec,
) { NodesSample(spec = it) }
