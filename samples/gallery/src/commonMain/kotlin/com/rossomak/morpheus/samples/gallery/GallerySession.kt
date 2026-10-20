package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rossomak.morpheus.MorpheusGraphSpec
import com.rossomak.morpheus.samples.CycleControl
import com.rossomak.morpheus.samples.Sample
import com.rossomak.morpheus.samples.SampleSpecs
import kotlin.coroutines.cancellation.CancellationException

/** One open sample: its preparation, its tuned specs, the tuning sheet and its cycle control. Dropped, and so reset, when the sample is left. */
@Stable
internal class GallerySession(val sample: Sample) {
    private val tunedSpecs = mutableStateMapOf<String, MorpheusGraphSpec>().apply { sample.specs.forEach { put(it.name, it.default) } }

    /** What the sample's content reads; snapshot-backed, so a tuned spec recomposes it in place. */
    val specs = SampleSpecs(tunedSpecs)

    var preparation: Preparation by mutableStateOf(Preparation.Running)
        private set

    var isTuning by mutableStateOf(false)

    /** Pauses and scrubs the sample's cycle, such as a morph held at one end while its spec is tuned. */
    val cycle = CycleControl()

    var selectedSpecName: String? by mutableStateOf(sample.specs.firstOrNull()?.name)

    val selectedSpec: MorpheusGraphSpec? get() = selectedSpecName?.let { tunedSpecs[it] }

    fun tune(spec: MorpheusGraphSpec) {
        selectedSpecName?.let { tunedSpecs[it] = spec }
    }

    /** Every spec as tuned, in the sample's order; snapshot-backed, so a tuning edit changes it. */
    val currentSpecs: List<MorpheusGraphSpec> get() = sample.specs.map { tunedSpecs.getValue(it.name) }

    /** True once any spec differs from its default. */
    val isTuned: Boolean get() = sample.specs.any { tunedSpecs[it.name] != it.default }

    /** Puts every spec back to its default, as a capture renders it. */
    fun resetSpecs() {
        sample.specs.forEach { tunedSpecs[it.name] = it.default }
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
