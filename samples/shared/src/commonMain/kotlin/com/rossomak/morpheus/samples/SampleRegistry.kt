package com.rossomak.morpheus.samples

import com.rossomak.morpheus.samples.catalog.minimalSample
import com.rossomak.morpheus.samples.showcase.heroSample

/** Every sample, in order: the one list the gallery and the capture tool both read. New samples go at the end. */
object SampleRegistry {
    val samples: List<Sample> = listOf(
        minimalSample,
        heroSample,
    )

    init {
        val duplicates = samples.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Sample ids $duplicates are registered more than once" }
    }
}
