package com.repflow.app.application.settings

/**
 * The app's colour scheme preference. Persisted by [storageValue], a stable
 * string, never by ordinal; an unknown stored string reads as [DEFAULT]
 * because the theme is read at launch and must never throw.
 */
enum class ThemeMode(
    val storageValue: String,
) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK"),
    ;

    companion object {
        /** Follow the system, so nothing changes on upgrade. */
        val DEFAULT = SYSTEM

        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
