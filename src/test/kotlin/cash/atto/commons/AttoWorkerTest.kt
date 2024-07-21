package cash.atto.commons

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class AttoWorkerTest {
    private val hash = AttoHash("0000000000000000000000000000000000000000000000000000000000000000".fromHexToByteArray())

    @ParameterizedTest
    @MethodSource("workerProvider")
    fun `should perform work`(worker: AttoWorker) {
        val network = AttoNetwork.LOCAL
        val timestamp = AttoNetwork.INITIAL_INSTANT
        val work = worker.work(network, timestamp, hash.value)
        assertTrue(isValid(network, timestamp, hash.value, work.value))
    }

    @Test
    fun `should validate work`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        assertTrue(isValid(AttoNetwork.LIVE, AttoNetwork.INITIAL_INSTANT, hash.value, work.value))
    }

    @Test
    fun `should validate work using decreased threshold`() {
        val work = AttoWork("888f6824075cabc3".fromHexToByteArray())
        val timestamp =
            AttoNetwork
                .INITIAL_DATE
                .plus(4, DateTimeUnit.YEAR)
                .atTime(LocalTime.fromSecondOfDay(0))
                .toInstant(TimeZone.UTC)
        assertTrue(isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }

    @Test
    fun `should not validate when work is below threshold`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        val timestamp =
            AttoNetwork
                .INITIAL_DATE
                .plus(4, DateTimeUnit.YEAR)
                .atTime(LocalTime.fromSecondOfDay(0))
                .toInstant(TimeZone.UTC)
        assertFalse(isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }

    @Test
    fun `should not validate when timestamp is before initial date`() {
        val work = AttoWork("a7e077e02e3e759f".fromHexToByteArray())
        val timestamp = AttoNetwork.INITIAL_INSTANT.minus(1, DateTimeUnit.SECOND)
        assertFalse(isValid(AttoNetwork.LIVE, timestamp, hash.value, work.value))
    }

    companion object {
        @JvmStatic
        fun workerProvider(): Stream<Arguments> =
            Stream.of(
                Arguments.of(AttoWorker.cpu()),
                Arguments.of(AttoWorker.opencl()),
            )
    }
}
