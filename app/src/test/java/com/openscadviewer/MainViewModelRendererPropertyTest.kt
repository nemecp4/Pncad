package com.openscadviewer

import net.jqwik.api.*
import org.junit.jupiter.api.Assertions.*

// Feature: tablet-layout, Property 3: Renderer state preservation round-trip

/**
 * Property-based test for renderer state preservation round-trip.
 *
 * For any valid mesh data (float arrays where vertices/normals lengths are multiples of 3
 * and colors length is a multiple of 4) and any valid camera parameters (finite floats,
 * distance > 0), storing into MainViewModel and retrieving produces byte-for-byte identical
 * arrays and equal float values.
 *
 * Validates: Requirements 6.3, 6.4, 1.4, 7.3
 */
class MainViewModelRendererPropertyTest {

    @Property(tries = 100)
    fun rendererStateRoundTripPreservesAllData(
        @ForAll("vertices") vertices: FloatArray,
        @ForAll("normals") normals: FloatArray,
        @ForAll("colors") colors: FloatArray,
        @ForAll("cameraRotXValues") cameraRotX: Float,
        @ForAll("cameraRotYValues") cameraRotY: Float,
        @ForAll("cameraDistanceValues") cameraDistance: Float,
        @ForAll("cameraPanXValues") cameraPanX: Float,
        @ForAll("cameraPanYValues") cameraPanY: Float
    ) {
        val viewModel = MainViewModel()

        // Store renderer state
        viewModel.meshVertices = vertices.copyOf()
        viewModel.meshNormals = normals.copyOf()
        viewModel.meshColors = colors.copyOf()
        viewModel.triangleCount = vertices.size / 9 // 3 vertices * 3 components per triangle

        // Store camera state
        viewModel.cameraRotX = cameraRotX
        viewModel.cameraRotY = cameraRotY
        viewModel.cameraDistance = cameraDistance
        viewModel.cameraPanX = cameraPanX
        viewModel.cameraPanY = cameraPanY

        // Read back and assert byte-for-byte identical arrays
        assertTrue(
            vertices.contentEquals(viewModel.meshVertices),
            "meshVertices round-trip failed: stored ${vertices.size} floats but got different content"
        )
        assertTrue(
            normals.contentEquals(viewModel.meshNormals),
            "meshNormals round-trip failed: stored ${normals.size} floats but got different content"
        )
        assertTrue(
            colors.contentEquals(viewModel.meshColors),
            "meshColors round-trip failed: stored ${colors.size} floats but got different content"
        )
        assertEquals(
            vertices.size / 9,
            viewModel.triangleCount,
            "triangleCount round-trip failed"
        )

        // Assert equal camera floats
        assertEquals(cameraRotX, viewModel.cameraRotX, "cameraRotX round-trip failed")
        assertEquals(cameraRotY, viewModel.cameraRotY, "cameraRotY round-trip failed")
        assertEquals(cameraDistance, viewModel.cameraDistance, "cameraDistance round-trip failed")
        assertEquals(cameraPanX, viewModel.cameraPanX, "cameraPanX round-trip failed")
        assertEquals(cameraPanY, viewModel.cameraPanY, "cameraPanY round-trip failed")
    }

    @Provide
    fun vertices(): Arbitrary<FloatArray> {
        return Arbitraries.integers().between(1, 100).flatMap { n ->
            val length = 3 * n
            Arbitraries.floats().between(-1e6f, 1e6f)
                .filter { it.isFinite() }
                .array(FloatArray::class.java)
                .ofSize(length)
        }
    }

    @Provide
    fun normals(): Arbitrary<FloatArray> {
        return Arbitraries.integers().between(1, 100).flatMap { n ->
            val length = 3 * n
            Arbitraries.floats().between(-1.0f, 1.0f)
                .filter { it.isFinite() }
                .array(FloatArray::class.java)
                .ofSize(length)
        }
    }

    @Provide
    fun colors(): Arbitrary<FloatArray> {
        return Arbitraries.integers().between(1, 100).flatMap { m ->
            val length = 4 * m
            Arbitraries.floats().between(0.0f, 1.0f)
                .filter { it.isFinite() }
                .array(FloatArray::class.java)
                .ofSize(length)
        }
    }

    @Provide
    fun cameraRotXValues(): Arbitrary<Float> {
        return Arbitraries.floats().between(-360.0f, 360.0f).filter { it.isFinite() }
    }

    @Provide
    fun cameraRotYValues(): Arbitrary<Float> {
        return Arbitraries.floats().between(-360.0f, 360.0f).filter { it.isFinite() }
    }

    @Provide
    fun cameraDistanceValues(): Arbitrary<Float> {
        return Arbitraries.floats().between(0.01f, 1000.0f).filter { it.isFinite() }
    }

    @Provide
    fun cameraPanXValues(): Arbitrary<Float> {
        return Arbitraries.floats().between(-1000.0f, 1000.0f).filter { it.isFinite() }
    }

    @Provide
    fun cameraPanYValues(): Arbitrary<Float> {
        return Arbitraries.floats().between(-1000.0f, 1000.0f).filter { it.isFinite() }
    }
}
