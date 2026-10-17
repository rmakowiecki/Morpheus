package com.rossomak.morpheus.tuner

import androidx.compose.runtime.Immutable
import com.rossomak.morpheus.MorpheusGraphPresets
import com.rossomak.morpheus.MorpheusGraphSpec

/**
 * A spec with the [name] and [note] the [MorpheusGraphPlayground] shows for it. Specs carry no labels of
 * their own, so a tuning tool keeps them alongside.
 */
@Immutable
public data class MorpheusGraphPreset(
    val name: String,
    val note: String,
    val spec: MorpheusGraphSpec,
)

/** The lib's [MorpheusGraphPresets], labeled for the playground. */
public object LabeledMorpheusGraphPresets {

    public val all: List<MorpheusGraphPreset> = with(MorpheusGraphPresets) {
        listOf(
            MorpheusGraphPreset("Baseline", "Uniform field, proximity edges, depth, pulse", baseline),
            MorpheusGraphPreset("Ribbon mesh", "Wavy triangulated band, fixed topology, single layer", ribbonMesh),
            MorpheusGraphPreset("Ribbon + ghost", "Ribbon over a faint, sparser, slower far mesh", ribbonPlusGhost),
            MorpheusGraphPreset("Glow constellation", "Ribbon with a hot node subset haloed; long edges fade", glowConstellation),
            MorpheusGraphPreset("Corner bloom", "Dense at the top-end corner, thinning out; built for app bars", cornerBloom),
            MorpheusGraphPreset("Depth fog", "Uniform single mesh, per-node depth dims and shrinks far nodes", depthFog),
            MorpheusGraphPreset("Living Delaunay", "Ribbon re-triangulated every frame; flipped edges fade in and out", livingDelaunay),
        )
    }

    public val morphFamily: List<MorpheusGraphPreset> = with(MorpheusGraphPresets.MorphFamily) {
        listOf(
            MorpheusGraphPreset("Uniform field", "Even mesh over the whole surface", uniformField),
            MorpheusGraphPreset("Ribbon", "Band across the lower middle", ribbon),
            MorpheusGraphPreset("Ribbon, high and narrow", "The band moved up, thinned and tilted", highNarrowRibbon),
            MorpheusGraphPreset("Corner bloom", "Dense at the top-end corner, hot nodes glowing", cornerBloom),
            MorpheusGraphPreset("Bloom, bottom-start", "The bloom moved to the opposite corner and tightened", bottomStartBloom),
            MorpheusGraphPreset("Ribbon + bloom", "Both shapes at once", ribbonAndBloom),
            MorpheusGraphPreset("Uniform + ghost", "Even mesh over a faint, sparser back layer", uniformWithGhost),
            MorpheusGraphPreset("Centre disc", "A hard-edged disc in the middle of the surface", centreDisc),
            MorpheusGraphPreset("Corner pair", "Blooms in the top-end and bottom-start corners at once", cornerPair),
            MorpheusGraphPreset("Double ribbon", "Two thin bands, one high, one low", doubleRibbon),
        )
    }
}
