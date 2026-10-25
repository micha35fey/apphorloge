package nl.micha.padelscore.mobile

import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team

// Korte teksten over de stand, voor de melding en het scherm.

fun MatchState.headline(): String =
    "${Team.A.label} ${pointLabel(Team.A)} - ${pointLabel(Team.B)} ${Team.B.label}"

fun MatchState.setsText(): String =
    (sets.map { "${it.gamesA}-${it.gamesB}" } + if (winner == null) listOf("$gamesA-$gamesB") else emptyList())
        .joinToString("  ")
