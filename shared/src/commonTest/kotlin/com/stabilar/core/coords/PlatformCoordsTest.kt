package com.stabilar.core.coords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformCoordsTest {

    private val imageWidth = 1878f
    private val imageHeight = 1712f

    // Marcadores detectados en Tapa_nueva.PNG (1878x1712), centroides en px.
    private fun assertMapsTo(expectedPx: Pair<Float, Float>, mmX: Double, mmY: Double) {
        val fx = PlatformCoords.mapXFrac(mmX)
        val fy = PlatformCoords.mapYFrac(mmY)
        val px = fx * imageWidth
        val py = fy * imageHeight
        // tolerancia <= 2px en cada eje
        assertEquals(expectedPx.first, px, 2f)
        assertEquals(expectedPx.second, py, 2f)
    }

    @Test
    fun mapsSensor1RearLeftCorrectly() {
        assertMapsTo(182.8f to 1479.0f, -200.0, -115.5)
    }

    @Test
    fun mapsSensor2FrontCorrectly() {
        assertMapsTo(938.5f to 169.9f, 0.0, 230.9)
    }

    @Test
    fun mapsSensor3RearRightCorrectly() {
        assertMapsTo(1694.2f to 1479.1f, 200.0, -115.5)
    }

    @Test
    fun centerOfPlatformMapsNearImageCenter() {
        val fx = PlatformCoords.mapXFrac(0.0)
        val fy = PlatformCoords.mapYFrac(0.0)
        assertEquals(0.4997f, fx, 0.002f)
        assertEquals(0.6090f, fy, 0.002f)
    }

    @Test
    fun positiveYIsUpInImage() {
        // +Y físico (frontal) debe quedar arriba en la imagen (fy menor).
        assertTrue(PlatformCoords.mapYFrac(230.9) < PlatformCoords.mapYFrac(-115.5))
    }
}
