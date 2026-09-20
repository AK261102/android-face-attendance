package com.sb.attendance

import com.sb.attendance.face.FaceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Covers the decision rule that gates attendance: two templates match only when their
 * cosine similarity clears the threshold.
 */
class FaceMatcherTest {

    private fun normalise(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val n = sqrt(sum).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    @Test
    fun `identical embeddings are a perfect match`() {
        val a = normalise(floatArrayOf(0.2f, 0.5f, 0.1f, 0.8f))
        assertEquals(1.0f, FaceMatcher.cosineSimilarity(a, a), 1e-4f)
        assertTrue(FaceMatcher.isMatch(FaceMatcher.cosineSimilarity(a, a)))
    }

    @Test
    fun `orthogonal embeddings do not match`() {
        val a = normalise(floatArrayOf(1f, 0f, 0f, 0f))
        val b = normalise(floatArrayOf(0f, 1f, 0f, 0f))
        val similarity = FaceMatcher.cosineSimilarity(a, b)
        assertEquals(0f, similarity, 1e-4f)
        assertFalse(FaceMatcher.isMatch(similarity))
    }

    @Test
    fun `similarity just under the threshold is rejected`() {
        assertFalse(FaceMatcher.isMatch(FaceMatcher.MATCH_THRESHOLD - 0.01f))
        assertTrue(FaceMatcher.isMatch(FaceMatcher.MATCH_THRESHOLD))
    }

    @Test
    fun `embedding survives a byte round trip`() {
        val original = normalise(FloatArray(512) { (it % 17).toFloat() - 8f })
        val restored = FaceMatcher.fromBytes(FaceMatcher.toBytes(original))
        assertEquals(original.size, restored.size)
        for (i in original.indices) assertEquals(original[i], restored[i], 1e-6f)
    }

    @Test
    fun `averaged template is unit length and sits between its samples`() {
        val a = normalise(floatArrayOf(1f, 0.1f, 0f, 0f))
        val b = normalise(floatArrayOf(0.9f, 0.3f, 0f, 0f))
        val template = FaceMatcher.averageTemplate(listOf(a, b))

        var norm = 0.0
        for (x in template) norm += x * x
        assertEquals(1.0f, sqrt(norm).toFloat(), 1e-4f)

        // The average is close to both contributing samples.
        assertTrue(FaceMatcher.cosineSimilarity(template, a) > 0.99f)
        assertTrue(FaceMatcher.cosineSimilarity(template, b) > 0.99f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `comparing different sized embeddings fails loudly`() {
        FaceMatcher.cosineSimilarity(FloatArray(128), FloatArray(512))
    }
}
