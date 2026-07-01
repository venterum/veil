package com.v2ray.ang.extension

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat
import com.v2ray.ang.R

/**
 * Starts an Activity with a Material 3 Expressive forward transition.
 *
 * The animation depends on the API level:
 * - API 34+: uses [Activity.overrideActivityTransition] for predictive-back support.
 * - API 33 and below: falls back to [Activity.overridePendingTransition].
 */
fun Activity.startActivityWithMaterialTransition(intent: Intent) {
    startActivity(intent)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.m3_hold,
            android.R.anim.fade_out
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.m3_hold, android.R.anim.fade_out)
    }
}

/**
 * Starts an Activity for result with a Material 3 Expressive forward transition.
 */
fun Activity.startActivityForResultWithMaterialTransition(intent: Intent, requestCode: Int) {
    startActivityForResult(intent, requestCode)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_OPEN,
            R.anim.m3_hold,
            android.R.anim.fade_out
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.m3_hold, android.R.anim.fade_out)
    }
}

/**
 * Applies a backward (return) transition when finishing an Activity.
 */
fun Activity.finishWithMaterialTransition() {
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            android.R.anim.fade_in,
            R.anim.m3_hold
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, R.anim.m3_hold)
    }
}

/**
 * Launches an Activity through an [ActivityResultLauncher] with Material 3 Expressive
 * forward transition options.
 */
fun ActivityResultLauncher<Intent>.launchWithMaterialTransition(activity: Activity, intent: Intent) {
    val options = ActivityOptionsCompat.makeCustomAnimation(
        activity,
        R.anim.m3_hold,
        android.R.anim.fade_out
    )
    launch(intent, options)
}
