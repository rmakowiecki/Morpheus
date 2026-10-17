package com.rossomak.morpheus.tuner

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rossomak.morpheus.GradientStop
import com.rossomak.morpheus.MorpheusGraphFill
import com.rossomak.morpheus.MorpheusGraphLayerSpec
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.ShapePrimitive

// Each primitive's constructor defaults, read off a default instance so they never drift from the lib.
private val UniformDefaults = ShapePrimitive.Uniform()
private val RibbonDefaults = ShapePrimitive.Ribbon()
private val BloomDefaults = ShapePrimitive.Bloom()

/**
 * The spec as Kotlin source in the style of [MorpheusGraphPresets], ready to paste into a preset list. Lists
 * only the layer fields that differ from the defaults.
 *
 * The pasted source names `Color`, `Offset`, `GradientStop`, `EdgeRule` and `ShapePrimitive` unqualified,
 * so the file it lands in needs their imports. A [MorpheusGraphFill.Custom] is code of the caller's own and
 * exports as `null`, which on a layer means it inherits the spec's or the surface's fill instead.
 */
public fun MorpheusGraphSpec.toKotlinSource(): String {
    val defaults = MorpheusGraphLayerSpec().sourceArguments()
    val layerSources = layers.joinToString(separator = ",\n") { layer ->
        val arguments = layer.sourceArguments().filter { (name, value) -> defaults[name] != value }
        if (arguments.isEmpty()) {
            "        MorpheusGraphLayerSpec()"
        } else {
            arguments.entries.joinToString(
                separator = ",\n",
                prefix = "        MorpheusGraphLayerSpec(\n",
                postfix = ",\n        )",
            ) { (name, value) -> "            $name = $value" }
        }
    }
    val fillLine = fill?.let { "\n    fill = ${it.toSource()}," }.orEmpty()
    return """
        |MorpheusGraphSpec(
        |    seed = $seed,
        |    layers = listOf(
        |$layerSources,
        |    ),$fillLine
        |)
    """.trimMargin()
}

private fun MorpheusGraphLayerSpec.sourceArguments(): Map<String, String> = linkedMapOf(
    "shapes" to shapes.joinToString(separator = ", ", prefix = "listOf(", postfix = ")") { it.toSource() },
    "edgeRule" to "EdgeRule.$edgeRule",
    "density" to density.toSource(),
    "fillChance" to fillChance.toSource(),
    "jitter" to jitter.toSource(),
    "driftAmplitude" to driftAmplitude.toSource(),
    "driftSpeed" to driftSpeed.toSource(),
    "envelopeFloor" to envelopeFloor.toSource(),
    "waveAmplitude" to waveAmplitude.toSource(),
    "waveSpeed" to waveSpeed.toSource(),
    "maxEdgeFactor" to maxEdgeFactor.toSource(),
    "minAngleDegrees" to minAngleDegrees.toSource(),
    "edgeKeepChance" to edgeKeepChance.toSource(),
    "repairReach" to repairReach.toSource(),
    "strictTriangles" to strictTriangles.toString(),
    "strokeWidthDp" to strokeWidthDp.toSource(),
    "edgeAlpha" to edgeAlpha.toSource(),
    "lengthFalloff" to lengthFalloff.toSource(),
    "pulseDepth" to pulseDepth.toSource(),
    "nodeSizeDp" to nodeSizeDp.toSource(),
    "nodeSizeVariance" to nodeSizeVariance.toSource(),
    "hotNodeShare" to hotNodeShare.toSource(),
    "glowRadiusDp" to glowRadiusDp.toSource(),
    "glowStrength" to glowStrength.toSource(),
    "depthDimming" to depthDimming.toSource(),
    "cometStrength" to cometStrength.toSource(),
    "cometSpark" to cometSpark.toSource(),
    "alpha" to alpha.toSource(),
    "fill" to (fill?.toSource() ?: "null"),
)

/** The fill as a constructor call; a custom fill is code of the caller's own, so it exports as a comment. */
private fun MorpheusGraphFill.toSource(): String = when (this) {
    is MorpheusGraphFill.Solid -> "MorpheusGraphFill.Solid(${color.toSource()})"
    is MorpheusGraphFill.Linear -> "MorpheusGraphFill.Linear(${stops.toSource()}, angleDegrees = ${angleDegrees.toSource()})"
    is MorpheusGraphFill.Radial ->
        "MorpheusGraphFill.Radial(${stops.toSource()}, center = Offset(${center.x.toSource()}, ${center.y.toSource()}), radius = ${radius.toSource()})"
    is MorpheusGraphFill.Custom -> "null /* a custom fill */"
}

private fun List<GradientStop>.toSource(): String =
    joinToString(separator = ", ", prefix = "listOf(", postfix = ")") { stop -> "GradientStop(${stop.position.toSource()}, ${stop.color.toSource()})" }

private fun Color.toSource(): String = "Color(0x${toArgbHex()})"

/** The primitive as a constructor call, listing only the arguments that differ from its defaults. */
private fun ShapePrimitive.toSource(): String {
    val arguments = when (this) {
        is ShapePrimitive.Uniform -> listOf("weight" to (weight to UniformDefaults.weight))
        is ShapePrimitive.Ribbon -> listOf(
            "weight" to (weight to RibbonDefaults.weight),
            "center" to (center to RibbonDefaults.center),
            "width" to (width to RibbonDefaults.width),
            "curve" to (curve to RibbonDefaults.curve),
            "tilt" to (tilt to RibbonDefaults.tilt),
        )
        is ShapePrimitive.Bloom -> listOf(
            "weight" to (weight to BloomDefaults.weight),
            "x" to (x to BloomDefaults.x),
            "y" to (y to BloomDefaults.y),
            "reach" to (reach to BloomDefaults.reach),
            "plateau" to (plateau to BloomDefaults.plateau),
        )
    }
    val changed = arguments.filter { (_, values) -> values.first != values.second }
    return changed.joinToString(separator = ", ", prefix = "ShapePrimitive.${this::class.simpleName}(", postfix = ")") { (name, values) ->
        "$name = ${values.first.toSource()}"
    }
}

/** A Kotlin float literal rounded to three decimals, trailing zeros trimmed: `0.5f`, `2.0f`. */
internal fun Float.toSource(): String = toFixed(SOURCE_DECIMALS).trimEnd('0').let { trimmed ->
    if (trimmed.endsWith('.')) "${trimmed}0f" else "${trimmed}f"
}

/** The color's ARGB as eight uppercase hex digits, alpha first: `FF2A2E8F`. */
internal fun Color.toArgbHex(): String = toArgb().toUInt().toString(HEX_RADIX).uppercase().padStart(ARGB_HEX_DIGITS, '0')

private const val SOURCE_DECIMALS = 3
private const val HEX_RADIX = 16
private const val ARGB_HEX_DIGITS = 8
