package com.sb.attendance.face

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Why a frame could not be turned into a face template. */
enum class FaceFailure { NO_FACE, MULTIPLE_FACES, TOO_SMALL }

sealed interface FaceCropResult {
    data class Success(val crop: Bitmap, val bounds: Rect) : FaceCropResult
    data class Failure(val reason: FaceFailure) : FaceCropResult
}

/**
 * ML Kit locates the face; this class turns that box into a square crop suitable for FaceNet.
 * Detection and recognition are separate steps: ML Kit does not produce embeddings.
 */
class FaceDetectorHelper : AutoCloseable {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    suspend fun detectAndCrop(bitmap: Bitmap): FaceCropResult {
        val faces = detect(bitmap)
        return when {
            faces.isEmpty() -> FaceCropResult.Failure(FaceFailure.NO_FACE)
            faces.size > 1 -> FaceCropResult.Failure(FaceFailure.MULTIPLE_FACES)
            else -> {
                val box = squareCropRect(faces.first(), bitmap.width, bitmap.height)
                if (box.width() < MIN_FACE_PX || box.height() < MIN_FACE_PX) {
                    FaceCropResult.Failure(FaceFailure.TOO_SMALL)
                } else {
                    val crop = Bitmap.createBitmap(bitmap, box.left, box.top, box.width(), box.height())
                    FaceCropResult.Success(crop, box)
                }
            }
        }
    }

    private suspend fun detect(bitmap: Bitmap): List<Face> = suspendCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * Expands ML Kit's box by [MARGIN] and squares it off, matching how FaceNet training
     * crops are framed, then clamps it back inside the source image.
     */
    private fun squareCropRect(face: Face, imgWidth: Int, imgHeight: Int): Rect {
        val b = face.boundingBox
        val side = (maxOf(b.width(), b.height()) * (1f + MARGIN)).toInt()
        val cx = b.centerX()
        val cy = b.centerY()

        var left = cx - side / 2
        var top = cy - side / 2
        var right = left + side
        var bottom = top + side

        // Shift the square back inside the frame before clamping, so we keep it square
        // whenever the image is large enough to allow it.
        if (left < 0) { right -= left; left = 0 }
        if (top < 0) { bottom -= top; top = 0 }
        if (right > imgWidth) { left -= (right - imgWidth); right = imgWidth }
        if (bottom > imgHeight) { top -= (bottom - imgHeight); bottom = imgHeight }

        return Rect(
            left.coerceAtLeast(0),
            top.coerceAtLeast(0),
            right.coerceAtMost(imgWidth),
            bottom.coerceAtMost(imgHeight)
        )
    }

    override fun close() = detector.close()

    private companion object {
        const val MARGIN = 0.25f
        const val MIN_FACE_PX = 80
    }
}
