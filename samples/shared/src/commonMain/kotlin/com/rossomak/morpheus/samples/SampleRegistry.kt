package com.rossomak.morpheus.samples

import com.rossomak.morpheus.samples.fills.depthTintSample
import com.rossomak.morpheus.samples.fills.fillsSample
import com.rossomak.morpheus.samples.fills.imageBrushSample
import com.rossomak.morpheus.samples.fills.radialFillSample
import com.rossomak.morpheus.samples.fills.rotatingGradientBrushSample
import com.rossomak.morpheus.samples.meshes.edgeRulesSample
import com.rossomak.morpheus.samples.meshes.layersSample
import com.rossomak.morpheus.samples.meshes.seedsSample
import com.rossomak.morpheus.samples.meshes.waveAndPulseSample
import com.rossomak.morpheus.samples.meshes.windowSample
import com.rossomak.morpheus.samples.morphs.cometsSample
import com.rossomak.morpheus.samples.morphs.fillMorphSample
import com.rossomak.morpheus.samples.morphs.followMorphSample
import com.rossomak.morpheus.samples.morphs.morphFamiliesSample
import com.rossomak.morpheus.samples.morphs.retargetMorphSample
import com.rossomak.morpheus.samples.shapes.shapeUnionSample
import com.rossomak.morpheus.samples.shapes.shapesSample
import com.rossomak.morpheus.samples.showcase.cometPlaygroundSample
import com.rossomak.morpheus.samples.showcase.facingArcsSample
import com.rossomak.morpheus.samples.showcase.heroSample
import com.rossomak.morpheus.samples.showcase.presetTourSample

/**
 * Every sample, in order: the one list the gallery and the capture tool both read. It is sorted by section, and new
 * samples go at the end of their section.
 */
object SampleRegistry {
    val samples: List<Sample> = listOf(
        heroSample,
        cometPlaygroundSample,
        facingArcsSample,
        presetTourSample,
        shapesSample,
        shapeUnionSample,
        layersSample,
        edgeRulesSample,
        seedsSample,
        windowSample,
        waveAndPulseSample,
        fillsSample,
        radialFillSample,
        depthTintSample,
        rotatingGradientBrushSample,
        imageBrushSample,
        morphFamiliesSample,
        fillMorphSample,
        followMorphSample,
        retargetMorphSample,
        cometsSample,
    )

    init {
        val duplicates = samples.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Sample ids $duplicates are registered more than once" }
        require(samples == samples.sortedBy { it.section }) { "Samples must be sorted by section" }
    }
}
