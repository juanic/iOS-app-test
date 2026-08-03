package com.stabilar.core.games


import com.stabilar.core.parser.CopPoint
/**
 * Región de desplazamiento del COP que un usuario puede alcanzar de forma
 * segura (sin caerse). Los juegos generan sus objetivos dentro de estos límites
 * y el canvas hace zoom a esta zona.
 */
object GameCoords {
    const val MIN_X = -100.0
    const val MAX_X = 100.0
    const val MIN_Y = -60.0
    const val MAX_Y = 80.0

    const val WIDTH_MM = MAX_X - MIN_X
    const val HEIGHT_MM = MAX_Y - MIN_Y

    /**
     * Escala uniforme (px por mm), igual para ambos ejes. Usar escalas distintas por eje
     * (p. ej. una basada en el ancho del canvas y otra en el alto) dibuja círculos que no
     * corresponden al área real de detección: un objetivo "circular" en mm se vería como
     * una elipse, y el radio de contacto real (isotrópico, en mm) quedaría más grande que
     * el círculo dibujado en el eje con menor escala. Se toma el mínimo de ambos ejes y se
     * centra el área de juego (letterbox) para que lo dibujado coincida siempre con lo que
     * realmente se detecta.
     */
    private fun scale(widthPx: Float, heightPx: Float): Float =
        minOf(widthPx / WIDTH_MM.toFloat(), heightPx / HEIGHT_MM.toFloat())

    private fun offsetX(widthPx: Float, heightPx: Float): Float =
        (widthPx - WIDTH_MM.toFloat() * scale(widthPx, heightPx)) / 2f

    private fun offsetY(widthPx: Float, heightPx: Float): Float =
        (heightPx - HEIGHT_MM.toFloat() * scale(widthPx, heightPx)) / 2f

    fun mapX(x: Double, widthPx: Float, heightPx: Float): Float =
        offsetX(widthPx, heightPx) + ((x - MIN_X) * scale(widthPx, heightPx)).toFloat()

    fun mapY(y: Double, widthPx: Float, heightPx: Float): Float =
        heightPx - offsetY(widthPx, heightPx) - ((y - MIN_Y) * scale(widthPx, heightPx)).toFloat()

    fun mmToPx(mm: Double, widthPx: Float, heightPx: Float): Float =
        (mm * scale(widthPx, heightPx)).toFloat()
}

enum class GameType(val title: String, val short: String, val subtitle: String) {
    BUBBLE_HUNTER("Cazador de burbujas", "Burbujas", "Explotá las burbujas moviendo tu centro de presión"),
    PATH_FOLLOW("Sendero", "Sendero", "Seguí el camino manteniendo el centro de presión adentro"),
    POSTURAL_SIMON("Simón postural", "Simón", "Sostené el centro de presión en la zona indicada")
}

/**
 * Niveles de dificultad de los juegos. Los juegos escalan el tamaño de los
 * objetivos, los tiempos disponibles y el ritmo de aparición usando los
 * multiplicadores de cada nivel.
 */
enum class Difficulty(val label: String, val level: Int) {
    N1("Fácil", 1),
    N2("Normal", 2),
    N3("Difícil", 3),
    N4("Muy difícil", 4),
    N5("Experto", 5);

    /** Tamaño de objetivos (radio de burbuja / corredor / zona). >1 = más fácil. */
    val sizeScale: Double
        get() = when (this) {
            N1 -> 1.2
            N2 -> 1.0
            N3 -> 0.85
            N4 -> 0.7
            N5 -> 0.55
        }

    /** Tiempo disponible (vida de burbuja / hold / timeout). >1 = más fácil. */
    val timeScale: Float
        get() = when (this) {
            N1 -> 1.2f
            N2 -> 1.0f
            N3 -> 0.8f
            N4 -> 0.65f
            N5 -> 0.5f
        }

    /** Ritmo (intervalo de aparición). >1 = más lento (más fácil). */
    val paceScale: Float
        get() = when (this) {
            N1 -> 1.3f
            N2 -> 1.0f
            N3 -> 0.85f
            N4 -> 0.75f
            N5 -> 0.65f
        }

    companion object {
        val DEFAULT: Difficulty = N2
    }
}

enum class FinishedReason { TIME_UP, STOPPED, COMPLETED }

sealed interface GamePhase {
    data object Idle : GamePhase
    data object Running : GamePhase
    data class Finished(val score: Int, val reason: FinishedReason) : GamePhase
}

enum class TargetKind { BUBBLE, PATH_NODE, ZONE }

data class GameTarget(
    val id: Int,
    val x: Double,
    val y: Double,
    val radiusMm: Double,
    val kind: TargetKind,
    val colorIndex: Int = 0,
    val active: Boolean = false,
    val progress: Float = 0f
)

/**
 * Evento transitorio: burbuja reventada por un hit del COP en la trama actual del
 * motor. Vive solo en el `GameUiState` de esa trama (no se acumula) — la UI lo usa
 * para disparar una animación de "pop" propia, independiente del loop del juego.
 */
data class PoppedBubble(
    val id: Int,
    val x: Double,
    val y: Double,
    val radiusMm: Double,
    val points: Int
)

data class GameUiState(
    val type: GameType = GameType.BUBBLE_HUNTER,
    val phase: GamePhase = GamePhase.Idle,
    val score: Int = 0,
    val combo: Int = 0,
    val remainingSeconds: Float = 0f,
    val message: String? = null,
    val targets: List<GameTarget> = emptyList(),
    val cop: CopPoint? = null,
    val trail: List<CopPoint> = emptyList(),
    val corridorRadiusMm: Float? = null,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val stats: List<GameStat> = emptyList(),
    val poppedThisTick: List<PoppedBubble> = emptyList(),
    /** Fracción (0..1) del sendero recorrida de forma continua desde el inicio. Solo la usa Sendero. */
    val pathProgress: Float = 0f
) {
    val isRunning: Boolean get() = phase is GamePhase.Running
}

data class GameStat(val name: String, val value: String, val unit: String)

interface GameEngine {
    val type: GameType
    val state: GameUiState
    fun start(durationSeconds: Int, difficulty: Difficulty, cop: CopPoint?): GameUiState
    fun update(cop: CopPoint?, deltaSeconds: Float): GameUiState
    fun stop(): GameUiState
    fun reset(): GameUiState
}
