package com.sb.attendance.face

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure comparison logic for face templates. Deliberately free of Android dependencies
 * so it can be unit tested on the JVM.
 */
object FaceMatcher {

    /**
     * Minimum cosine similarity for two embeddings to be accepted as the same person.
     *
     * Measured against this exact model and preprocessing (see tools/validate_embeddings.py):
     * two photos of the same person taken years apart, with different pose, expression and
     * lighting, scored 0.74; two different people scored 0.02 and -0.00.
     *
     * 0.60 therefore sits far above the different-person cluster (~30x it) while leaving
     * headroom below the hardest same-person pair measured. In the app's actual flow the
     * enrolment shots and the attendance selfie are minutes apart in the same lighting, so
     * genuine matches score higher than that 0.74 worst case. Raise it to be stricter.
     */
    const val MATCH_THRESHOLD = 0.60f

    /** Both vectors are expected to be L2-normalised, so this is a plain dot product. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding size mismatch: ${a.size} vs ${b.size}" }
        var dot = 0.0
        for (i in a.indices) dot += (a[i] * b[i]).toDouble()
        return dot.toFloat().coerceIn(-1f, 1f)
    }

    fun isMatch(similarity: Float): Boolean = similarity >= MATCH_THRESHOLD

    /**
     * Averages several enrolment samples into one template. Averaging a few shots makes the
     * template noticeably more tolerant of pose and lighting than a single capture.
     */
    fun averageTemplate(samples: List<FloatArray>): FloatArray {
        require(samples.isNotEmpty()) { "Need at least one sample" }
        val size = samples.first().size
        val acc = FloatArray(size)
        for (s in samples) {
            require(s.size == size) { "Inconsistent embedding sizes" }
            for (i in 0 until size) acc[i] += s[i]
        }
        for (i in 0 until size) acc[i] /= samples.size
        return FaceEmbedder.l2Normalise(acc)
    }

    fun toBytes(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (v in embedding) buffer.putFloat(v)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.float }
    }
}
