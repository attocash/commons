package cash.atto.commons

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

fun Uint8Array.toByteArray(): ByteArray {
    val output = ByteArray(length)
    repeat(length) { index ->
        output[index] = get(index)
    }
    return output
}


fun ByteArray.toUint8Array(): Uint8Array {
    val output = Uint8Array(size)
    repeat(size) { index ->
        output[index] = get(index)
    }
    return output
}

suspend fun <T> Promise<T>.await(): T = suspendCoroutine { cont ->
    then({ cont.resume(it) }, { cont.resumeWithException(it) })
}
