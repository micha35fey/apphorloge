package nl.micha.padelscore.score

/** Speeltijd als "12:34", of "1:02:05" vanaf een uur. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Korte samenvatting van de instellingen, voor onder de startknop. */
fun MatchSettings.summary(): String {
    val format = when (setsToWin) {
        0 -> "Alleen games"
        1 -> "1 set"
        else -> "Best of 3"
    }
    return "${deuceRule.label} · $format"
}

/** Instellingen als tekst, om ze te bewaren of naar het horloge te sturen. */
fun MatchSettings.encode(): String = MatchState(this).encode()

fun decodeSettings(text: String): MatchSettings? = MatchState.decode(text)?.settings
