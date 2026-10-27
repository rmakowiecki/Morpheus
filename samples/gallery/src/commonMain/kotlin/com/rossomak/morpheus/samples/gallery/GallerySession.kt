package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.CycleControl
import com.rossomak.morpheus.samples.MorphEasing
import com.rossomak.morpheus.samples.MorphTiming
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleSpecs
import kotlin.coroutines.cancellation.CancellationException

/**
 * One open sample: its preparation, its tuned specs, morph easings and timing, the tuning sheet and its cycle
 * control.
 * Dropped, and so reset, when the sample is left.
 */
@Stable
internal class GallerySession(val sample: Sample) {
    private val tunedSpecs = mutableStateMapOf<String, MorpheusGraphSpec>().apply { sample.specs.forEach { put(it.name, it.default) } }
    private val tunedEasings = mutableStateMapOf<String, MorphEasing>().apply { sample.morphs.forEach { put(it.name, it.default) } }

    /** The morphs' timing as tuned; null for a sample without morphs. */
    var timing: MorphTiming? by mutableStateOf(sample.timing)

    /** What the sample's content reads; snapshot-backed, so a tuned spec, easing or timing recomposes it in place. */
    val specs: SampleSpecs get() = SampleSpecs(tunedSpecs, tunedEasings, timing)

    var preparation: Preparation by mutableStateOf(Preparation.Running)
        private set

    var isTuning by mutableStateOf(false)

    /** Pauses and scrubs the sample's cycle, such as a morph held at one end while its spec is tuned. */
    val cycle = CycleControl()

    /** The spec the sheet tunes; null only for a sample without specs. */
    var selectedSpecName: String? by mutableStateOf(sample.specs.firstOrNull()?.name)

    val selectedSpec: MorpheusGraphSpec? get() = selectedSpecName?.let { tunedSpecs[it] }

    /** The easing picked for the morph named [name]. */
    fun easingOf(name: String): MorphEasing = tunedEasings.getValue(name)

    fun tuneEasing(name: String, easing: MorphEasing) {
        tunedEasings[name] = easing
    }

    fun tune(spec: MorpheusGraphSpec) {
        selectedSpecName?.let { tunedSpecs[it] = spec }
    }

    /** Every spec as tuned, in the sample's order; snapshot-backed, so a tuning edit changes it. */
    val currentSpecs: List<MorpheusGraphSpec> get() = sample.specs.map { tunedSpecs.getValue(it.name) }

    /** True once any spec, morph easing or the timing differs from its default. */
    val isTuned: Boolean
        get() = sample.specs.any { tunedSpecs[it.name] != it.default } || sample.morphs.any { tunedEasings[it.name] != it.default } || timing != sample.timing

    /** Puts every spec, morph easing and the timing back to its default, as a capture renders it. */
    fun resetSpecs() {
        sample.specs.forEach { tunedSpecs[it.name] = it.default }
        sample.morphs.forEach { tunedEasings[it.name] = it.default }
        timing = sample.timing
    }

    /** Runs the sample's preparation step; a failure, a wasm JS error included (not an [Exception]), shows in the pane. */
    suspend fun prepare() {
        preparation = try {
            sample.prepare()
            Preparation.Done
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            Preparation.Failed(failure.message ?: failure.toString())
        }
    }
}

internal sealed interface Preparation {
    data object Running : Preparation
    data object Done : Preparation
    data class Failed(val message: String) : Preparation
}
