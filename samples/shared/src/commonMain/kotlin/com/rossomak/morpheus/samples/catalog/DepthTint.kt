@file:Suppress("MagicNumber")

package com.rossomak.morpheus.samples.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.rossomak.morpheus.LocalMorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleCategory
import com.rossomak.morpheus.samples.SampleGrid
import com.rossomak.morpheus.samples.SampleTile
import com.rossomak.morpheus.samples.TunableSpec

private const val DEPTH_TINT = "Depth tint"

/**
 * Three layers, back to front: dense, small and faint, then sparse, large and strong. The front drifts most, which
 * reads as parallax.
 */
private val depthTintSpec = MorpheusGraphSpec(
    seed = 16,
    layers = listOf(
        fillSampleLayer.copy(
            density = 60f,
            nodeSizeDp = 1.4f,
            strokeWidthDp = 0.8f,
            driftAmplitude = 0.15f,
            alpha = 0.6f,
        ),
        fillSampleLayer.copy(
            density = 30f,
            nodeSizeDp = 2.4f,
            strokeWidthDp = 1.1f,
            driftAmplitude = 0.35f,
            alpha = 0.8f,
        ),
        fillSampleLayer.copy(
            density = 14f,
            nodeSizeDp = 4f,
            strokeWidthDp = 1.6f,
            driftAmplitude = 0.6f,
        ),
    ),
)

private val backTint = Color(0xFF4F6D99)
private val frontTint = Color(0xFFFF8A1F)

/**
 * The color of layer [layerIndex] of [layerCount], from a muted blue at the back to a bright orange in front. A lone
 * layer is the front.
 */
fun depthTintColor(layerIndex: Int, layerCount: Int): Color {
    require(layerIndex in 0 until layerCount) { "Layer $layerIndex isn't one of $layerCount layers" }
    val frontness = if (layerCount == 1) 1f else layerIndex / (layerCount - 1).toFloat()
    return lerp(backTint, frontTint, frontness)
}

private val singleColorFill = MorpheusGraphFill.Solid(frontTint)
private val depthTintFill = MorpheusGraphFill.Custom { context -> SolidColor(depthTintColor(context.layerIndex, context.layerCount)) }

/** The same three layers in one color and in a fill that colors each by how far back it sits. */
@Composable
fun DepthTintSample(modifier: Modifier = Modifier, spec: MorpheusGraphSpec = depthTintSpec) {
    SampleGrid(
        modifier = modifier,
        columns = 2,
        tiles = listOf(
            SampleTile("Single color") { SampleGraph(spec.copy(fill = singleColorFill)) },
            SampleTile(DEPTH_TINT) {
                CompositionLocalProvider(LocalMorpheusGraphFill provides depthTintFill) { SampleGraph(spec) }
            },
        ),
    )
}

internal val depthTintSample = Sample(
    id = "depth-tint",
    title = "Depth tint",
    description = "A custom fill that tints three layers by depth, from a muted blue at the back to a bright orange in front.",
    category = SampleCategory.Fills,
    specs = listOf(TunableSpec(DEPTH_TINT, depthTintSpec)),
    content = { specs -> DepthTintSample(spec = specs[DEPTH_TINT]) },
)
