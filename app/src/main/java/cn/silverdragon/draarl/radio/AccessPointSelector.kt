package cn.silverdragon.draarl.radio

import android.os.Build
import androidx.annotation.RequiresApi
import cn.silverdragon.draarl.data.AccessPoint
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

data class AccessPointProbe(val accessPoint: AccessPoint, val latencyMs: Int?) {
    val reachable: Boolean get() = latencyMs != null
}

data class AccessPointSelection(val selected: AccessPoint, val probes: List<AccessPointProbe>, val measured: Boolean)

object AccessPointSelector {
    suspend fun select(
        points: List<AccessPoint>,
        probePoint: (AccessPoint) -> AccessPointProbe = ::probe
    ): AccessPointSelection {
        require(points.isNotEmpty())
        val permits = Semaphore(minOf(points.size, MAX_PARALLEL_PROBES))
        val probes = coroutineScope {
            points.map { point ->
                async {
                    permits.withPermit {
                        measure(point, probePoint)
                    }
                }
            }.awaitAll()
        }
        val reachable = probes.filter(AccessPointProbe::reachable)
        val selectedProbe = reachable.minWithOrNull(
            compareBy<AccessPointProbe> { it.latencyMs ?: Int.MAX_VALUE }
                .thenBy { it.accessPoint.priority }
        ) ?: probes.minWith(compareBy { it.accessPoint.priority })
        return AccessPointSelection(selectedProbe.accessPoint, probes, reachable.isNotEmpty())
    }

    private suspend fun measure(point: AccessPoint, probePoint: (AccessPoint) -> AccessPointProbe): AccessPointProbe {
        val result = runCatching {
            withTimeoutOrNull(PROBE_BUDGET_MS) {
                runInterruptible { probePoint(point) }
            }
        }
        result.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
        }
        return result.getOrNull() ?: AccessPointProbe(point, null)
    }

    private fun probe(point: AccessPoint): AccessPointProbe {
        val samples = buildList {
            repeat(PROBE_ATTEMPTS) {
                ping(point.host)?.let(::add)
            }
        }
        return AccessPointProbe(point, samples.minOrNull())
    }

    private fun ping(host: String): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pingProcess(host)?.let { return it }
        }
        return runCatching {
            val address = InetAddress.getByName(host)
            val startedAt = System.nanoTime()
            if (!address.isReachable(PING_TIMEOUT_MS)) return@runCatching null
            ((System.nanoTime() - startedAt) / 1_000_000L).toInt().coerceAtLeast(1)
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pingProcess(host: String): Int? = runCatching {
        val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", host)
            .redirectErrorStream(true)
            .start()
        try {
            if (!process.waitFor(PING_TIMEOUT_MS.toLong() + 500L, TimeUnit.MILLISECONDS)) {
                return@runCatching null
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            PING_TIME_REGEX.find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()?.coerceAtLeast(1)
        } finally {
            if (process.isAlive) process.destroy()
        }
    }.getOrNull()

    private val PING_TIME_REGEX = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE)
    private const val PROBE_ATTEMPTS = 2
    private const val MAX_PARALLEL_PROBES = 4
    private const val PING_TIMEOUT_MS = 1_000
    private const val PROBE_BUDGET_MS = 4_000L
}
