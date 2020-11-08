package com.richarddewan.camerax.util

import android.app.Activity
import android.util.DisplayMetrics
import java.lang.ref.WeakReference


/*
created by Richard Dewan 30/10/2020
*/

class DisplayMetricsHelper (activity: Activity) {
    private val displayMetrics = DisplayMetrics()
    private val viewReference = WeakReference(activity)

    private fun DisplayMetrics.screenWith(): Float {
        viewReference.get()?.windowManager?.defaultDisplay?.getMetrics(this)
        return widthPixels.coerceAtMost(heightPixels) / density
    }

    fun isPhone(): Boolean {
        return displayMetrics.screenWith() < 600
    }

    fun is7InchTablet(): Boolean {
        return displayMetrics.screenWith() >= 600 && displayMetrics.screenWith() < 720
    }

    fun is10InchTablet(): Boolean {
        return displayMetrics.screenWith() >= 720
    }
}