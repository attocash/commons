package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoTransaction
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class AttoMockSettingsTest {
    @Test
    fun `node settings preserve defaults and explicit genesis`() =
        runTest {
            // Given
            val privateKey = AttoPrivateKey.generate()
            val genesis = AttoTransaction.createGenesis(AttoPrivateKey.generate())
            val settings = AttoNodeMockSettings(privateKey).apply { this.genesis = genesis }

            // When
            val configuration = settings.configuration()

            // Then
            assertEquals(AttoNodeMockConfiguration(genesis, privateKey), configuration)
            assertSame(genesis, configuration.genesisTransaction)
        }

    @Test
    fun `node settings apply overrides without changing earlier configurations`() =
        runTest {
            // Given
            val privateKey = AttoPrivateKey.generate()
            val genesis = AttoTransaction.createGenesis(privateKey)
            val settings = AttoNodeMockSettings(privateKey).apply { this.genesis = genesis }
            val defaults = settings.configuration()
            settings.name = "custom-node"
            settings.image = "custom-node:version"
            settings.mysqlImage = "custom-mysql:version"
            settings.dbName = "custom-db"
            settings.dbUser = "custom-user"
            settings.dbPassword = "custom-password"
            settings.pullImages = true
            settings.logOutput = true

            // When
            val configured = settings.configuration()
            settings.name = "next-node"
            settings.pullImages = false
            val next = settings.configuration()

            // Then
            assertEquals(AttoNodeMockConfiguration(genesis, privateKey), defaults)
            assertEquals(
                defaults.copy(
                    name = "custom-node",
                    image = "custom-node:version",
                    mysqlImage = "custom-mysql:version",
                    dbName = "custom-db",
                    dbUser = "custom-user",
                    dbPassword = "custom-password",
                    pullImages = true,
                    logOutput = true,
                ),
                configured,
            )
            assertEquals(configured.copy(name = "next-node", pullImages = false), next)
        }

    @Test
    fun `resetting explicit genesis creates genesis for configured private key`() =
        runTest {
            // Given
            val privateKey = AttoPrivateKey.generate()
            val explicitGenesis = AttoTransaction.createGenesis(AttoPrivateKey.generate())
            val settings = AttoNodeMockSettings(privateKey).apply { genesis = explicitGenesis }
            val explicit = settings.configuration()

            // When
            settings.genesis = null
            val generated = settings.configuration()

            // Then
            assertSame(explicitGenesis, explicit.genesisTransaction)
            assertEquals(privateKey.toPublicKey(), generated.genesisTransaction.block.publicKey)
            assertNotEquals(explicitGenesis.block.publicKey, generated.genesisTransaction.block.publicKey)
        }

    @Test
    fun `worker settings preserve defaults and apply latest overrides`() {
        // Given
        val settings = AttoWorkerMockSettings()
        val defaults = settings.configuration()
        settings.name = "custom-worker"
        settings.image = "custom-worker:version"
        settings.pullImage = true
        settings.logOutput = true

        // When
        val configured = settings.configuration()
        settings.pullImage = false
        settings.image = "next-worker:version"
        val next = settings.configuration()

        // Then
        assertEquals(AttoWorkerMockConfiguration(), defaults)
        assertEquals(
            AttoWorkerMockConfiguration(
                name = "custom-worker",
                image = "custom-worker:version",
                pullImage = true,
                logOutput = true,
            ),
            configured,
        )
        assertEquals(configured.copy(image = "next-worker:version", pullImage = false), next)
    }
}
