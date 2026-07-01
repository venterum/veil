package com.v2ray.ang.viewmodel

import androidx.lifecycle.ViewModel
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.ANG_PACKAGE
import com.v2ray.ang.util.LogUtil
import java.io.IOException

class LogcatViewModel : ViewModel() {
    private val logsetsAll: MutableList<String> = mutableListOf()
    private var filteredLogs: List<String> = emptyList()
    private var currentFilter: String = ""

    fun getAll(): List<String> = filteredLogs

    fun loadLogcat() {
        try {
            val lst = mutableListOf("logcat", "-d", "-v", "time", "-s")
            lst.add("GoLog:I")
            lst.add("${ANG_PACKAGE}:I")
            lst.add("AndroidRuntime:E")
            lst.add("System.err:E")
            val process = Runtime.getRuntime().exec(lst.toTypedArray())
            val stdout = process.inputStream.bufferedReader().use { it.readLines() }
            val stderr = process.errorStream.bufferedReader().use { it.readLines() }
            process.waitFor()

            logsetsAll.clear()
            logsetsAll.addAll(stdout.reversed())
            if (stdout.isEmpty() && stderr.isNotEmpty()) {
                logsetsAll.add("logcat error: ${stderr.joinToString("\n")}")
            }
            applyFilter()
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "Failed to get logcat", e)
            logsetsAll.clear()
            logsetsAll.add("Failed to get logcat: ${e.message}")
            applyFilter()
        } catch (e: InterruptedException) {
            LogUtil.e(AppConfig.TAG, "Logcat interrupted", e)
        }
    }

    fun clearLogcat() {
        try {
            val lst = mutableListOf<String>()
            lst.add("logcat")
            lst.add("-c")
            val process = Runtime.getRuntime().exec(lst.toTypedArray())
            process.waitFor()

            logsetsAll.clear()
            filteredLogs = emptyList()
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "Failed to clear logcat", e)
        }
    }

    fun filter(content: String?) {
        currentFilter = content?.trim() ?: ""
        applyFilter()
    }

    private fun applyFilter() {
        filteredLogs = if (currentFilter.isEmpty()) {
            logsetsAll.toList()
        } else {
            logsetsAll.filter { it.contains(currentFilter) }
        }
    }
}
