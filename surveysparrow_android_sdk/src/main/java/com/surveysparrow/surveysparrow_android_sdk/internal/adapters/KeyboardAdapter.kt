package com.surveysparrow.surveysparrow_android_sdk.internal.adapters

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

internal class KeyboardAdapter {
    private var activity: Activity? = null
    private var savedSoftInputMode: Int? = null

    // Returns true when a different Activity was set (e.g. recreated on rotation).
    fun setActivity(activity: Activity?): Boolean {
        val changed = this.activity != null && activity != null && this.activity !== activity
        this.activity = activity
        if (savedSoftInputMode == null) {
            savedSoftInputMode = activity?.window?.attributes?.softInputMode
        }
        return changed
    }

    fun pauseDefaultKeyboardBehavior() {
        Handler(Looper.getMainLooper()).post {
            activity?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
    }

    fun resumeDefaultKeyboardBehavior() {
        Handler(Looper.getMainLooper()).post {
            savedSoftInputMode?.let { mode ->
                activity?.window?.setSoftInputMode(mode)
            }
        }
    }
}
