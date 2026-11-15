package cash.atto.commons.transport

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.TooLongLineException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdjsonHeartbeatParsingTest {
    @Test
    fun `ignores initial consecutive interleaved trailing blank and whitespace lines`() =
        runTest {
            // Given
            val channel = ByteReadChannel("\n\n \t\r\n{\"value\":\"one\"}\n\n\t \n{\"value\":\"two\"}\n\n \t\n")
            val values = mutableListOf<Record>()

            // When
            channel.readNdjson<Record> { values.add(it) }

            // Then
            assertEquals(listOf(Record("one"), Record("two")), values)
        }

    @Test
    fun `rejects malformed nonblank records after a heartbeat`() =
        runTest {
            // Given
            val channel = ByteReadChannel("\nnot-json\n")
            val values = mutableListOf<Record>()

            // When
            assertFailsWith<SerializationException> { channel.readNdjson<Record> { values.add(it) } }

            // Then
            assertEquals(emptyList(), values)
        }

    @Test
    fun `retains the line limit for oversized records and whitespace`() =
        runTest {
            // Given
            val oversizedLines =
                listOf(
                    "{\"value\":\"${"x".repeat(NDJSON_MAX_LINE_CHARS.toInt())}\"}",
                    " ".repeat(NDJSON_MAX_LINE_CHARS.toInt() + 1),
                )

            for (line in oversizedLines) {
                val channel = ByteReadChannel("\n$line\n")
                val values = mutableListOf<Record>()

                // When
                assertFailsWith<TooLongLineException> { channel.readNdjson<Record> { values.add(it) } }

                // Then
                assertEquals(emptyList(), values)
            }
        }

    @Serializable
    private data class Record(
        val value: String,
    )
}
