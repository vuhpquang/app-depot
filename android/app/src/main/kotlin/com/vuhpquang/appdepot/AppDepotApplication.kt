package com.vuhpquang.appdepot

import android.app.Application
import android.util.Log
import com.vuhpquang.appdepot.android.InstallCoordinator
import com.vuhpquang.appdepot.core.EventSink

class AppDepotApplication : Application() {
    /** Process-wide: installs outlive any screen. The event sink becomes History in Task 4. */
    val coordinator: InstallCoordinator by lazy {
        InstallCoordinator(this, EventSink { Log.i("AppDepot", "event ${it.type} ${it.appSlug} ${it.message ?: ""}") })
    }
}
