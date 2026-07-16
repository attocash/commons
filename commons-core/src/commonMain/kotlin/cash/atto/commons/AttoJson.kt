package cash.atto.commons

import kotlinx.serialization.json.Json

internal val attoJson =
    Json {
        ignoreUnknownKeys = true
    }
