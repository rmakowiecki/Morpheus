package com.rossomak.morpheus

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.test.Test

/**
 * Times the settles at a morph's start: both ends of the "Pills to ribbons" showcase and, on the front
 * layer, the comet transit mesh, all settled in one frame. The gallery's half-second averages dilute that
 * spike, so this measures it alone. Printed, not asserted; it only runs with `MORPHEUS_BENCHMARK` set:
 *
 * `MORPHEUS_BENCHMARK=1 ./gradlew :morpheus:jvmTest --tests '*SettleBenchmark' --rerun -i`
 */
class SettleBenchmark {

    @Test
    fun `settling the showcase's morph meshes`() {
        if (System.getenv("MORPHEUS_BENCHMARK") == null) return
        val morph = MorpheusGraphMorph(heroPills, heroRibbons, fraction = MID_FRACTION)
        for (layer in morphLayers(morph, MorpheusGraphFill.Solid(Color.White))) {
            val field = LayerField(layer.spec, layer.seed, max(WIDTH, HEIGHT), PX_PER_DP)
            field.shape = layer.shape
            field.advance(0f, WIDTH, HEIGHT)
            val shape = layer.shape as LayerShape.Morph
            repeat(WARM_UP_RUNS) { field.rebuildMorphMesh(shape, WIDTH, HEIGHT) }
            val nanos = LongArray(MEASURED_RUNS) {
                val started = System.nanoTime()
                field.rebuildMorphMesh(shape, WIDTH, HEIGHT)
                System.nanoTime() - started
            }.apply { sort() }
            val settlesBefore = field.settleCount
            field.rebuildMorphMesh(shape, WIDTH, HEIGHT)
            val meshes = if (field.settleCount - settlesBefore > 2) "two ends and the transit mesh" else "two ends"
            println(
                "slot ${layer.slotFromFront} (density ${layer.spec.density.toInt()}, $meshes): " +
                    "median ${nanos[nanos.size / 2].millis()} ms, fastest ${nanos.first().millis()} ms over $MEASURED_RUNS runs",
            )
        }
    }

    private fun Long.millis(): String = "%.3f".format(this / 1_000_000.0)

    private companion object {
        /** The benchmark recipe's 16:9 canvas, at its device scale. */
        const val WIDTH = 640f
        const val HEIGHT = 360f
        const val PX_PER_DP = 2f
        const val MID_FRACTION = 0.5f
        const val WARM_UP_RUNS = 200
        const val MEASURED_RUNS = 200
    }
}
