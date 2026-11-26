@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttoNodeMockMySqlTest {
    @Test
    fun `root uses the image's dedicated root password setting`() {
        val container = recordingMySqlContainer()
        withMySqlCredentials(container, "root", "root-fixture")
        assertEquals("root-fixture", recordedSetting(container, "rootPassword"))
        assertNull(recordedSetting(container, "username"))
    }

    @Test
    fun `a custom database user receives the node's application password`() {
        val container = recordingMySqlContainer()
        withMySqlCredentials(container, "ledger_app", "app-fixture")
        assertEquals("ledger_app", recordedSetting(container, "username"))
        assertEquals("app-fixture", recordedSetting(container, "userPassword"))
        assertNull(recordedSetting(container, "rootPassword"))
    }
}

private fun recordingMySqlContainer(): JsAny =
    js(
        """
        ({
            settings: { rootPassword: null, username: null, userPassword: null },
            withRootPassword(value) { this.settings.rootPassword = value; return this; },
            withUsername(value) { this.settings.username = value; return this; },
            withUserPassword(value) { this.settings.userPassword = value; return this; }
        })
        """,
    )

private fun recordedSetting(
    container: JsAny,
    name: String,
): String? = js("container.settings[name]")
