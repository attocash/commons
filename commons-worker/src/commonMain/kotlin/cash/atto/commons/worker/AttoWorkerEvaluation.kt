package cash.atto.commons.worker

import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.getThreshold
import cash.atto.commons.isValid
import cash.atto.commons.utils.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration

data class AttoWorkEvaluation(
    val environment: AttoNetwork,
    val wps: Double,
)

fun AttoWorker.evaluate(
    network: AttoNetwork,
    maxDuration: Duration,
): Flow<AttoWorkEvaluation> {
    require(maxDuration > Duration.ZERO) { "maxDuration must be greater than zero." }

    return WorkEvaluator(
        worker = this,
        environment = network,
        maxDuration = maxDuration,
    ).evaluate()
}

private class WorkEvaluator(
    private val worker: AttoWorker,
    private val environment: AttoNetwork,
    private val maxDuration: Duration,
) {
    private val timestamp = AttoInstant.now()
    private val targetExpectedAttempts = AttoWork.getExpectedAttempts(environment, timestamp)
    private val ladder = environment.evaluationLadder()
    private val stageBudget = maxDuration / ladder.size
    private val started = TimeSource.Monotonic.markNow()
    private val samples = mutableListOf<WorkEvaluationSample>()
    private val latestWps = MutableStateFlow<Double?>(null)

    fun evaluate(): Flow<AttoWorkEvaluation> =
        channelFlow {
            val measurement = launch(Dispatchers.Default) { measureLadder() }
            var emittedWps: Double? = null

            suspend fun emitLatest(force: Boolean) {
                val wps = latestWps.value
                if (wps != null && (force || wps != emittedWps)) {
                    send(AttoWorkEvaluation(environment, wps))
                    emittedWps = wps
                }
            }

            var emittedIntervals = 0
            val maxIntervals = maxDuration.emissionIntervals()
            while (emittedIntervals < maxIntervals && !measurement.joinWithin(EVALUATION_EMIT_INTERVAL)) {
                emittedIntervals += 1
                emitLatest(force = true)
            }
            if (!measurement.isCompleted) {
                measurement.cancelAndJoin()
            }
            emitLatest(force = false)
        }

    private suspend fun measureLadder() {
        for (network in ladder) {
            if (!measureNetwork(network)) {
                break
            }
        }
    }

    private suspend fun measureNetwork(network: AttoNetwork): Boolean {
        val budget =
            nextBudget()
                ?: run {
                    publish()
                    return false
                }
        val model = samples.takeIf { it.isNotEmpty() }?.let(WorkEvaluationModel::fit)
        val plan =
            WorkMeasurementPlan(
                network = network,
                timestamp = timestamp,
                maxDuration = budget,
                estimatedSampleSeconds = model?.estimateSeconds(AttoWork.getExpectedAttempts(network, timestamp)),
                estimatedSampleSafetyFactor = estimatedSampleSafetyFactor(),
            )

        if (!hasEnoughTimeForSample(budget, plan.estimatedSampleSeconds, plan.estimatedSampleSafetyFactor)) {
            publish()
            return false
        }

        val stage =
            worker.measureStage(plan) { stage ->
                publish(stage.toModelSamples())
            }
        if (stage.samples.isEmpty()) {
            publish()
            return false
        }

        samples += stage.toModelSamples()
        publish()
        return true
    }

    private fun nextBudget(): Duration? {
        val remaining = maxDuration - started.elapsedNow()
        if (remaining <= Duration.ZERO) {
            return null
        }

        return minOf(stageBudget, remaining)
    }

    private fun estimatedSampleSafetyFactor(): Double =
        if (samples.distinctBy { it.network }.size < CONFIDENT_MODEL_NETWORKS) {
            FIRST_SAMPLE_SAFETY_FACTOR
        } else {
            NEXT_SAMPLE_SAFETY_FACTOR
        }

    private fun publish(stageSamples: List<WorkEvaluationSample> = emptyList()) {
        val currentSamples = samples + stageSamples
        if (currentSamples.isEmpty()) {
            return
        }

        latestWps.value = WorkEvaluationModel.fit(currentSamples).estimateWps(targetExpectedAttempts)
    }
}

private suspend fun AttoWorker.measureStage(
    plan: WorkMeasurementPlan,
    onProgress: suspend (WorkEvaluationStage) -> Unit,
): WorkEvaluationStage {
    val samples = mutableListOf<WorkEvaluationSample>()
    val started = TimeSource.Monotonic.markNow()
    var windowStarted = TimeSource.Monotonic.markNow()
    var previousWindowWps: Double? = null
    var windowSamples = 0
    val expectedAttempts = AttoWork.getExpectedAttempts(plan.network, plan.timestamp)

    while (true) {
        val remaining = plan.maxDuration - started.elapsedNow()
        if (remaining <= Duration.ZERO) {
            return WorkEvaluationStage(samples.toList(), started.elapsedNow())
        }

        val measuredSampleSeconds = samples.estimateNextSampleSeconds(started.elapsedNow())
        val nextSampleSeconds = measuredSampleSeconds ?: plan.estimatedSampleSeconds
        val safetyFactor =
            if (measuredSampleSeconds == null) {
                plan.estimatedSampleSafetyFactor
            } else {
                NEXT_SAMPLE_SAFETY_FACTOR
            }
        if (!hasEnoughTimeForSample(remaining, nextSampleSeconds, safetyFactor)) {
            return WorkEvaluationStage(samples.toList(), started.elapsedNow())
        }

        val sampleStarted = TimeSource.Monotonic.markNow()
        val completed =
            withTimeoutOrNull(remaining) {
                val target = AttoWorkTarget(SecureRandom.randomByteArray(ATTO_WORK_TARGET_SIZE.toUInt()))
                val work = work(plan.network, plan.timestamp, target)
                WorkEvaluationCompleted(target, work)
            } ?: return WorkEvaluationStage(samples.toList(), started.elapsedNow())
        val elapsed = sampleStarted.elapsedNow()

        check(AttoWork.isValid(plan.network, plan.timestamp, completed.target, completed.work.value)) {
            "Worker returned invalid work while evaluating ${plan.network}."
        }

        samples +=
            WorkEvaluationSample(
                network = plan.network,
                expectedAttempts = expectedAttempts,
                elapsed = elapsed,
            )
        onProgress(WorkEvaluationStage(samples.toList(), started.elapsedNow()))
        yield()

        windowSamples += 1
        val windowElapsed = windowStarted.elapsedNow()
        if (windowElapsed >= STABILITY_WINDOW) {
            val currentWindowWps = windowSamples.toDouble() / windowElapsed.toDouble(DurationUnit.SECONDS)
            val previousWps = previousWindowWps
            if (previousWps != null && isStable(previousWps, currentWindowWps)) {
                return WorkEvaluationStage(samples.toList(), started.elapsedNow())
            }

            previousWindowWps = currentWindowWps
            windowStarted = TimeSource.Monotonic.markNow()
            windowSamples = 0
        }
    }
}

private fun List<WorkEvaluationSample>.estimateNextSampleSeconds(elapsed: Duration): Double? {
    if (isEmpty()) {
        return null
    }

    return elapsed.toDouble(DurationUnit.SECONDS) / size
}

private fun WorkEvaluationStage.toModelSamples(): List<WorkEvaluationSample> {
    if (samples.isEmpty()) {
        return emptyList()
    }

    val sampleElapsed = elapsed / samples.size
    return samples.map { it.copy(elapsed = sampleElapsed) }
}

private fun hasEnoughTimeForSample(
    remaining: Duration,
    estimatedSeconds: Double?,
    safetyFactor: Double,
): Boolean {
    if (estimatedSeconds == null) {
        return true
    }

    return estimatedSeconds * safetyFactor <= remaining.toDouble(DurationUnit.SECONDS)
}

private suspend fun Job.joinWithin(duration: Duration): Boolean =
    withContext(Dispatchers.Default) {
        withTimeoutOrNull(duration) {
            join()
            true
        } ?: false
    }

private fun Duration.emissionIntervals(): Int {
    val intervalSeconds = EVALUATION_EMIT_INTERVAL.toDouble(DurationUnit.SECONDS)
    return ceil(toDouble(DurationUnit.SECONDS) / intervalSeconds).toInt().coerceAtLeast(1)
}

private data class WorkEvaluationCompleted(
    val target: AttoWorkTarget,
    val work: AttoWork,
)

private data class WorkMeasurementPlan(
    val network: AttoNetwork,
    val timestamp: AttoInstant,
    val maxDuration: Duration,
    val estimatedSampleSeconds: Double?,
    val estimatedSampleSafetyFactor: Double,
)

private data class WorkEvaluationStage(
    val samples: List<WorkEvaluationSample>,
    val elapsed: Duration,
)

private data class WorkEvaluationSample(
    val network: AttoNetwork,
    val expectedAttempts: Double,
    val elapsed: Duration,
)

private data class WorkEvaluationModel(
    private val fixedSeconds: Double,
    private val secondsPerAttempt: Double,
) {
    fun estimateSeconds(expectedAttempts: Double): Double =
        max(
            MINIMUM_ESTIMATED_SECONDS,
            fixedSeconds + secondsPerAttempt * expectedAttempts,
        )

    fun estimateWps(expectedAttempts: Double): Double = 1.0 / estimateSeconds(expectedAttempts)

    companion object {
        fun fit(samples: List<WorkEvaluationSample>): WorkEvaluationModel {
            if (samples.size < 2 || samples.distinctBy { it.network }.size < 2) {
                val totalSeconds = samples.sumOf { it.elapsed.toDouble(DurationUnit.SECONDS) }
                val averageSeconds = totalSeconds / samples.size
                val totalExpectedAttempts = samples.sumOf { it.expectedAttempts }
                val averageExpectedAttempts = totalExpectedAttempts / samples.size
                val fixedSeconds = averageSeconds * SINGLE_NETWORK_FIXED_SECONDS_SHARE
                return WorkEvaluationModel(
                    fixedSeconds = fixedSeconds,
                    secondsPerAttempt = (averageSeconds - fixedSeconds) / averageExpectedAttempts,
                )
            }

            val averageExpectedAttempts = samples.map { it.expectedAttempts }.average()
            val averageSeconds = samples.map { it.elapsed.toDouble(DurationUnit.SECONDS) }.average()
            val variance =
                samples.sumOf {
                    val attemptDelta = it.expectedAttempts - averageExpectedAttempts
                    attemptDelta * attemptDelta
                }
            if (variance == 0.0) {
                return WorkEvaluationModel(
                    fixedSeconds = 0.0,
                    secondsPerAttempt = averageSeconds / averageExpectedAttempts,
                )
            }

            val covariance =
                samples.sumOf {
                    (it.expectedAttempts - averageExpectedAttempts) * (it.elapsed.toDouble(DurationUnit.SECONDS) - averageSeconds)
                }
            val secondsPerAttempt = max(0.0, covariance / variance)
            val fixedSeconds = max(0.0, averageSeconds - secondsPerAttempt * averageExpectedAttempts)

            return WorkEvaluationModel(
                fixedSeconds = fixedSeconds,
                secondsPerAttempt = secondsPerAttempt,
            )
        }
    }
}

private fun AttoNetwork.evaluationLadder(): List<AttoNetwork> =
    when (this) {
        AttoNetwork.LOCAL -> listOf(AttoNetwork.LOCAL)
        AttoNetwork.DEV -> listOf(AttoNetwork.LOCAL, AttoNetwork.DEV)
        AttoNetwork.BETA -> listOf(AttoNetwork.LOCAL, AttoNetwork.DEV, AttoNetwork.BETA)
        AttoNetwork.LIVE -> listOf(AttoNetwork.LOCAL, AttoNetwork.DEV, AttoNetwork.BETA, AttoNetwork.LIVE)
        AttoNetwork.UNKNOWN -> throw IllegalArgumentException("Cannot evaluate work for UNKNOWN network.")
    }

private fun AttoWork.Companion.getExpectedAttempts(
    network: AttoNetwork,
    timestamp: AttoInstant,
): Double {
    val threshold = getThreshold(network, timestamp)
    return TWO_TO_64 / (threshold.toDouble() + 1.0)
}

private fun isStable(
    previousWps: Double,
    currentWps: Double,
): Boolean {
    val larger = max(previousWps, currentWps)
    if (larger == 0.0) {
        return true
    }

    return abs(previousWps - currentWps) / larger <= STABILITY_RELATIVE_TOLERANCE
}

private const val ATTO_WORK_TARGET_SIZE = 32
private const val CONFIDENT_MODEL_NETWORKS = 3
private val EVALUATION_EMIT_INTERVAL = 1.toDuration(DurationUnit.SECONDS)
private const val FIRST_SAMPLE_SAFETY_FACTOR = 4.0
private const val MINIMUM_ESTIMATED_SECONDS = 1.0e-12
private const val NEXT_SAMPLE_SAFETY_FACTOR = 1.25
private const val SINGLE_NETWORK_FIXED_SECONDS_SHARE = 2.0 / 3.0
private const val STABILITY_RELATIVE_TOLERANCE = 0.10
private val STABILITY_WINDOW = 2.toDuration(DurationUnit.SECONDS)
private val TWO_TO_64 = ULong.MAX_VALUE.toDouble() + 1.0
