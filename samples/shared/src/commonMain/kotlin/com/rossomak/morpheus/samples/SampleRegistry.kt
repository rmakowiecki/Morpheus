package com.rossomak.morpheus.samples

import com.rossomak.morpheus.samples.catalog.animatedBrushSample
import com.rossomak.morpheus.samples.catalog.cometsSample
import com.rossomak.morpheus.samples.catalog.compoundShapesSample
import com.rossomak.morpheus.samples.catalog.depthTintSample
import com.rossomak.morpheus.samples.catalog.edgeRulesSample
import com.rossomak.morpheus.samples.catalog.fillMorphSample
import com.rossomak.morpheus.samples.catalog.fillsSample
import com.rossomak.morpheus.samples.catalog.imageBrushSample
import com.rossomak.morpheus.samples.catalog.layersSample
import com.rossomak.morpheus.samples.catalog.morphFamiliesSample
import com.rossomak.morpheus.samples.catalog.orbitingSweepSample
import com.rossomak.morpheus.samples.catalog.presetTourSample
import com.rossomak.morpheus.samples.catalog.radialFillSample
import com.rossomak.morpheus.samples.catalog.ringAndArcsSample
import com.rossomak.morpheus.samples.catalog.shapePrimitivesSample
import com.rossomak.morpheus.samples.catalog.waveAndPulseSample
import com.rossomak.morpheus.samples.showcase.cometPlaygroundSample
import com.rossomak.morpheus.samples.showcase.heroSample

/** Every sample, in order: the one list the gallery and the capture tool both read. New samples go at the end. */
object SampleRegistry {
    val samples: List<Sample> = listOf(
        heroSample,
        cometPlaygroundSample,
        presetTourSample,
        layersSample,
        edgeRulesSample,
        shapePrimitivesSample,
        waveAndPulseSample,
        ringAndArcsSample,
        compoundShapesSample,
        fillsSample,
        radialFillSample,
        animatedBrushSample,
        orbitingSweepSample,
        depthTintSample,
        imageBrushSample,
        morphFamiliesSample,
        cometsSample,
        fillMorphSample,
    )

    init {
        val duplicates = samples.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Sample ids $duplicates are registered more than once" }
    }
}
