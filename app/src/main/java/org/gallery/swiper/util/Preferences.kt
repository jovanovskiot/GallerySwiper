package org.gallery.swiper.util

import android.content.Context

object Preferences {
    private const val PREFS_NAME = "gallery_swiper_prefs"
    private const val KEY_ONBOARDING_SEEN = "onboarding_seen"

    fun hasSeenOnboarding(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_SEEN, false)
    }

    fun markOnboardingSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_SEEN, true)
            .apply()
    }
}
