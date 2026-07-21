@file:JvmName("AttoVotes")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import kotlin.jvm.JvmName

fun AttoSignedVote.isValidBlocking(): Boolean = runBlocking { isValid() }
