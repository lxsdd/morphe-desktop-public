package app.morphe.gui.ui.theme

/** Protect Windows Skia/Compose surfaces from full-screen transition regressions. */
fun useAnimatedScreenTransitions(osName: String? = System.getProperty("os.name")): Boolean =
    osName?.contains("windows", ignoreCase = true) != true
