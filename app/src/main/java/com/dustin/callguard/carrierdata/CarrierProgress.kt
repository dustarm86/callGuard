package com.dustin.callguard.carrierdata

import java.io.FilterInputStream
import java.io.InputStream

/** how far one file's download has got, with the numbers the screen shows: size, percent, speed and time left. */
data class CarrierProgress(
    val source: String,
    val fileName: String,
    val fileNumber: Int,
    val fileCount: Int,
    val done: Long,
    val total: Long?,
    val elapsedMs: Long,
) {
    /** 0 to 1 when the size is known, otherwise null (the bar then just shows activity). */
    val fraction: Float?
        get() = total?.takeIf { it > 0 }?.let { (done.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }

    val finishedDownloading: Boolean get() = total != null && total > 0 && done >= total

    /** bytes per second, or null in the first moments before it means anything. */
    val bytesPerSecond: Long?
        get() = if (elapsedMs < 1_000 || done <= 0) null else (done * 1000 / elapsedMs)

    /** seconds left at the current speed; null until there's a size and enough time to estimate from. */
    val etaSeconds: Long?
        get() {
            val size = total ?: return null
            val speed = bytesPerSecond ?: return null
            if (speed <= 0 || elapsedMs < 2_000) return null
            return ((size - done).coerceAtLeast(0) + speed - 1) / speed
        }

    fun lines(): List<String> {
        val lines = mutableListOf("downloading the $fileName, file $fileNumber of $fileCount, from $source")
        val size = total
        when {
            finishedDownloading -> lines += "all ${SourceCheck.size(size)} downloaded, now building and checking the file"
            size != null && size > 0 -> {
                val percent = ((fraction ?: 0f) * 100).toInt()
                val parts = mutableListOf("${SourceCheck.size(done)} of ${SourceCheck.size(size)}", "$percent%")
                bytesPerSecond?.let { parts += "${SourceCheck.size(it)}/s" }
                etaSeconds?.let { parts += "about ${duration(it)} left" }
                lines += parts.joinToString(" · ")
            }
            else -> lines += "${SourceCheck.size(done)} so far (the size isn't known)"
        }
        return lines
    }

    companion object {
        /** 45 becomes "45 s", 125 becomes "2 min 5 s". */
        fun duration(seconds: Long): String =
            if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"
    }
}

/** counts the bytes that pass through, reporting at most every [minIntervalMs] and always when the last byte arrives. */
class CountingInputStream(
    source: InputStream,
    private val total: Long?,
    private val minIntervalMs: Long = 250,
    private val onBytes: (done: Long, total: Long?) -> Unit,
) : FilterInputStream(source) {
    private var done = 0L
    private var lastReport = 0L

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) count(1)
        return byte
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = super.read(buffer, offset, length)
        if (n > 0) count(n.toLong())
        return n
    }

    private fun count(n: Long) {
        done += n
        val now = System.currentTimeMillis()
        if (now - lastReport >= minIntervalMs || (total != null && done >= total)) {
            lastReport = now
            onBytes(done, total)
        }
    }
}
