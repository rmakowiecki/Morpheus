package com.rossomak.morpheus

import com.rossomak.morpheus.MorpheusGraphPresets.MorphFamily
import com.rossomak.morpheus.ShapePrimitive.Bloom
import com.rossomak.morpheus.ShapePrimitive.Ring

// The specs of the other samples with comets, copied from samples/shared's catalog/Comets.kt, catalog/RetargetMorph.kt,
// catalog/SampleMorph.kt and showcase/FacingArcs.kt (the library can't depend on the samples). Fills are left out:
// they never touch the mesh.

/** The "Comets" sample's centre disc and corner pair. */
internal val cometsCentreDisc = MorphFamily.centreDisc.withSampleComets().withCometsRelay()
internal val cometsCornerPair = MorphFamily.cornerPair.withSampleComets().withCometsRelay().mapLayers { layer ->
    layer.copy(shapes = layer.shapes.map { if (it is Bloom) it.copy(reach = it.reach * 0.7f) else it })
}

/** The "Retarget morph" sample's blooms. */
internal val retargetTopRight = retargetBloom(x = 0.85f, y = 0.2f)
internal val retargetTopLeft = retargetBloom(x = 0.15f, y = 0.2f)
internal val retargetBottomCentre = retargetBloom(x = 0.5f, y = 0.85f)

private val facingArcsFront = playgroundFront.copy(comets = playgroundFront.comets.copy(pieceSize = 11f, zone = 0.15f, tail = 8f, lead = -0.15f), morphGap = 0.4f)

/** The "Facing arcs" sample's two arcs. */
internal val facingArcsLeft = MorpheusGraphSpec(
    seed = 43,
    layers = listOf(heroGhost, facingArcsFront.copy(shapes = listOf(Ring(x = 0.22f, y = 0.5f, radius = 0.16f, thickness = 0.08f, startAngle = 45f, sweep = 270f)))),
)
internal val facingArcsRight = facingArcsLeft.copy(
    layers = listOf(heroGhost, facingArcsFront.copy(shapes = listOf(Ring(x = 0.78f, y = 0.5f, radius = 0.16f, thickness = 0.08f, startAngle = 225f, sweep = 270f)))),
)

private fun retargetBloom(x: Float, y: Float) = MorphFamily.cornerBloom.withSampleComets().mapLayers {
    it.copy(shapes = listOf(Bloom(x = x, y = y, reach = 0.14f, plateau = 0.4f)), comets = it.comets.copy(zone = 0.3f, lead = 0.5f), morphGap = 0.3f)
}

private fun MorpheusGraphSpec.forMorphSample(density: Float) = mapLayers { layer ->
    val atDensity = layer.copy(density = density)
    if (layer.shapes.none { it is Bloom }) atDensity else atDensity.copy(fillChance = 1f, shapes = layer.shapes.map { if (it is Bloom) it.copy(plateau = 1f) else it })
}

private fun MorpheusGraphSpec.withSampleComets() = forMorphSample(density = 70f).mapLayers { layer ->
    layer.copy(shapes = layer.shapes.map { if (it is Bloom) it.copy(reach = it.reach * 0.65f) else it }, comets = MorpheusGraphComets(size = 1.5f, spark = 0.8f))
}

private fun MorpheusGraphSpec.withCometsRelay() = mapLayers { it.copy(comets = it.comets.copy(zone = 0.7f), morphGap = 0f) }
