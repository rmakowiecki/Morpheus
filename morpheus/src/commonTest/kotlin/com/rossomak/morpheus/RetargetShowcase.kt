package com.rossomak.morpheus

import com.rossomak.morpheus.MorpheusGraphPresets.MorphFamily
import com.rossomak.morpheus.ShapePrimitive.Bloom

// The "Retarget morph" sample's blooms, copied from samples/shared's catalog/RetargetMorph.kt, catalog/Comets.kt and
// catalog/SampleMorph.kt (the library can't depend on the samples). Fills are left out: they never touch the mesh.

/** The "Retarget morph" sample's blooms. */
internal val retargetTopRight = retargetBloom(x = 0.85f, y = 0.2f)
internal val retargetTopLeft = retargetBloom(x = 0.15f, y = 0.2f)
internal val retargetBottomCentre = retargetBloom(x = 0.5f, y = 0.85f)

private fun retargetBloom(x: Float, y: Float) = MorphFamily.cornerBloom.withSampleComets().mapLayers {
    it.copy(shapes = listOf(Bloom(x = x, y = y, reach = 0.14f, plateau = 0.4f)), comets = it.comets.copy(zone = 0.3f, lead = 0.5f), morphGap = 0.3f)
}

private fun MorpheusGraphSpec.forMorphSample(density: Float) = mapLayers { layer ->
    val atDensity = layer.copy(density = density)
    if (layer.shapes.none { it is Bloom }) atDensity else atDensity.copy(fillChance = 1f, shapes = layer.shapes.map { if (it is Bloom) it.copy(plateau = 0.95f) else it })
}

private fun MorpheusGraphSpec.withSampleComets() = forMorphSample(density = 70f).mapLayers { layer ->
    layer.copy(shapes = layer.shapes.map { if (it is Bloom) it.copy(reach = it.reach * 0.65f) else it }, comets = MorpheusGraphComets(size = 1.5f, spark = 0.8f))
}
