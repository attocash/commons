package cash.atto.commons.worker

import cash.atto.commons.AttoHasher
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.toAtto
import cash.atto.commons.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

data class AttoWorkEvaluation(
    val environment: AttoNetwork,
    val wps: Double,
)

fun AttoWorker.evaluate(
    network: AttoNetwork,
    maxDuration: Duration,
): Flow<AttoWorkEvaluation> {
    require(maxDuration > Duration.ZERO) { "maxDuration must be greater than zero." }

    return Evaluator(
        worker = this,
        network = network,
        maxDuration = maxDuration,
    ).evaluate()
}

private val networks =
    listOf(
        AttoNetwork.LOCAL,
        AttoNetwork.DEV,
        AttoNetwork.BETA,
        AttoNetwork.LIVE,
    )

private val multipliers =
    mapOf(
        AttoNetwork.LOCAL to
            mapOf(
                AttoNetwork.LOCAL to 1.0,
                AttoNetwork.DEV to 0.0071042649,
                AttoNetwork.BETA to 0.0007203817,
                AttoNetwork.LIVE to 0.0000696977,
            ),
        AttoNetwork.DEV to
            mapOf(
                AttoNetwork.DEV to 1.0,
                AttoNetwork.BETA to 0.1014012945,
                AttoNetwork.LIVE to 0.0098106796,
            ),
        AttoNetwork.BETA to
            mapOf(
                AttoNetwork.BETA to 1.0,
                AttoNetwork.LIVE to 0.0967510293,
            ),
    )

private class Evaluator(
    private val worker: AttoWorker,
    private val network: AttoNetwork,
    private val maxDuration: Duration,
    private val targetCount: Int = 100,
) {
    init {
        require(network != AttoNetwork.UNKNOWN) { "Atto network can't be UNKNOWN." }
    }

    private val targets =
        Array(targetCount) {
            AttoWorkTarget(AttoHasher.hash(32, network.name.encodeToByteArray(), it.toULong().toByteArray()))
        }

    fun evaluate(): Flow<AttoWorkEvaluation> =
        flow {
            val evaluationStartedAt = Clock.System.now()
            val evaluationStarted = TimeSource.Monotonic.markNow()
            val networksToMeasure = networks.take(networks.indexOf(network) + 1)

            for ((networkIndex, currentNetwork) in networksToMeasure.withIndex()) {
                val multiplier = currentNetwork.multiplierTo(network)
                var sumNanos = 0L
                var counter = 0L

                fun calculateWps(): Double {
                    if (sumNanos == 0L || counter == 0L) return 0.0
                    return counter.toDouble() * NANOS_PER_SECOND / sumNanos
                }

                fun calculateProjectedWps(): Double = calculateWps() * multiplier

                suspend fun report(): Double {
                    val projectedWps = calculateProjectedWps()
                    emit(AttoWorkEvaluation(network, projectedWps))
                    return projectedWps
                }

                var targetCounter = 0
                var nextReportSecond = 1L
                var lastCooperativePause = TimeSource.Monotonic.markNow()
                val networkStarted = TimeSource.Monotonic.markNow()
                val wpsHistory = linkedMapOf<Long, Double>()

                while (evaluationStarted.elapsedNow() < maxDuration) {
                    currentCoroutineContext().ensureActive()

                    val remaining = maxDuration - evaluationStarted.elapsedNow()
                    if (remaining <= Duration.ZERO) {
                        break
                    }

                    val duration =
                        withTimeoutOrNull(remaining) {
                            measureTime {
                                worker.work(currentNetwork, evaluationStartedAt.toAtto(), targets[targetCounter++ % targetCount])
                            }
                        } ?: break

                    sumNanos += duration.inWholeNanoseconds.coerceAtLeast(1)
                    counter++

                    val elapsedSecond = networkStarted.elapsedNow().inWholeSeconds
                    if (elapsedSecond >= nextReportSecond) {
                        wpsHistory[elapsedSecond] = report()
                        nextReportSecond = elapsedSecond + 1
                        lastCooperativePause = TimeSource.Monotonic.markNow()
                        delay(COOPERATIVE_PAUSE)

                        if (wpsHistory.enoughSample()) {
                            break
                        }
                    } else if (lastCooperativePause.elapsedNow() >= COOPERATIVE_PAUSE_INTERVAL) {
                        lastCooperativePause = TimeSource.Monotonic.markNow()
                        delay(COOPERATIVE_PAUSE)
                    }
                }

                if (counter > 0L) {
                    report()
                }

                val nextNetwork = networksToMeasure.getOrNull(networkIndex + 1) ?: break

                if (networkIndex > 1) {
                    // Easier networks finish so quickly that fixed worker round-trip overhead dominates the sample:
                    // sending the target and consuming the result costs proportionally more than the work itself.
                    // Measure far enough up the difficulty curve before trusting projections for the next network.
                    val projectedNextNetworkWps = calculateWps() * currentNetwork.multiplierTo(nextNetwork)
                    if (projectedNextNetworkWps < MIN_PROJECTED_WPS_TO_MEASURE_NEXT_NETWORK) {
                        break
                    }
                }
            }
        }.flowOn(Dispatchers.Default)

    private fun AttoNetwork.multiplierTo(target: AttoNetwork): Double {
        if (this == target) {
            return 1.0
        }
        return multipliers[this]?.get(target) ?: error("No work multiplier from $this to $target.")
    }

    private fun LinkedHashMap<Long, Double>.enoughSample(): Boolean {
        val recent = this.values.toList().takeLast(5)
        if (this.size < 5) return false

        val avg = recent.average()
        if (avg == 0.0) return false

        val min = recent.min()
        val max = recent.max()

        return (max - min) / avg <= 0.10
    }

    private companion object {
        private const val MIN_PROJECTED_WPS_TO_MEASURE_NEXT_NETWORK = 5.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private val COOPERATIVE_PAUSE = 1L.milliseconds
        private val COOPERATIVE_PAUSE_INTERVAL = 16.milliseconds
    }
}
