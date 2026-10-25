package nl.micha.padelscore.score

/**
 * Wat een scheidsrechter roept na een wijziging van de stand, zonder teamnamen.
 * Punten worden genoemd vanuit de serveerder (voordeel/nadeel), games in de volgorde van
 * het scherm (Wij links, Zij rechts). Een regelovergang ("\n") betekent een korte pauze.
 */
object RefereeCall {
    const val PAUSE = "\n"
    const val WELCOME = "Fijne wedstrijd${PAUSE}Maak er een mooie pot van"

    /** Tekst om om te roepen bij de overgang van [previous] naar [next], of null als er niets te melden is. */
    fun announce(previous: MatchState?, next: MatchState): String? {
        if (previous == next) return null
        // Begin van een nieuwe partij: een welkom in plaats van "nul gelijk".
        if (previous == null && next.isFresh()) return WELCOME
        val call = mainCall(previous, next) ?: return null
        // Na de stand, zoals een scheidsrechter: de spelers staan dan nog op hun oude plek.
        val change = previous != null && next.winner == null &&
            previous.endsSwapped != next.endsSwapped && movedForward(previous, next)
        return if (change) "$call${PAUSE}Kant wisselen" else call
    }

    /** Een gespeeld punt, geen "laatste punt terug". Kant wisselen gebeurt alleen na een game of tiebreakpunt. */
    private fun movedForward(previous: MatchState, next: MatchState): Boolean {
        val gamesBefore = previous.totalGames()
        val gamesAfter = next.totalGames()
        return gamesAfter > gamesBefore ||
            (gamesAfter == gamesBefore && next.tiebreak && next.pointsA + next.pointsB > previous.pointsA + previous.pointsB)
    }

    private fun MatchState.isFresh(): Boolean =
        sets.isEmpty() && gamesA == 0 && gamesB == 0 && pointsA == 0 && pointsB == 0 && winner == null

    private fun MatchState.totalGames(): Int = sets.sumOf { it.gamesA + it.gamesB } + gamesA + gamesB

    private fun mainCall(previous: MatchState?, next: MatchState): String? {
        // Games in de volgorde waarin de spelers ze zien: de kant van voor het punt.
        val left = previous?.leftTeam ?: next.leftTeam
        if (next.winner != null) {
            if (previous?.winner != null) return null
            if (next.settings.setsToWin == 0) return "Game, set en match${PAUSE}${gamesCall(next.gamesA, next.gamesB, left)}"
            val sets = next.sets.joinToString(", ") { gamesCall(it.gamesA, it.gamesB, left) }
            return "Game, set en match$PAUSE$sets"
        }
        if (previous != null && next.sets.size > previous.sets.size) {
            val set = next.sets.last()
            return "Game en set$PAUSE${gamesCall(set.gamesA, set.gamesB, left)}"
        }
        if (previous != null && next.sets.size == previous.sets.size &&
            next.gamesA + next.gamesB > previous.gamesA + previous.gamesB
        ) {
            val call = "Game$PAUSE${gamesCall(next.gamesA, next.gamesB, left)}"
            return if (next.tiebreak) "$call. Tiebreak" else call
        }
        return scoreCall(next)
    }

    /** De huidige puntenstand, vanuit de serveerder. */
    fun scoreCall(state: MatchState): String {
        val serverPoints = if (state.server == Team.A) state.pointsA else state.pointsB
        val receiverPoints = if (state.server == Team.A) state.pointsB else state.pointsA
        // Gelijke punten worden dubbel genoemd ("dertig, dertig"); alleen 40-40 heet "deuce".
        if (state.tiebreak) return "${number(serverPoints)}, ${number(receiverPoints)}"
        if (serverPoints >= 3 && receiverPoints >= 3) {
            return when {
                serverPoints == receiverPoints && state.settings.deuceRule == DeuceRule.STAR -> when {
                    state.deuces >= 3 -> "Star point"
                    state.deuces == 2 -> "Deuce twee"
                    else -> "Deuce"
                }
                serverPoints == receiverPoints && state.isDecidingPoint -> "Deuce. ${state.settings.deuceRule.label}"
                serverPoints == receiverPoints -> "Deuce"
                serverPoints > receiverPoints -> "Voordeel"
                else -> "Nadeel"
            }
        }
        return "${pointWord(serverPoints)}, ${pointWord(receiverPoints)}"
    }

    private fun gamesCall(gamesA: Int, gamesB: Int, left: Team): String {
        val (first, second) = if (left == Team.A) gamesA to gamesB else gamesB to gamesA
        return if (first == second) "${number(first)} gelijk" else "${number(first)}, ${number(second)}"
    }

    private fun pointWord(points: Int): String = when (points) {
        0 -> "nul"
        1 -> "vijftien"
        2 -> "dertig"
        else -> "veertig"
    }

    private val numbers = listOf(
        // "één" met accenten, anders spreekt de stem het uit als het lidwoord "een".
        "nul", "één", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen", "tien",
        "elf", "twaalf", "dertien", "veertien", "vijftien", "zestien", "zeventien", "achttien", "negentien", "twintig",
    )

    private fun number(n: Int): String = numbers.getOrNull(n) ?: n.toString()
}
