package cash.atto.commons

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AttoAddressTest {
    private val expectedAccount =
        AttoAddress.parse("atto_aarfzz26z5pfwrkdcwt4jdhhe2vvixscqwehgmfjqxku43rgtjso4x6re3pc3")

    @Test
    fun `should create account`() {
        // given
        val publicKey =
            AttoPublicKey("225CE75ECF5E5B454315A7C48CE726AB545E4285887330A985D54E6E269A64EE".fromHexToByteArray())

        // when
        val account = publicKey.toAddress(AttoAlgorithm.V1)

        // then
        assertEquals(expectedAccount, account)
    }

    @Test
    fun `should extract address to public key`() {
        // when
        val publicKey = expectedAccount.publicKey

        // then
        assertEquals(expectedAccount, publicKey.toAddress(AttoAlgorithm.V1))
    }

    @Test
    fun `should throw illegal argument exception when regex doesn't match`() {
        // given
        val wrongAccount = expectedAccount.value.replace("atto_", "nano_")

        // when
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            AttoAddress.parse(wrongAccount)
        }
    }

    @Test
    fun `should throw illegal argument exception when checksum doesn't match`() {
        // given
        val wrongAccount = expectedAccount.value.substring(0, expectedAccount.value.length - 1) + "a"

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            AttoAddress.parse(wrongAccount)
        }
    }
}