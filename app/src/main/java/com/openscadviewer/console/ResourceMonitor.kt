package com.openscadviewer.console

import kotlinx.coroutines.*
import java.io.RandomAccessFile

/**
 * Monitors memory and CPU usage during active computation sessions.
 * Emits resource usage entries to ConsoleLogger every 1 second.
 */
class ResourceMonitor(private val logger: ConsoleLogger) {
    private var monitorJob: Job? = null
    private var peakMemoryMb: Float = 0f
    private var previousCpuTime: Long = 0L
    private var previousWallTime: Long = 0L

    fun start(scope: CoroutineScope) {
        peakMemoryMb = 0f
        previousCpuTime = getProcessCpuTime()
        previousWallTime = System.nanoTime()

        monitorJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val memoryMb = getUsedMemoryMb()
                val cpuPercent = getCpuUsagePercent()

                if (memoryMb > peakMemoryMb) {
                    peakMemoryMb = memoryMb
                }

                logger.emit(
                    LogSeverity.INFO,
                    "Memory: %.1f MB | CPU: %.0f%%".format(memoryMb, cpuPercent)
                )
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        logger.emit(LogSeverity.INFO, "Peak memory usage: %.1f MB".format(peakMemoryMb))
    }

    fun getPeakMemoryMb(): Float = peakMemoryMb

    private fun getUsedMemoryMb(): Float {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return usedBytes / (1024f * 1024f)
    }

    private fun getCpuUsagePercent(): Float {
        val currentCpuTime = getProcessCpuTime()
        val currentWallTime = System.nanoTime()

        val cpuDelta = currentCpuTime - previousCpuTime
        val wallDelta = currentWallTime - previousWallTime

        previousCpuTime = currentCpuTime
        previousWallTime = currentWallTime

        return if (wallDelta > 0) {
            (cpuDelta.toFloat() / wallDelta.toFloat()) * 100f
        } else {
            0f
        }
    }

    private fun getProcessCpuTime(): Long {
        return try {
            val reader = RandomAccessFile("/proc/self/stat", "r")
            val line = reader.readLine()
            reader.close()
            val fields = line.split(" ")
            // Fields 13 (utime) and 14 (stime) in clock ticks
            val utime = fields[13].toLong()
            val stime = fields[14].toLong()
            // Convert clock ticks to nanoseconds (assuming 100 Hz tick rate)
            (utime + stime) * 10_000_000L
        } catch (e: Exception) {
            0L
        }
    }
}
