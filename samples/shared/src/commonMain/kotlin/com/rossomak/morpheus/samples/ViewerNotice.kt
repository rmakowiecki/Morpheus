package com.rossomak.morpheus.samples

/**
 * The notice for viewers sensitive to flashing lights, in one place. The gallery shows it at the top of its list, before any sample plays;
 * the README and the Pages fallback page quote [HEADLINE] and [RISK], and `ViewerNoticeTest` keeps them in step.
 */
object ViewerNotice {
    const val HEADLINE = "Viewer discretion is advised"

    const val RISK = "Some samples have bright, fast-moving lights and rapid changes in brightness, " +
        "which can trigger seizures in people with photosensitive epilepsy."

    /** The gallery has a Pause button on every sample. */
    const val PAUSE_HINT = "Pause stops any sample."
}
