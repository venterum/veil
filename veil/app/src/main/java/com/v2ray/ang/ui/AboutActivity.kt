package com.v2ray.ang.ui

import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.databinding.ActivityAboutBinding
import com.v2ray.ang.util.Utils

class AboutActivity : BaseActivity() {
    private val binding by lazy { ActivityAboutBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewWithToolbar(binding.root, showHomeAsUp = true, title = getString(R.string.title_about))

        binding.layoutSoureCcode.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_URL)
        }

        binding.layoutTgChannel.setOnClickListener {
            Utils.openUri(this, AppConfig.TG_CHANNEL_URL)
        }

        binding.layoutOssLicenses.setOnClickListener {
            val webView = android.webkit.WebView(this)
            webView.loadUrl("file:///android_asset/open_source_licenses.html")
            MaterialAlertDialogBuilder(this)
                .setTitle("Open source licenses")
                .setView(webView)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.layoutPrivacyPolicy.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_PRIVACY_POLICY)
        }

        binding.layoutOriginalRepo.setOnClickListener {
            Utils.openUri(this, AppConfig.APP_ORIGINAL_URL)
        }

        "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})".also {
            binding.tvVersion.text = it
        }
        BuildConfig.APPLICATION_ID.also {
            binding.tvAppId.text = it
        }
    }
}
