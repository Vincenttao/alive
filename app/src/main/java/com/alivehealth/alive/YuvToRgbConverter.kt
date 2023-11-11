package com.alivehealth.alive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import java.nio.ByteBuffer

/**
 * 其目的是将摄像头捕获的YUV格式的图像转换为RGB格式的 `Bitmap`。 *
 * 类定义
 * - `class YuvToRgbConverter(context: Context)`：这个类接受一个 `Context` 参数，通常用于访问应用的资源和设备的特定功能。
 * ### 成员变量
 * - `private val rs = RenderScript.create(context)`：使用传入的 `Context` 来创建一个 `RenderScript` 实例。
 * `RenderScript` 是一个高效的计算框架，用于处理大量数据。
 * - `private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))`：
 * 创建一个内置的 `RenderScript` 脚本，用于将YUV格式转换为RGB格式。

 * - `@Synchronized fun yuvToRgb(image: Image, output: Bitmap)`：这是类的主要方法，
 * 用于将 `Image` 格式的YUV图像转换为 `Bitmap` 格式的RGB图像。

 *
 * ### `imageToByteBuffer` 方法
 * - `private fun imageToByteBuffer(image: Image, outputBuffer: ByteBuffer)`：
 * 这个辅助方法将 `Image` 对象中的YUV数据转换为 `ByteBuffer`。
 * 它处理不同的平面（Y、U、V），并考虑不同的行跨度和像素跨度来正确地读取数据。

 * 这个 `YuvToRgbConverter` 类是一个典型的图像处理工具，用于Android设备上相机捕获的YUV图像数据的处理。
 * 它使用Android的 `RenderScript` 框架来提高图像转换的性能，特别适合处理大量数据或需要高性能的场景。
 * 此类的实现考虑了不同YUV格式的复杂性，并将其转换为更通用的RGB格式的 `Bitmap`，便于在Android应用中进一步处理或展示。
 */
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteBuffer
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {

        // Ensure that the intermediate output byte buffer is allocated
        if (!::yuvBuffer.isInitialized) {
            pixelCount = image.cropRect.width() * image.cropRect.height()
            yuvBuffer = ByteBuffer.allocateDirect(
                pixelCount * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8)
        }

        // Get the YUV data in byte array form
        imageToByteBuffer(image, yuvBuffer)

        // Ensure that the RenderScript inputs and outputs are allocated
        if (!::inputAllocation.isInitialized) {
            inputAllocation = Allocation.createSized(rs, Element.U8(rs), yuvBuffer.array().size)
        }
        if (!::outputAllocation.isInitialized) {
            outputAllocation = Allocation.createFromBitmap(rs, output)
        }

        // Convert YUV to RGB
        inputAllocation.copyFrom(yuvBuffer.array())
        scriptYuvToRgb.setInput(inputAllocation)
        scriptYuvToRgb.forEach(outputAllocation)
        outputAllocation.copyTo(output)
    }

    private fun imageToByteBuffer(image: Image, outputBuffer: ByteBuffer) {
        assert(image.format == ImageFormat.YUV_420_888)

        val imageCrop = image.cropRect
        val imagePlanes = image.planes
        val rowData = ByteArray(imagePlanes.first().rowStride)

        imagePlanes.forEachIndexed { planeIndex, plane ->

            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            val outputStride: Int

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    U V U V U V U V
            // Y Y Y Y Y Y Y Y    U V U V U V U V
            // Y Y Y Y Y Y Y Y    U V U V U V U V
            // Y Y Y Y Y Y Y Y    U V U V U V U V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            var outputOffset: Int

            when (planeIndex) {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }
                1 -> {
                    outputStride = 2
                    outputOffset = pixelCount + 1
                }
                2 -> {
                    outputStride = 2
                    outputOffset = pixelCount
                }
                else -> {
                    // Image contains more than 3 planes, something strange is going on
                    return@forEachIndexed
                }
            }

            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            // We have to divide the width and height by two if it's not the Y plane
            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                Rect(
                    imageCrop.left / 2,
                    imageCrop.top / 2,
                    imageCrop.right / 2,
                    imageCrop.bottom / 2
                )
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            buffer.position(rowStride * planeCrop.top + pixelStride * planeCrop.left)
            for (row in 0 until planeHeight) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    length = planeWidth
                    buffer.get(outputBuffer.array(), outputOffset, length)
                    outputOffset += length
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    length = (planeWidth - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until planeWidth) {
                        outputBuffer.array()[outputOffset] = rowData[col * pixelStride]
                        outputOffset += outputStride
                    }
                }

                if (row < planeHeight - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
    }
}