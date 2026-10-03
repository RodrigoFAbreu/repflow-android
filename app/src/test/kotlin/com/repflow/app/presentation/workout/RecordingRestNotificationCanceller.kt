package com.repflow.app.presentation.workout

/** A [RestNotificationCanceller] that only counts its calls, for the ViewModel tests. */
class RecordingRestNotificationCanceller : RestNotificationCanceller {
    var cancelCount = 0
        private set

    override fun cancel() {
        cancelCount++
    }
}
