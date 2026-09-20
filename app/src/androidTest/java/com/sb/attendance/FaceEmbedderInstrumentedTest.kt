package com.sb.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sb.attendance.face.FaceEmbedder
import com.sb.attendance.face.FaceMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

/**
 * Runs the real TFLite model on a device against real faces.
 *
 * This covers the one failure the JVM unit tests structurally cannot see: if the
 * preprocessing convention in [FaceEmbedder] did not match what the model expects, it would
 * still return 512 finite, plausible-looking floats — but collapsed, so that every face
 * matched every other one and the app happily recorded attendance for the wrong person.
 * [FaceMatcherTest] would pass throughout, because its arithmetic is correct either way.
 *
 * Fixtures are three 160x160 face crops: two of the same person (years apart, different pose,
 * expression and lighting) and one of a different person.
 */
@RunWith(AndroidJUnit4::class)
class FaceEmbedderInstrumentedTest {

    private lateinit var embedder: FaceEmbedder

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun setUp() {
        // The app's assets (the model) come from the target context; the fixtures from the
        // test APK's own assets.
        embedder = FaceEmbedder(context)
    }

    @After
    fun tearDown() {
        embedder.close()
    }

    private fun face(name: String): Bitmap =
        testContext.assets.open(name).use { BitmapFactory.decodeStream(it) }
            ?: error("Could not decode fixture $name")

    @Test
    fun modelLoadsWithExpectedShape() {
        assertEquals(160, embedder.inputSize)
        assertEquals(512, embedder.embeddingSize)
    }

    @Test
    fun embeddingIsUnitLengthAndFinite() {
        val v = embedder.embed(face("face_a1.png"))

        assertEquals(512, v.size)
        for (x in v) assertTrue("embedding contains a non-finite value", x.isFinite())

        var sum = 0.0
        for (x in v) sum += x * x
        assertEquals("embedding should be L2-normalised", 1.0f, sqrt(sum).toFloat(), 1e-3f)
    }

    /** The core property: same person scores high, different people score low. */
    @Test
    fun samePersonScoresFarAboveDifferentPeople() {
        val a1 = embedder.embed(face("face_a1.png"))
        val a2 = embedder.embed(face("face_a2.png"))
        val b1 = embedder.embed(face("face_b1.png"))

        val same = FaceMatcher.cosineSimilarity(a1, a2)
        val diff1 = FaceMatcher.cosineSimilarity(a1, b1)
        val diff2 = FaceMatcher.cosineSimilarity(a2, b1)

        // Guards against a collapsed embedding space, where everything matches everything.
        assertTrue(
            "different people scored $diff1 / $diff2 — embeddings look collapsed",
            diff1 < 0.4f && diff2 < 0.4f
        )
        assertTrue("same person only scored $same", same > 0.65f)
        assertTrue(
            "separation too small: same=$same diff=${maxOf(diff1, diff2)}",
            same - maxOf(diff1, diff2) > 0.3f
        )
    }

    /** The threshold must actually sit between the two populations, not just near them. */
    @Test
    fun thresholdSeparatesTheTwoPopulations() {
        val a1 = embedder.embed(face("face_a1.png"))
        val a2 = embedder.embed(face("face_a2.png"))
        val b1 = embedder.embed(face("face_b1.png"))

        assertTrue(FaceMatcher.isMatch(FaceMatcher.cosineSimilarity(a1, a2)))
        assertTrue(!FaceMatcher.isMatch(FaceMatcher.cosineSimilarity(a1, b1)))
        assertTrue(!FaceMatcher.isMatch(FaceMatcher.cosineSimilarity(a2, b1)))
    }

    /** Re-embedding the same bitmap must be deterministic, or matching would be unstable. */
    @Test
    fun embeddingIsDeterministic() {
        val first = embedder.embed(face("face_a1.png"))
        val second = embedder.embed(face("face_a1.png"))
        assertEquals(1.0f, FaceMatcher.cosineSimilarity(first, second), 1e-4f)
    }
}
