package com.richarddewan.camerax.util

import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog

/*
created by Richard Dewan 04/10/2020
*/


val EXTENSION_WHITELIST = arrayOf("JPG","PNG")
const val IMMERSIVE_FLAG_TIMEOUT = 500L

/** Combination of all flags required to put activity into immersive mode */
// Enables regular immersive mode.
// For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
// Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
const val FLAGS_FULLSCREEN =
    View.SYSTEM_UI_FLAG_IMMERSIVE or
    // Set the content to appear under the system bars so that the
    // content doesn't resize when the system bars hide and show.
     View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
     View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
     View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
     // Hide the nav bar and status bar
     View.SYSTEM_UI_FLAG_FULLSCREEN or
     View.SYSTEM_UI_FLAG_HIDE_NAVIGATION




/** Milliseconds used for UI animations */
const val ANIMATION_FAST_MILLIS = 50L
const val ANIMATION_SLOW_MILLIS = 100L

/** Same as [AlertDialog.show] but setting immersive mode in the dialog's window */
fun AlertDialog.showImmersive() {
    // Set the dialog to not focusable
    window?.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

    // Make sure that the dialog's window is in full screen
    window?.decorView?.systemUiVisibility = FLAGS_FULLSCREEN

    // Show the dialog while still in immersive mode
    show()

    // Set the dialog to focusable again
    window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
}