package com.rossomak.morpheus.samples.gallery

/** The detail pane's theme control. */
internal enum class ThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    System("System"),
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }
}
