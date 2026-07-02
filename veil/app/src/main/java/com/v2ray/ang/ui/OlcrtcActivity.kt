package com.v2ray.ang.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils

class OlcrtcActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private val carriers = arrayOf("jitsi", "wbstream", "telemost")
    private val transports = arrayOf("datachannel", "vp8channel", "seichannel", "videochannel")

    private val et_remarks: EditText by lazy { findViewById(R.id.et_remarks) }
    private val et_address: EditText by lazy { findViewById(R.id.et_address) }
    private val et_port: EditText by lazy { findViewById(R.id.et_port) }
    private val sp_carrier: MaterialAutoCompleteTextView by lazy { findViewById(R.id.sp_carrier) }
    private val sp_transport: MaterialAutoCompleteTextView by lazy { findViewById(R.id.sp_transport) }
    private val et_room_id: EditText by lazy { findViewById(R.id.et_room_id) }
    private val et_client_id: EditText by lazy { findViewById(R.id.et_client_id) }
    private val et_server_url: EditText by lazy { findViewById(R.id.et_server_url) }
    private val et_key_hex: EditText by lazy { findViewById(R.id.et_key_hex) }
    private val et_engine: EditText by lazy { findViewById(R.id.et_engine) }
    private val layout_engine: LinearLayout by lazy { findViewById(R.id.layout_engine) }
    private val layout_server_url: LinearLayout by lazy { findViewById(R.id.layout_server_url) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentViewWithToolbar(
            R.layout.activity_server_olcrtc,
            showHomeAsUp = true,
            title = EConfigType.OLCRTC.toString()
        )

        sp_carrier.setSimpleItems(carriers)
        sp_transport.setSimpleItems(transports)

        sp_carrier.setOnItemClickListener { _, _, position, _ ->
            val c = carriers[position]
            layout_server_url.visibility = if (c == "jitsi" || c == "telemost") View.VISIBLE else View.GONE
        }

        sp_transport.setOnItemClickListener { _, _, position, _ ->
            layout_engine.visibility = if (transports[position] == "datachannel") View.GONE else View.VISIBLE
        }

        if (editGuid.isNotEmpty()) {
            val config = MmkvManager.decodeServerConfig(editGuid) ?: return
            loadConfig(config)
        } else {
            et_address.setText(carriers[0])
            et_port.setText(AppConfig.PORT_OLCRTC_SOCKS)
            et_address.isEnabled = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_server, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.save_config) {
            saveConfig()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadConfig(config: ProfileItem) {
        et_remarks.setText(config.remarks)
        et_address.setText(config.server)
        et_port.setText(config.serverPort)

        sp_carrier.setText(config.olcrtcCarrier.orEmpty(), false)
        sp_transport.setText(config.olcrtcTransport.orEmpty(), false)
        et_room_id.setText(config.olcrtcRoomId)
        et_server_url.setText(config.olcrtcServerUrl)
        et_client_id.setText(config.olcrtcClientId)
        et_key_hex.setText(config.olcrtcKeyHex)
        et_engine.setText(config.olcrtcEngine)

        et_address.isEnabled = false
    }

    private fun saveConfig() {
        val config = if (editGuid.isNotEmpty()) {
            MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.OLCRTC)
        } else {
            ProfileItem.create(EConfigType.OLCRTC)
        }

        val carrierIndex = carriers.indexOf(sp_carrier.text.toString()).coerceAtLeast(0)
        val transportIndex = transports.indexOf(sp_transport.text.toString()).coerceAtLeast(0)

        config.remarks = et_remarks.text.toString().trim()
        config.server = carriers[carrierIndex]
        config.serverPort = et_port.text.toString().trim().takeIf { it.isNotBlank() } ?: AppConfig.PORT_OLCRTC_SOCKS
        config.olcrtcCarrier = carriers[carrierIndex]
        config.olcrtcTransport = transports[transportIndex]
        config.olcrtcRoomId = et_room_id.text.toString().trim()
        config.olcrtcServerUrl = et_server_url.text.toString().trim().takeIf { it.isNotBlank() }
        config.olcrtcClientId = et_client_id.text.toString().trim().takeIf { it.isNotBlank() }
        config.olcrtcKeyHex = et_key_hex.text.toString().trim()
        config.olcrtcEngine = et_engine.text.toString().trim().takeIf { it.isNotBlank() }
        config.subscriptionId = subscriptionId.orEmpty()

        val guid = MmkvManager.encodeServerConfig(editGuid, config)
        LogUtil.w(AppConfig.TAG, "OlcrtcActivity: saved config guid=$guid configType=${config.configType}")
        finish()
    }
}
