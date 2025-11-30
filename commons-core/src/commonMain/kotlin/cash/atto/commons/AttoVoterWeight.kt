package cash.atto.commons

import kotlinx.serialization.Serializable

@Serializable
data class AttoVoterWeight(
    val address: AttoAddress,
    val weight: AttoAmount,
    val lastVotedAt: AttoInstant,
)
