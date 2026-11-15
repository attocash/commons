package cash.atto.commons.node.stream.server

/** A complete, validated next membership. Constructing a plan must not start its streams. */
data class AttoStreamPlan(
    val members: Map<String, AttoStreamMember>,
)
