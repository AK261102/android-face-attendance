package com.sb.attendance.face

import android.content.Context
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter
import kotlin.math.sqrt

/**
 * Wraps the FaceNet TFLite model. Turns an already-cropped face bitmap into an
 * L2-normalised embedding vector, so two faces can be compared with a dot product.
 *
 * Input/output shapes are read from the interpreter rather than hard-coded, so swapping
 * in a different FaceNet-style model (e.g. MobileFaceNet at 112x112/192-d) needs no code change.
 */
class FaceEmbedder(context: Context) : AutoCloseable {

    private val interpreter: Interpreter
    val inputSize: Int
    val embeddingSize: Int

    init {
        val model = loadModel(context, MODEL_ASSET)
        val options = Interpreter.Options().apply { numThreads = 4 }
        interpreter = Interpreter(model, options)

        // input: [1, H, W, 3]   output: [1, D]
        val inShape = interpreter.getInputTensor(0).shape()
        val outShape = interpreter.getOutputTensor(0).shape()
        inputSize = inShape[1]
        embeddingSize = outShape[outShape.size - 1]
    }

    /** @param faceBitmap a tight crop around a single face. */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val scaled = if (faceBitmap.width != inputSize || faceBitmap.height != inputSize) {
            Bitmap.createScaledBitmap(faceBitmap, inputSize, inputSize, true)
        } else {
            faceBitmap
        }

        val input = toStandardizedBuffer(scaled)
        val output = Array(1) { FloatArray(embeddingSize) }
        interpreter.run(input, output)

        if (scaled !== faceBitmap) scaled.recycle()
        return l2Normalise(output[0])
    }

    /**
     * FaceNet expects per-image standardisation: (pixel - mean) / std over the whole crop.
     * This is what makes the model robust to overall brightness differences between the
     * enrolment shot and the attendance selfie.
     */
    private fun toStandardizedBuffer(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val rgb = FloatArray(pixels.size * 3)
        var sum = 0.0
        var i = 0
        for (p in pixels) {
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            rgb[i++] = r; rgb[i++] = g; rgb[i++] = b
            sum += r + g + b
        }

        val mean = (sum / rgb.size).toFloat()
        var varSum = 0.0
        for (v in rgb) {
            val d = v - mean
            varSum += d * d
        }
        // Clamp std the same way tf.image.per_image_standardization does, to avoid div-by-zero
        // on a flat (all-one-colour) crop.
        val std = maxOf(sqrt(varSum / rgb.size).toFloat(), 1.0f / sqrt(rgb.size.toFloat()))

        val buffer = ByteBuffer.allocateDirect(rgb.size * 4).order(ByteOrder.nativeOrder())
        for (v in rgb) buffer.putFloat((v - mean) / std)
        buffer.rewind()
        return buffer
    }

    override fun close() = interpreter.close()

    private fun loadModel(context: Context, asset: String): MappedByteBuffer {
        val fd = context.assets.openFd(asset)
        FileInputStream(fd.fileDescriptor).use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    companion object {
        private const val MODEL_ASSET = "facenet_512.tflite"

        /** Scales the vector to unit length so cosine similarity reduces to a dot product. */
        fun l2Normalise(v: FloatArray): FloatArray {
            var sum = 0.0
            for (x in v) sum += x * x
            val norm = sqrt(sum).toFloat()
            if (norm <= 0f) return v
            return FloatArray(v.size) { v[it] / norm }
        }
    }
}
