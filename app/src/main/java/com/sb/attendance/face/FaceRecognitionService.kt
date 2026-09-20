package com.sb.attendance.face

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of turning a captured frame into a face template. */
sealed interface EmbedResult {
    data class Success(val embedding: FloatArray, val crop: Bitmap) : EmbedResult
    data class Failure(val reason: FaceFailure) : EmbedResult
}

/** Outcome of verifying a selfie against an enrolled template. */
sealed interface VerifyResult {
    data class Matched(val similarity: Float) : VerifyResult
    data class NotMatched(val similarity: Float) : VerifyResult
    data class NoFace(val reason: FaceFailure) : VerifyResult
}

/**
 * The full recognition pipeline: ML Kit finds the face, FaceNet turns it into a vector,
 * FaceMatcher compares vectors. Created once and shared (the TFLite interpreter is expensive).
 */
class FaceRecognitionService(context: Context) : AutoCloseable {

    private val detector = FaceDetectorHelper()
    private val embedder = FaceEmbedder(context)

    val embeddingSize: Int get() = embedder.embeddingSize

    suspend fun embed(frame: Bitmap): EmbedResult = withContext(Dispatchers.Default) {
        when (val cropped = detector.detectAndCrop(frame)) {
            is FaceCropResult.Failure -> EmbedResult.Failure(cropped.reason)
            is FaceCropResult.Success ->
                EmbedResult.Success(embedder.embed(cropped.crop), cropped.crop)
        }
    }

    /**
     * 1:1 verification of a selfie against the staff member's enrolled template.
     * A frame with no detectable face is never a match.
     */
    suspend fun verify(frame: Bitmap, enrolled: FloatArray): VerifyResult =
        when (val result = embed(frame)) {
            is EmbedResult.Failure -> VerifyResult.NoFace(result.reason)
            is EmbedResult.Success -> {
                val similarity = FaceMatcher.cosineSimilarity(result.embedding, enrolled)
                result.crop.recycle()
                if (FaceMatcher.isMatch(similarity)) VerifyResult.Matched(similarity)
                else VerifyResult.NotMatched(similarity)
            }
        }

    override fun close() {
        detector.close()
        embedder.close()
    }
}
