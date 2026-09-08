package com.v2ray.ang.root

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {

    data class Result(val code: Int, val output: String) {
        val success: Boolean get() = code == 0
    }

    fun runScript(context: Context, name: String, script: String): Result {
        val dir = File(context.filesDir, AppConfig.ROOT_RUNTIME_DIR).apply { mkdirs() }
        val file = File(dir, name).apply {
            writeText(script)
            setExecutable(true, false)
        }
        return exec("sh ${file.absolutePath}")
    }

    fun exec(command: String, timeoutSeconds: Long = 30): Result {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()

            // Drain stdout on a background thread so the pipe never fills up and
            // blocks the process, while the main thread enforces the timeout.
            val output = StringBuilder()
            val reader = Thread({
                try {
                    process.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(4096)
                        var n: Int
                        while (r.read(buf).also { n = it } != -1) {
                            output.append(buf, 0, n)
                        }
                    }
                } catch (_: Exception) {
                    // Stream is closed when the process is destroyed or exits.
                }
            }, "rootshell-stdout")
            reader.isDaemon = true
            reader.start()

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                LogUtil.e(AppConfig.TAG, "RootShell: timed out: $command")
                return Result(-1, output.toString())
            }
            reader.join()
            val result = Result(process.exitValue(), output.toString())
            if (!result.success) {
                LogUtil.w(AppConfig.TAG, "RootShell: '$command' exited ${result.code}: ${output.trim()}")
            }
            result
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "RootShell: failed to run '$command'", e)
            Result(-1, e.message ?: e.javaClass.simpleName)
        }
    }
}
