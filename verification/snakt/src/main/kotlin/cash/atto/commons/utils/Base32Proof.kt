package cash.atto.commons.utils

import org.jetbrains.kotlin.formver.plugin.AlwaysVerify
import org.jetbrains.kotlin.formver.plugin.preconditions
import org.jetbrains.kotlin.formver.plugin.verify

@AlwaysVerify
fun decodedSizeAllocatesOnlyCompleteBytes(characterCount: Int) {
    preconditions {
        characterCount >= 0
        // Int.MAX_VALUE / 5, expressed as a literal for SnaKt.
        characterCount <= 429_496_729
    }

    val decodedSize = base32DecodedSize(characterCount)
    val remainingBits = characterCount * 5 - decodedSize * 8
    verify(decodedSize >= 0, remainingBits >= 0, remainingBits < 8)
}

@AlwaysVerify
fun addressPayloadDecodesTo38Bytes() {
    val decodedSize = base32DecodedSize(61)
    verify(decodedSize == 38)
}
