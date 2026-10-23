package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import io.kotest.matchers.collections.shouldBeEmpty
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test

/**
 * Keeps "Show violations" clean on every gallery sample's still frames: Minimal (the [MorpheusGraphPresets.baseline]
 * preset) and both ends of the "Pills to ribbons" showcase, plus the frames where the showcase's morph
 * ends and settles. Each frame is checked as the overlay rings it, through [LayerField.nodeViolations]
 * and [LayerField.ringedConnectivityViolations]. Stepped at 30 fps from t = 0, as [GoldenTopologyTest] steps.
 */
class GalleryStillViolationsTest {

    @Test
    fun `every gallery sample's still frames show no violations`() {
        val stills = listOf("minimal" to MorpheusGraphPresets.baseline, "hero pills" to heroPills, "hero ribbons" to heroRibbons)
        val violations = stills.flatMap { (name, spec) ->
            spec.layers.flatMapIndexed { index, layer -> stillViolations("$name/layer $index", layer) }
        }

        violations.shouldBeEmpty()
    }

    @Test
    fun `the showcase morph shows no violations where it ends and settles`() {
        val violations = SEEDS.flatMap { seed -> SURFACES.flatMap { (width, height) -> morphViolations(seed, width, height) } }

        violations.shouldBeEmpty()
    }

    private fun stillViolations(label: String, layer: MorpheusGraphLayerSpec): List<String> =
        SEEDS.flatMap { seed -> SURFACES.flatMap { (width, height) -> stillViolations("$label/seed $seed", layer, seed, width, height) } }

    private fun stillViolations(label: String, layer: MorpheusGraphLayerSpec, seed: Int, width: Float, height: Float): List<String> {
        val field = LayerField(layer, seed, max(width, height), PX_PER_DP)
        val violations = mutableListOf<String>()
        for (frame in 0..STILL_FRAMES.last()) {
            field.advance(frame * FRAME_SECONDS, width, height)
            if (frame in STILL_FRAMES) violations += overlayViolations("$label/${width.toInt()}x${height.toInt()}/frame $frame", field, frame)
        }
        return violations
    }

    /** One field per mesh, kept across frames the way the modifier keeps them. */
    private fun morphViolations(seed: Int, width: Float, height: Float): List<String> {
        val morph = MorpheusGraphMorph(heroPills.copy(seed = seed), heroRibbons.copy(seed = seed), fraction = 0f)
        val fields = HashMap<String, LayerField>()
        val violations = mutableListOf<String>()
        for (frame in 0..MORPH_SETTLED_FRAME) {
            val fraction = min(frame / MORPH_FRAMES.toFloat(), 1f)
            for (layer in morphLayers(morph.copy(fraction = fraction), TEST_FILL)) {
                val mesh = "slot ${layer.slotFromFront}/density ${layer.spec.density.toInt()}"
                val field = fields.getOrPut(mesh) { LayerField(layer.spec, layer.seed, max(width, height), PX_PER_DP) }
                field.spec = layer.spec
                field.shape = layer.shape
                field.advance(frame * FRAME_SECONDS, width, height)
                if (frame in MORPH_CHECKED_FRAMES) violations += overlayViolations("morph/seed $seed/${width.toInt()}x${height.toInt()}/frame $frame/$mesh", field, frame)
            }
        }
        return violations
    }

    private fun overlayViolations(where: String, field: LayerField, frame: Int): List<String> {
        val nodes = field.nodeViolations(frame * FRAME_SECONDS)
        val connectivity = field.ringedConnectivityViolations()
        return listOfNotNull(
            "$where: nodes ${nodes.take(MAX_LISTED)}".takeIf { nodes.isNotEmpty() },
            "$where: graphs too small ${connectivity.smallGraphNodes.take(MAX_LISTED)}".takeIf { connectivity.smallGraphNodes.isNotEmpty() },
            "$where: cut nodes ${connectivity.cutNodes.take(MAX_LISTED)}".takeIf { connectivity.cutNodes.isNotEmpty() },
        )
    }

    private companion object {
        val SEEDS = listOf(1, 42, 9_001)

        /** The standard surfaces and the showcase's own canvas. */
        val SURFACES = listOf(640f to 360f, 360f to 640f, 540f to 270f)
        const val FRAME_SECONDS = 1f / 30f
        val STILL_FRAMES = listOf(0, 45, 150)
        const val MORPH_FRAMES = 60
        const val MORPH_SETTLED_FRAME = 75

        /** The morph's still start, its end and its settled frame. */
        val MORPH_CHECKED_FRAMES = listOf(0, MORPH_FRAMES, MORPH_SETTLED_FRAME)
        const val MAX_LISTED = 10
        const val PX_PER_DP = 2.75f
        val TEST_FILL = MorpheusGraphFill.Solid(Color.White)
    }
}
