package nl.dicomcamera.app.diagnostics

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Network-layer reachability check (ICMP ping / InetAddress fallback).
 * Not a DICOM test — use C-ECHO for Verification SCU against a PACS AE.
 */
object HostPing {
    data class Result(
        val ok: Boolean,
        val message: String,
        val rttMs: Long? = null,
    )

    private val safeHost: Pattern =
        Pattern.compile("^[A-Za-z0-9._\\-:]{1,253}$")

    fun ping(host: String, timeoutMs: Int = 5_000): Result {
        val target = host.trim()
        if (target.isEmpty()) {
            return Result(ok = false, message = "Ping failed: host is empty")
        }
        if (!safeHost.matcher(target).matches()) {
            return Result(ok = false, message = "Ping failed: invalid host")
        }

        val fromBinary = pingViaBinary(target, timeoutMs)
        if (fromBinary != null) return fromBinary

        return pingViaInetAddress(target, timeoutMs)
    }

    private fun pingViaBinary(host: String, timeoutMs: Int): Result? {
        val timeoutSec = ((timeoutMs + 999) / 1000).coerceIn(1, 30)
        return try {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", timeoutSec.toString(), host)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor((timeoutMs + 2_000).toLong(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(ok = false, message = "Ping timed out ($host)")
            }
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.exitValue()
            val rtt = parseRttMs(output)
            if (exit == 0) {
                Result(
                    ok = true,
                    message = if (rtt != null) "Ping OK — ${rtt} ms ($host)" else "Ping OK ($host)",
                    rttMs = rtt,
                )
            } else {
                Result(ok = false, message = "Ping failed ($host): no reply")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pingViaInetAddress(host: String, timeoutMs: Int): Result {
        return try {
            val started = System.nanoTime()
            val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
            val rtt = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (reachable) {
                Result(ok = true, message = "Ping OK — ${rtt} ms ($host)", rttMs = rtt)
            } else {
                Result(ok = false, message = "Ping failed ($host): unreachable")
            }
        } catch (e: Exception) {
            Result(ok = false, message = "Ping failed ($host): ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun parseRttMs(output: String): Long? {
        val timeEq = Regex("""time[=<]([\d.]+)\s*ms""", RegexOption.IGNORE_CASE).find(output)
        if (timeEq != null) {
            return timeEq.groupValues[1].toDoubleOrNull()?.toLong()
        }
        val avg = Regex("""=\s*[\d.]+/([\d.]+)/""").find(output)
        return avg?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
    }
}
