package com.stabilar.core.coords

/**
 * Calibración del mapeo coordenadas de plataforma (mm) -> fracción de la imagen
 * Tapa_nueva.PNG (1878x1712 px).
 *
 * Verificación programática de los 3 marcadores de sensores (color #CA2151,
 * ~100px de diámetro) detectados en la imagen:
 *   S1(-200,-115.5) -> (182.8, 1479.0)
 *   S2(0, 230.9)    -> (938.5, 169.9)
 *   S3(200,-115.5)  -> (1694.2, 1479.1)
 *
 * Ajuste lineal resultante (error < 0.1px):
 *   pxX = 938.5   + 3.7785 * mmX
 *   pxY = 1042.54 - 3.7793 * mmY     (+Y físico apunta hacia arriba en la imagen)
 *
 * El mapeo anterior (500x500mm -> canvas completo) fallaba en el eje vertical
 * por 56-67px (escala y offset incorrectos); el eje X estaba casi bien.
 */
object PlatformCoords {
    // Dimensiones de la imagen de referencia.
    const val IMAGE_WIDTH = 1878f
    const val IMAGE_HEIGHT = 1712f

    private const val CENTER_X_PX = 938.5f
    private const val CENTER_Y_PX = 1042.54f
    private const val PX_PER_MM_X = 3.7785f
    private const val PX_PER_MM_Y = 3.7793f

    /** Fracción horizontal (0..1) de la imagen para una coordenada X en mm. */
    fun mapXFrac(x: Double): Float = ((CENTER_X_PX + PX_PER_MM_X * x) / IMAGE_WIDTH).toFloat()

    /** Fracción vertical (0..1) de la imagen para una coordenada Y en mm (+Y hacia arriba). */
    fun mapYFrac(y: Double): Float = ((CENTER_Y_PX - PX_PER_MM_Y * y) / IMAGE_HEIGHT).toFloat()
}
