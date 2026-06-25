package com.openscadviewer.renderer

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.math.max
import kotlin.math.sqrt

// Feature: preview-controls-and-settings, Property 2: Axis length scales proportionally with bounding box

/**
 * Property-based tests for AxesDrawer.computeAxisLength.
 *
 * Verifies that the axis length is a monotonically increasing function
 * of the bounding box diagonal and matches the formula max(diagonal * 0.5, 1.0).
 *
 * **Validates: Requirements 3.4**
 */
class AxesDrawerPropertyTest {

    /**
     * Property 2: Axis length scales proportionally with bounding box
     *
     * For any valid bounding box (min < max in each axis), the computed axis length
     * shall equal max(diagonal * 0.5, 1.0) and shall be a monotonically increasing
     * function of the bounding box diagonal.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 2: Axis length scales proportionally with bounding box")
    fun axisLengthMatchesFormula(
        @ForAll("boundingBoxes") box: Pair<FloatArray, FloatArray>
    ) {
        val (boundingMin, boundingMax) = box

        val result = AxesDrawer.computeAxisLength(boundingMin, boundingMax)

        // Compute expected value
        val dx = boundingMax[0] - boundingMin[0]
        val dy = boundingMax[1] - boundingMin[1]
        val dz = boundingMax[2] - boundingMin[2]
        val diagonal = sqrt(dx * dx + dy * dy + dz * dz)
        val expected = max(diagonal * 0.5f, 1.0f)

        assertEquals(expected, result, 1e-5f,
            "Axis length should equal max(diagonal * 0.5, 1.0) for bounding box " +
            "min=[${boundingMin[0]}, ${boundingMin[1]}, ${boundingMin[2]}], " +
            "max=[${boundingMax[0]}, ${boundingMax[1]}, ${boundingMax[2]}]"
        )
    }

    /**
     * Monotonicity: if the bounding box diagonal increases, the axis length
     * does not decrease.
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 2: Axis length scales proportionally with bounding box")
    fun axisLengthIsMonotonicallyIncreasingWithDiagonal(
        @ForAll("boundingBoxes") box: Pair<FloatArray, FloatArray>,
        @ForAll("positiveScaleFactors") scaleFactor: Float
    ) {
        val (boundingMin, boundingMax) = box

        // Compute axis length for original box
        val originalLength = AxesDrawer.computeAxisLength(boundingMin, boundingMax)

        // Scale the bounding box to increase the diagonal
        val scaledMax = floatArrayOf(
            boundingMin[0] + (boundingMax[0] - boundingMin[0]) * scaleFactor,
            boundingMin[1] + (boundingMax[1] - boundingMin[1]) * scaleFactor,
            boundingMin[2] + (boundingMax[2] - boundingMin[2]) * scaleFactor
        )

        val scaledLength = AxesDrawer.computeAxisLength(boundingMin, scaledMax)

        if (scaleFactor > 1.0f) {
            assertTrue(scaledLength >= originalLength,
                "Axis length should not decrease when bounding box diagonal increases. " +
                "Original: $originalLength, Scaled (factor=$scaleFactor): $scaledLength"
            )
        } else {
            assertTrue(scaledLength <= originalLength,
                "Axis length should not increase when bounding box diagonal decreases. " +
                "Original: $originalLength, Scaled (factor=$scaleFactor): $scaledLength"
            )
        }
    }

    /**
     * The axis length is always at least 1.0 (the floor value).
     *
     * **Validates: Requirements 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: preview-controls-and-settings, Property 2: Axis length scales proportionally with bounding box")
    fun axisLengthIsAtLeastOne(
        @ForAll("boundingBoxes") box: Pair<FloatArray, FloatArray>
    ) {
        val (boundingMin, boundingMax) = box

        val result = AxesDrawer.computeAxisLength(boundingMin, boundingMax)

        assertTrue(result >= 1.0f,
            "Axis length should always be at least 1.0, but got $result"
        )
    }

    // --- Custom Generators ---

    @Provide
    fun boundingBoxes(): Arbitrary<Pair<FloatArray, FloatArray>> {
        val coord = Arbitraries.floats().between(-1000f, 1000f)
            .filter { it.isFinite() }
        val extent = Arbitraries.floats().between(0.01f, 500f)
            .filter { it.isFinite() }

        return Combinators.combine(coord, coord, coord, extent, extent, extent)
            .`as` { minX, minY, minZ, extX, extY, extZ ->
                val boundingMin = floatArrayOf(minX, minY, minZ)
                val boundingMax = floatArrayOf(minX + extX, minY + extY, minZ + extZ)
                Pair(boundingMin, boundingMax)
            }
    }

    @Provide
    fun positiveScaleFactors(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.1f, 10.0f)
            .filter { it.isFinite() }
    }
}
