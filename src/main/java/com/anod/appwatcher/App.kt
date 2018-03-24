package com.anod.appwatcher

import android.content.Context
import com.anod.appwatcher.userLog.UserLogger
import info.anodsplace.framework.app.ApplicationContext

/**
 * @author algavris
 * *
 * @date 07/05/2016.
 */
object App {

    fun with(context: Context): AppWatcherApplication {
        return context.applicationContext as AppWatcherApplication
    }

    fun with(context: ApplicationContext): AppWatcherApplication {
        return context.actual as AppWatcherApplication
    }

    fun provide(context: Context): AppComponent {
        return (context.applicationContext as AppWatcherApplication).appComponent
    }

    fun provide(context: ApplicationContext): AppComponent {
        return (context.actual as AppWatcherApplication).appComponent
    }

    fun log(context: Context): UserLogger {
        return (context.applicationContext as AppWatcherApplication).appComponent.userLogger
    }
}
