package com.rossomak.morpheus.samples.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The lifecycle owner the sample sees: the real one's, except that while [isPaused] it stays below STARTED. The
 * lib's renderer advances its clock only while STARTED, so pausing freezes drift, wave, pulse and a custom
 * brush's time without any code in the sample.
 */
@Composable
internal fun rememberPausableLifecycleOwner(isPaused: Boolean): LifecycleOwner {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { PausableLifecycleOwner(parent.lifecycle) }
    SideEffect { owner.isPaused = isPaused }
    DisposableEffect(owner) {
        owner.start()
        onDispose { owner.stop() }
    }
    return owner
}

private class PausableLifecycleOwner(private val parent: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = followed() }
    private val observer = LifecycleEventObserver { _, _ -> update() }

    var isPaused: Boolean = false
        set(value) {
            field = value
            update()
        }

    override val lifecycle: Lifecycle get() = registry

    fun start() {
        parent.addObserver(observer)
        update()
    }

    fun stop() = parent.removeObserver(observer)

    private fun update() {
        registry.currentState = followed()
    }

    private fun followed(): Lifecycle.State = if (isPaused && parent.currentState.isAtLeast(Lifecycle.State.STARTED)) Lifecycle.State.CREATED else parent.currentState
}
