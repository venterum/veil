package com.v2ray.ang.viewmodel

import android.app.Application
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.SubscriptionUpdateResult
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.moveItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.MessageUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SubscriptionsViewModel(application: Application) : BaseViewModel(application) {
    private val subscriptions: MutableList<SubscriptionCache> =
        MmkvManager.decodeSubscriptions().toMutableList()

    private val _subsFlow = MutableStateFlow(subscriptions.toList())
    val subsFlow: StateFlow<List<SubscriptionCache>> = _subsFlow.asStateFlow()

    fun getAll(): List<SubscriptionCache> = subscriptions.toList()

    fun reload() {
        subscriptions.clear()
        subscriptions.addAll(MmkvManager.decodeSubscriptions())
        _subsFlow.value = subscriptions.toList()
    }

    fun remove(subId: String): Boolean {
        val changed = subscriptions.removeAll { it.guid == subId }
        if (changed) {
            SettingsManager.removeSubscriptionWithDefault(subId)
            SettingsChangeManager.makeSetupGroupTab()
        }
        _subsFlow.value = subscriptions.toList()
        return changed
    }

    fun update(subId: String, item: SubscriptionItem) {
        val idx = subscriptions.indexOfFirst { it.guid == subId }
        if (idx >= 0) {
            subscriptions[idx] = SubscriptionCache(subId, item)
            MmkvManager.encodeSubscription(subId, item)
        }
        _subsFlow.value = subscriptions.toList()
    }

    fun move(fromPosition: Int, toPosition: Int) {
        if (subscriptions.moveItem(fromPosition, toPosition)) {
            MmkvManager.encodeSubsList(subscriptions.mapTo(mutableListOf()) { it.guid })
            SettingsChangeManager.makeSetupGroupTab()
            _subsFlow.value = subscriptions.toList()
        }
    }

    fun updateSubscriptions() {
        val updateSubscription = MmkvManager.decodeSettingsBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        if (updateSubscription) {
            updateSubscriptionsOnly()
        }
    }

    fun updateSubscriptionsOnly() {
        launchLoading {
            try {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.updateConfigViaSubAll()
                }
                showUpdateResult(result)
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    /**
     * Updates a single subscription and resets its auto-update timer.
     */
    fun updateOne(subId: String) {
        val item = MmkvManager.decodeSubscription(subId) ?: return
        launchLoading {
            try {
                val result = withContext(Dispatchers.IO) {
                    AngConfigManager.updateConfigViaSub(SubscriptionCache(subId, item))
                }
                SubscriptionUpdater.syncOne(app, subId)
                showUpdateResult(result)
                reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Subscription update failed for $subId", e)
                toastError(R.string.toast_failure)
            }
        }
    }

    /**
     * Measures the real ping for every server of a single subscription.
     */
    fun pingOne(subId: String) {
        val guids = MmkvManager.decodeServerList(subId)
        if (guids.isEmpty()) {
            toastError(R.string.toast_none_data)
            return
        }
        MessageUtil.sendMsg2TestService(
            app,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL)
        )
        MmkvManager.clearAllTestDelayResults(guids)
        MessageUtil.sendMsg2TestService(
            app,
            TestServiceMessage(
                key = AppConfig.MSG_MEASURE_CONFIG_START,
                subscriptionId = subId,
                serverGuids = emptyList()
            )
        )
        toast(getString(R.string.connection_test_testing_count, guids.size))
    }

    private fun showUpdateResult(result: SubscriptionUpdateResult) {
        when {
            result.successCount + result.failureCount + result.skipCount == 0 ->
                toast(R.string.title_update_subscription_no_subscription)

            result.successCount > 0 && result.failureCount + result.skipCount == 0 ->
                toast(getString(R.string.title_update_config_count, result.configCount))

            else ->
                toast(
                    getString(
                        R.string.title_update_subscription_result,
                        result.configCount,
                        result.successCount,
                        result.failureCount,
                        result.skipCount
                    )
                )
        }
        if (result.hwidRejectedCount > 0) {
            toast(getString(R.string.title_hwid_rejected, result.hwidRejectedCount))
        }
    }
}
