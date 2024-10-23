package cash.atto.commons.worker

fun AttoWorker.Companion.opencl(): AttoWorker = opencl(0U)

fun AttoWorker.Companion.opencl(deviceNumber: UByte): AttoWorker = AttoWorkerOpenCL(deviceNumber)

expect class AttoWorkerOpenCL(deviceNumber: UByte) : AttoWorker
