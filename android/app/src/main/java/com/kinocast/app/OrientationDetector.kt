package com.kinocast.app

import android.content.Context
import android.content.res.Configuration
import android.view.OrientationEventListener

/**
 * Telefonning 16:9 (Landscape) holatda ekanligini aniqlovchi detektor.
 * Telefon tikka (Portrait) bo'lganda kino uzatish avtomatik to'xtatiladi.
 */
class OrientationDetector(
    private val context: Context,
    private val onOrientationChanged: (isLandscape16x9: Boolean) -> Unit
) {
    private var isLandscape = false

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            // 60° - 120° (o'ngga yotqizilgan) yoki 240° - 300° (chapga yotqizilgan)
            val currentLandscape = (orientation in 60..120) || (orientation in 240..300)

            if (currentLandscape != isLandscape) {
                isLandscape = currentLandscape
                onOrientationChanged(isLandscape)
            }
        }
    }

    fun start() {
        // Boshlang'ich holatni konfiguratsiyadan tekshirish
        val currentOrientation = context.resources.configuration.orientation
        isLandscape = (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
        onOrientationChanged(isLandscape)

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }
    }

    fun stop() {
        orientationListener.disable()
    }

    fun isCurrentlyLandscape(): Boolean {
        return isLandscape
    }
}
