package nl.micha.padelscore.score

// Pure Kotlin scorelogica, zonder Android-afhankelijkheden zodat hij los te testen is.

enum class Team {
    A, B;

    val other: Team get() = if (this == A) B else A

    val label: String get() = if (this == A) "Wij" else "Zij"
}

data class MatchSettings(
    /** Gouden punt: bij 40-40 beslist het volgende punt de game. Uit = voordeel-regel. */
    val goldenPoint: Boolean = true,
    /** Aantal sets nodig om te winnen: 1 (één set) of 2 (best of 3). */
    val setsToWin: Int = 2,
    val firstServer: Team = Team.A,
)

data class SetScore(val gamesA: Int, val gamesB: Int)

data class MatchState(
    val settings: MatchSettings = MatchSettings(),
    val sets: List<SetScore> = emptyList(),
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val pointsA: Int = 0,
    val pointsB: Int = 0,
    val tiebreak: Boolean = false,
    /** Team dat de huidige game (of de tiebreak) begint met serveren. */
    val gameServer: Team = settings.firstServer,
    val winner: Team? = null,
) {
    val setsA: Int get() = sets.count { it.gamesA > it.gamesB }
    val setsB: Int get() = sets.count { it.gamesB > it.gamesA }

    /** Wie nu serveert. In de tiebreak wisselt het na het eerste punt en daarna om de twee punten. */
    val server: Team
        get() {
            if (!tiebreak) return gameServer
            val played = pointsA + pointsB
            return if (((played + 1) / 2) % 2 == 0) gameServer else gameServer.other
        }

    val isGoldenPointMoment: Boolean
        get() = !tiebreak && settings.goldenPoint && pointsA == 3 && pointsB == 3

    /** Korte melding boven de stand: winnaar, tiebreak of gouden punt. */
    val statusText: String?
        get() = when {
            winner != null -> "${winner.label} wint!"
            tiebreak -> "Tiebreak"
            isGoldenPointMoment -> "Gouden punt"
            else -> null
        }

    fun pointLabel(team: Team): String {
        val mine = if (team == Team.A) pointsA else pointsB
        val theirs = if (team == Team.A) pointsB else pointsA
        if (tiebreak) return mine.toString()
        if (mine >= 3 && theirs >= 3) {
            return when {
                mine > theirs -> "AD"
                else -> "40"
            }
        }
        return when (mine) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            else -> "40"
        }
    }

    fun pointFor(team: Team): MatchState {
        if (winner != null) return this
        var a = pointsA
        var b = pointsB
        if (team == Team.A) a++ else b++
        val mine = if (team == Team.A) a else b
        val theirs = if (team == Team.A) b else a

        if (tiebreak) {
            if (mine >= 7 && mine - theirs >= 2) return winGame(team)
            return copy(pointsA = a, pointsB = b)
        }

        val wonGame = mine >= 4 && (mine - theirs >= 2 || settings.goldenPoint)
        if (wonGame) return winGame(team)
        // Terug van voordeel naar 40-40, zodat de getallen klein blijven.
        if (a == 4 && b == 4) {
            a = 3
            b = 3
        }
        return copy(pointsA = a, pointsB = b)
    }

    private fun winGame(team: Team): MatchState {
        val ga = gamesA + if (team == Team.A) 1 else 0
        val gb = gamesB + if (team == Team.B) 1 else 0
        // Na een tiebreak begint het team dat in de tiebreak als eerste ontving; dat is ook gewoon "de ander".
        val nextServer = gameServer.other
        val base = copy(pointsA = 0, pointsB = 0, gameServer = nextServer)

        val setWon = tiebreak || ((ga >= 6 || gb >= 6) && kotlin.math.abs(ga - gb) >= 2)
        if (setWon) {
            val newSets = sets + SetScore(ga, gb)
            val next = base.copy(sets = newSets, gamesA = 0, gamesB = 0, tiebreak = false)
            val setsWonByTeam = newSets.count { if (team == Team.A) it.gamesA > it.gamesB else it.gamesB > it.gamesA }
            return if (setsWonByTeam >= settings.setsToWin) next.copy(winner = team) else next
        }
        return base.copy(gamesA = ga, gamesB = gb, tiebreak = ga == 6 && gb == 6)
    }

    // Simpele tekstvorm om de stand op te slaan, zodat een herstart van de app niets kwijtraakt.
    fun encode(): String = listOf(
        if (settings.goldenPoint) 1 else 0,
        settings.setsToWin,
        settings.firstServer.name,
        sets.joinToString(",") { "${it.gamesA}-${it.gamesB}" },
        gamesA, gamesB, pointsA, pointsB,
        if (tiebreak) 1 else 0,
        gameServer.name,
        winner?.name ?: "",
    ).joinToString(";")

    companion object {
        fun decode(text: String): MatchState? = runCatching {
            val f = text.split(";")
            require(f.size == 11)
            val settings = MatchSettings(
                goldenPoint = f[0] == "1",
                setsToWin = f[1].toInt(),
                firstServer = Team.valueOf(f[2]),
            )
            val sets = if (f[3].isEmpty()) emptyList() else f[3].split(",").map {
                val (x, y) = it.split("-")
                SetScore(x.toInt(), y.toInt())
            }
            MatchState(
                settings = settings,
                sets = sets,
                gamesA = f[4].toInt(),
                gamesB = f[5].toInt(),
                pointsA = f[6].toInt(),
                pointsB = f[7].toInt(),
                tiebreak = f[8] == "1",
                gameServer = Team.valueOf(f[9]),
                winner = f[10].takeIf { it.isNotEmpty() }?.let { Team.valueOf(it) },
            )
        }.getOrNull()
    }
}
