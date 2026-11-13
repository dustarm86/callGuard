package com.dustin.callguard.diagnostics

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * collects the system's own record of what happened to calls (the telecom dump and call-related
 * log lines) into one text file. it needs two permissions that android only lets you grant over adb,
 * one time. without them it still captures what it can and says what was missing.
 */
object DiagnosticsCapture {
    const val PERMISSION_DUMP = "android.permission.DUMP"
    const val PERMISSION_READ_LOGS = "android.permission.READ_LOGS"

    private const val MAX_DUMP_CHARS = 3_000_000
    private const val MAX_LOG_CHARS = 6_000_000
    private const val MAX_LOG_LINES = 4_000
    private val logKeywords = listOf(
        "callguard", "telecom", "callscreening", "freezer", "blockednumber", "voicemail",
    )

    fun hasPermission(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun grantCommands(packageName: String): List<String> =
        listOf(PERMISSION_DUMP, PERMISSION_READ_LOGS).map { "adb shell pm grant $packageName $it" }

    /** what android is doing to this app in the background, which can delay or block the screener. */
    fun backgroundLines(context: Context): List<String> {
        val lines = mutableListOf<String>()
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            lines += "background restricted by user: ${if (manager.isBackgroundRestricted) "yes" else "no"}"
        }
        runCatching {
            val power = context.getSystemService(PowerManager::class.java)
            val unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
            lines += "battery optimization: ${if (unrestricted) "unrestricted" else "optimized"}"
        }
        runCatching {
            val usage = context.getSystemService(UsageStatsManager::class.java)
            lines += "app standby bucket: ${LogFilter.standbyBucket(usage.appStandbyBucket)}"
        }
        return lines
    }

    suspend fun capture(context: Context, header: List<String>): String = withContext(Dispatchers.IO) {
        val out = StringBuilder()
        out.appendLine("callGuard diagnostics, captured ${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        header.forEach { out.appendLine(it) }
        out.appendLine("dump permission: ${if (hasPermission(context, PERMISSION_DUMP)) "granted" else "NOT granted"}")
        out.appendLine("log permission: ${if (hasPermission(context, PERMISSION_READ_LOGS)) "granted" else "NOT granted"}")
        backgroundLines(context).forEach { out.appendLine(it) }

        out.appendLine()
        out.appendLine("=== dumpsys telecom ===")
        out.appendLine(runCommand(listOf("dumpsys", "telecom"), MAX_DUMP_CHARS))

        out.appendLine()
        out.appendLine("=== power-save allowlist (lines naming callGuard) ===")
        val idle = runCommand(listOf("dumpsys", "deviceidle", "whitelist"), 200_000)
        out.appendLine(
            LogFilter.keep(idle.lines(), listOf(context.packageName), 20)
                .joinToString("\n")
                .ifBlank { "callGuard is not listed, or the command was not allowed" }
        )

        out.appendLine()
        out.appendLine("=== logcat (call-related lines) ===")
        val log = runCommand(listOf("logcat", "-d", "-b", "main,system,radio", "-v", "threadtime"), MAX_LOG_CHARS)
        out.appendLine(LogFilter.keep(log.lines(), logKeywords, MAX_LOG_LINES).joinToString("\n"))
        out.toString()
    }

    private fun runCommand(args: List<String>, maxChars: Int, timeoutMs: Long = 20_000): String =
        try {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { input ->
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (text.length < maxChars) text.append(buffer, 0, minOf(count, maxChars - text.length))
                        }
                    }
                }
            }
            reader.start()
            reader.join(timeoutMs)
            if (reader.isAlive) {
                process.destroyForcibly()
                reader.join(2_000)
                text.append("\n[stopped after ${timeoutMs / 1000}s]")
            }
            text.toString()
        } catch (e: Exception) {
            "[could not run ${args.joinToString(" ")}: ${e.message}]"
        }
}
