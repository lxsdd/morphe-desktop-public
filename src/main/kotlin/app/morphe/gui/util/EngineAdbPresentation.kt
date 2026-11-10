package app.morphe.gui.util

import app.morphe.engine.util.AdbDevice

/** Presentation-only hint; exact ADB serial is always retained by operations. */
val AdbDevice.displayNameWithEndpoint: String
    get() = if (displayName == id) id else "$displayName · " +
        (if (id.length <= 20) id else "${id.take(10)}…${id.takeLast(6)}")
