package com.v2ray.ang.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityAppIconBinding
import com.v2ray.ang.handler.MmkvManager

/**
 * Lets the user pick between the available launcher icons. Each option maps to an
 * <activity-alias> declared in the manifest; switching enables the chosen alias and
 * disables the others so exactly one launcher entry stays active.
 */
class AppIconActivity : BaseActivity() {
    private val binding by lazy { ActivityAppIconBinding.inflate(layoutInflater) }

    /**
     * Package the launcher aliases live under. The aliases are declared with relative
     * names in the manifest, so they resolve against the module namespace rather than
     * the applicationId (the two differ in this build). Deriving it from a class that
     * lives in the namespace root keeps the component names correct even if the
     * applicationId changes.
     */
    private val aliasPackage: String =
        AppConfig::class.java.`package`?.name ?: "com.v2ray.ang"

    /** value stored in settings -> manifest alias (relative to the alias package) */
    private enum class AppIcon(val value: String, val alias: String) {
        DEFAULT("default", ".MainActivityDefault"),
        WINK("wink", ".MainActivityWink"),
        SUMMER("summer", ".MainActivitySummer"),
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_app_icon))

        binding.cardDefault.setOnClickListener { select(AppIcon.DEFAULT) }
        binding.cardWink.setOnClickListener { select(AppIcon.WINK) }
        binding.cardSummer.setOnClickListener { select(AppIcon.SUMMER) }

        refreshSelection(currentIcon())
    }

    private fun currentIcon(): AppIcon {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_APP_ICON, AppIcon.DEFAULT.value)
        return AppIcon.entries.firstOrNull { it.value == stored } ?: AppIcon.DEFAULT
    }

    private fun select(icon: AppIcon) {
        if (icon == currentIcon()) return
        // Reflect the choice immediately; only persist it once the component swap succeeds.
        refreshSelection(icon)
        if (applyIcon(icon)) {
            MmkvManager.encodeSettings(AppConfig.PREF_APP_ICON, icon.value)
        } else {
            refreshSelection(currentIcon())
        }
    }

    /**
     * Enables the target alias first, then disables the others. Doing it in this order
     * avoids a window where no launcher component is active (which would make the app
     * disappear from the home screen). Returns true when the swap was applied.
     */
    private fun applyIcon(icon: AppIcon): Boolean {
        return try {
            val pm = packageManager
            pm.setComponentEnabledSetting(
                aliasComponent(icon),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            AppIcon.entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    aliasComponent(it),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            true
        } catch (e: Exception) {
            android.util.Log.e(AppConfig.TAG, "Failed to switch launcher icon", e)
            android.widget.Toast.makeText(this, R.string.toast_failure, android.widget.Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun aliasComponent(icon: AppIcon) = ComponentName(packageName, aliasPackage + icon.alias)

    private fun refreshSelection(selected: AppIcon) {
        setCardState(binding.cardDefault, binding.checkDefault, selected == AppIcon.DEFAULT)
        setCardState(binding.cardWink, binding.checkWink, selected == AppIcon.WINK)
        setCardState(binding.cardSummer, binding.checkSummer, selected == AppIcon.SUMMER)
    }

    private fun setCardState(card: MaterialCardView, check: ImageView, isSelected: Boolean) {
        card.isChecked = isSelected
        card.strokeWidth = if (isSelected) dp(2) else 0
        check.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
