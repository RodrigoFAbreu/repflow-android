package com.repflow.app.application.settings

/**
 * How focus mode shows the extra set fields (RPE, pain, technique). Persisted
 * by [storageValue], a stable string, never by ordinal; an unknown stored
 * string reads as [DEFAULT].
 */
enum class ExtraSetFields(
    val storageValue: String,
) {
    ALWAYS_SHOWN("ALWAYS_SHOWN"),
    COLLAPSED("COLLAPSED"),
    OFF("OFF"),
    ;

    companion object {
        /** Collapsed, today's behaviour. */
        val DEFAULT = COLLAPSED

        fun fromStorage(value: String?): ExtraSetFields = entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
