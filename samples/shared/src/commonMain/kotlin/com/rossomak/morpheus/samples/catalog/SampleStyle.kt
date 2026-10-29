package com.rossomak.morpheus.samples.catalog

import androidx.compose.ui.graphics.Color

/** Mid-luminance colors that read on both light and dark surfaces, and the period the animated fills turn in. */
internal object SampleStyle {
    val blue = Color(0xFF4C8DF6)
    val violet = Color(0xFF8E6CEF)
    val magenta = Color(0xFFD65DB1)
    val orange = Color(0xFFF08A4B)
    val amber = Color(0xFFE9A93B)
    val teal = Color(0xFF2FB8A6)

    val spectrum = listOf(blue, violet, magenta, orange)

    const val ROTATION_SECONDS = 6f
}
