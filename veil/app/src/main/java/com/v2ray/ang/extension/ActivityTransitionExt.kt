package com.v2ray.ang.extension

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityOptionsCompat

fun Activity.startActivityWithMaterialTransition(intent: Intent) {
    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
    startActivity(intent, options.toBundle())
}

fun Activity.startActivityForResultWithMaterialTransition(intent: Intent, requestCode: Int) {
    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this)
    @Suppress("DEPRECATION")
    startActivityForResult(intent, requestCode, options.toBundle())
}

fun Activity.finishWithMaterialTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAfterTransition()
    } else {
        finish()
    }
}

fun ActivityResultLauncher<Intent>.launchWithMaterialTransition(activity: Activity, intent: Intent) {
    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity)
    launch(intent, options)
}
