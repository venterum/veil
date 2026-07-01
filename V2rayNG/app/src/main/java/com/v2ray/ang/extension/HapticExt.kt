package com.v2ray.ang.extension

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Performs a light haptic feedback when the user interacts with a UI element.
 * Falls back to the system haptic feedback if no vibrator is available.
 */
fun View.performLightHapticFeedback() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/**
 * Performs a stronger haptic feedback for more significant actions,
 * such as toggling a primary switch or long-pressing to drag.
 */
fun View.performMediumHapticFeedback() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
    } else {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/**
 * Vibrates the device with a short, light pattern compatible with Material 3
 * Expressive motion feedback. Safe to call on any API level.
 */
fun Context.performShortVibration() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(20L)
    }
}
