package com.repflow.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point and Hilt component root.
 *
 * This class is application composition/bootstrap, not an infrastructure
 * implementation, so it lives at the app's root package rather than under
 * `infrastructure/di`.
 */
@HiltAndroidApp
class RepFlowApplication : Application()
