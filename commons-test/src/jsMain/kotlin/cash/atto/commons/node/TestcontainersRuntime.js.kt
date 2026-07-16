package cash.atto.commons.node

internal fun configureTestcontainersRuntime() {
    js(
        """
        if (
            typeof process !== "undefined" &&
            process.env != null &&
            process.env.TESTCONTAINERS_RYUK_PRIVILEGED == null
        ) {
            process.env.TESTCONTAINERS_RYUK_PRIVILEGED = "true";
        }
        """,
    )
}
