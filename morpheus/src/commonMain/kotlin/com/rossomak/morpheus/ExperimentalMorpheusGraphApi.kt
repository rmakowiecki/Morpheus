package com.rossomak.morpheus

/**
 * Marks diagnostics and tooling hooks: frame and layer stats, invariant overlays. They exist for
 * tuning and profiling and may change shape or disappear without notice.
 */
@RequiresOptIn(message = "Morpheus graph diagnostics may change without notice.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class ExperimentalMorpheusGraphApi
