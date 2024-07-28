package cash.atto.commons

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.writeULongLe
import org.jocl.CL
import org.jocl.CL.CL_CONTEXT_PLATFORM
import org.jocl.CL.CL_DEVICE_MAX_COMPUTE_UNITS
import org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE
import org.jocl.CL.CL_DEVICE_TYPE_ALL
import org.jocl.CL.CL_MEM_COPY_HOST_PTR
import org.jocl.CL.CL_MEM_READ_ONLY
import org.jocl.CL.CL_MEM_READ_WRITE
import org.jocl.CL.CL_MEM_WRITE_ONLY
import org.jocl.CL.CL_TRUE
import org.jocl.CL.clBuildProgram
import org.jocl.CL.clCreateBuffer
import org.jocl.CL.clCreateCommandQueueWithProperties
import org.jocl.CL.clCreateContext
import org.jocl.CL.clCreateKernel
import org.jocl.CL.clCreateProgramWithSource
import org.jocl.CL.clEnqueueNDRangeKernel
import org.jocl.CL.clEnqueueReadBuffer
import org.jocl.CL.clFinish
import org.jocl.CL.clGetDeviceIDs
import org.jocl.CL.clGetDeviceInfo
import org.jocl.CL.clGetPlatformIDs
import org.jocl.CL.clReleaseCommandQueue
import org.jocl.CL.clReleaseContext
import org.jocl.CL.clReleaseKernel
import org.jocl.CL.clReleaseMemObject
import org.jocl.CL.clReleaseProgram
import org.jocl.CL.clSetKernelArg
import org.jocl.Pointer
import org.jocl.Sizeof
import org.jocl.cl_command_queue
import org.jocl.cl_context
import org.jocl.cl_context_properties
import org.jocl.cl_device_id
import org.jocl.cl_kernel
import org.jocl.cl_platform_id
import org.jocl.cl_program
import org.jocl.cl_queue_properties
import java.io.Closeable

private val OPENCL = AttoWorkerOpenCL()

fun AttoWorker.Companion.opencl(): AttoWorker = OPENCL

fun AttoWorker.Companion.opencl(deviceNumber: UByte): AttoWorker = AttoWorkerOpenCL(deviceNumber)

class AttoWorkerOpenCL(
    deviceNumber: UByte = 0U,
) : AttoWorker,
    Closeable {
    private val kernelSource: String by lazy {
        val fileLocation = AttoWorkerOpenCL::class.java.classLoader.getResource("kernels/work.cl")!!
        fileLocation.openStream().bufferedReader().use { it.readText() }
    }

    private val context: cl_context
    private val queue: cl_command_queue
    private val program: cl_program
    private val kernel: cl_kernel
    private val globalWorkSize: LongArray

    init {
        CL.setExceptionsEnabled(true)

        val numPlatformsArray = IntArray(1)
        clGetPlatformIDs(0, null, numPlatformsArray)
        val numPlatforms = numPlatformsArray[0]

        val platforms = arrayOfNulls<cl_platform_id>(numPlatforms)
        clGetPlatformIDs(platforms.size, platforms, null)
        val platform = platforms[0]

        val contextProperties = cl_context_properties()
        contextProperties.addProperty(CL_CONTEXT_PLATFORM.toLong(), platform)

        val numDevicesArray = IntArray(1)
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, 0, null, numDevicesArray)
        val numDevices = numDevicesArray[0]

        if (deviceNumber.toInt() >= numDevices) {
            throw IllegalArgumentException("Invalid device number: $deviceNumber. Only $numDevices devices available.")
        }

        val devices = arrayOfNulls<cl_device_id>(numDevices)
        clGetDeviceIDs(platform, CL_DEVICE_TYPE_ALL, numDevices, devices, null)
        val device = devices[deviceNumber.toInt()]

        context = clCreateContext(contextProperties, 1, arrayOf(device), null, null, null)
        queue = clCreateCommandQueueWithProperties(context, device, cl_queue_properties(), null)

        program = clCreateProgramWithSource(context, 1, arrayOf(kernelSource), null, null)
        clBuildProgram(program, 0, null, null, null, null)

        kernel = clCreateKernel(program, "work", null)

        val maxWorkGroupSizeArray = LongArray(1)
        clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.cl_long.toLong(), Pointer.to(maxWorkGroupSizeArray), null)
        val maxWorkGroupSize = maxWorkGroupSizeArray[0]

        val computeUnitsArray = LongArray(1)
        clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, Sizeof.cl_long.toLong(), Pointer.to(computeUnitsArray), null)
        val computeUnits = computeUnitsArray[0]

        globalWorkSize = LongArray(1) { maxWorkGroupSize * computeUnits }
    }

    override fun work(
        threshold: ULong,
        target: ByteArray,
    ): AttoWork {
        val resultHostData = LongArray(1)
        val foundHostData = IntArray(1)
        val bufResult = clCreateBuffer(context, CL_MEM_WRITE_ONLY, Sizeof.cl_long.toLong(), null, null)
        val bufFound =
            clCreateBuffer(context, CL_MEM_READ_WRITE or CL_MEM_COPY_HOST_PTR, Sizeof.cl_int.toLong(), Pointer.to(foundHostData), null)
        val bufHash = clCreateBuffer(context, CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR, target.size.toLong(), Pointer.to(target), null)

        clSetKernelArg(kernel, 0, Sizeof.cl_mem.toLong(), Pointer.to(bufResult))
        clSetKernelArg(kernel, 1, Sizeof.cl_mem.toLong(), Pointer.to(bufHash))
        clSetKernelArg(kernel, 2, Sizeof.cl_long.toLong(), Pointer.to(longArrayOf(threshold.toLong())))
        clSetKernelArg(kernel, 3, Sizeof.cl_mem.toLong(), Pointer.to(bufFound))

        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null)
        clFinish(queue)

        clEnqueueReadBuffer(queue, bufResult, CL_TRUE, 0, Sizeof.cl_long.toLong(), Pointer.to(resultHostData), 0, null, null)

        val result =
            Buffer().let {
                it.writeULongLe(resultHostData[0].toULong())
                it.readByteArray()
            }

        clReleaseMemObject(bufResult)
        clReleaseMemObject(bufFound)
        clReleaseMemObject(bufHash)

        return AttoWork(result)
    }

    override fun close() {
        clReleaseKernel(kernel)
        clReleaseProgram(program)
        clReleaseCommandQueue(queue)
        clReleaseContext(context)
    }
}
