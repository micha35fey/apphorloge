package nl.micha.padelscore.mobile

import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team

// Korte teksten over de stand, voor de melding en het scherm.

fun MatchState.headline(): String =
    "${Team.A.label} ${pointLabel(Team.A)} - ${pointLabel(Team.B)} ${Team.B.label}"

/** Sets en huidige games, in de volgorde van het scherm (het team links eerst). */
fun MatchState.setsText(): String {
    val scores = sets.map { it.gamesA to it.gamesB } + if (winner == null) listOf(gamesA to gamesB) else emptyList()
    return scores.joinToString("  ") { (a, b) -> if (leftTeam == Team.A) "$a-$b" else "$b-$a" }
}
