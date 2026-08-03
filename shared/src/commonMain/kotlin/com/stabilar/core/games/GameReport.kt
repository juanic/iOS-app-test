package com.stabilar.core.games


import com.stabilar.core.parser.CopPoint
/**
 * Resultado de una partida, con la trayectoria del COP y las métricas del juego.
 * Se usa para mostrar el detalle en el overlay de resultado, para generar el
 * informe PDF de la partida y para persistirla en el historial de sesiones.
 */
data class GameReport(
    val timestamp: Long,
    val gameType: GameType,
    val gameTitle: String,
    val difficulty: Difficulty,
    val durationSeconds: Double,
    val score: Int,
    val points: List<CopPoint>,
    val gameStats: List<GameStat>,
    val stabilityStats: List<GameStat>
) {
    companion object
}
