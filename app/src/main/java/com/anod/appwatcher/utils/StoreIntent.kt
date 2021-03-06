package com.anod.appwatcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import info.anodsplace.framework.app.addMultiWindowFlags

/**
 * @author Alex Gavrishev
 * @date 14/02/2018
 */
object StoreIntent {
    const val URL_PLAY_STORE = "market://details?id=%s"
    const val URL_WEB_PLAY_STORE = "https://play.google.com/store/apps/details?id=%s"
}

fun Intent.forPlayStore(pkg: String, context: Context): Intent {
    val url = String.format(StoreIntent.URL_PLAY_STORE, pkg)
    this.action = Intent.ACTION_VIEW
    this.data = Uri.parse(url)
    addMultiWindowFlags(context)
    return this
}

fun Intent.forMyApps(update: Boolean, context: Context): Intent {
    this.action = "com.google.android.finsky.VIEW_MY_DOWNLOADS"
    this.component = ComponentName("com.android.vending",
            "com.google.android.finsky.activities.MainActivity")
    if (update) {
        this.putExtra("trigger_update_all", true)
    }
    addMultiWindowFlags(context)
    return this
}