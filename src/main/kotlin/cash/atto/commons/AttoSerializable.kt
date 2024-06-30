package cash.atto.commons

import kotlinx.io.Buffer

interface AttoSerializable {
    fun toBuffer(): Buffer
}
