package nl.micha.padelscore.score

/**
 * Wat een scheidsrechter roept na een wijziging van de stand.
 * Punten worden genoemd vanuit de serveerder, games en sets vanuit de winnaar.
 */
object RefereeCall {

    /** Tekst om om te roepen bij de overgang van [previous] naar [next], of null als er niets te melden is. */
    fun announce(previous: MatchState?, next: MatchState): String? {
        if (previous == next) return null
        val winner = next.winner
        if (winner != null) {
            if (previous?.winner != null) return null
            val sets = next.sets.joinToString(", ") { set -> gamesFor(set.gamesA, set.gamesB, winner) }
            return if (next.settings.setsToWin == 0) {
                "Game, set en match ${name(winner)}"
            } else {
                "Game, set en match ${name(winner)}. $sets"
            }
        }
        if (previous != null && next.sets.size > previous.sets.size) {
            val set = next.sets.last()
            val setWinner = if (set.gamesA > set.gamesB) Team.A else Team.B
            return "Game en set ${name(setWinner)}, ${gamesFor(set.gamesA, set.gamesB, setWinner)}"
        }
        if (previous != null && next.sets.size == previous.sets.size &&
            next.gamesA + next.gamesB > previous.gamesA + previous.gamesB
        ) {
            val gameWinner = if (next.gamesA > previous.gamesA) Team.A else Team.B
            val call = "Game ${name(gameWinner)}. ${gamesCall(next)}"
            return if (next.tiebreak) "$call. Tiebreak" else call
        }
        return scoreCall(next)
    }

    /** De huidige puntenstand, vanuit de serveerder. */
    fun scoreCall(state: MatchState): String {
        val server = state.server
        val serverPoints = state.points(server)
        val receiverPoints = state.points(server.other)
        if (state.tiebreak) {
            return if (serverPoints == receiverPoints) {
                "${number(serverPoints)} gelijk"
            } else {
                "${number(serverPoints)}, ${number(receiverPoints)}"
            }
        }
        if (serverPoints >= 3 && receiverPoints >= 3) {
            return when {
                serverPoints == receiverPoints && state.settings.goldenPoint -> "Veertig gelijk, gouden punt"
                serverPoints == receiverPoints -> "Deuce"
                serverPoints > receiverPoints -> "Voordeel ${name(server)}"
                else -> "Voordeel ${name(server.other)}"
            }
        }
        if (serverPoints == receiverPoints) return "${pointWord(serverPoints)} gelijk"
        return "${pointWord(serverPoints)}, ${pointWord(receiverPoints)}"
    }

    private fun gamesCall(state: MatchState): String = when {
        state.gamesA == state.gamesB -> "${number(state.gamesA)} gelijk"
        state.gamesA > state.gamesB -> "${name(Team.A)} leidt ${number(state.gamesA)}, ${number(state.gamesB)}"
        else -> "${name(Team.B)} leidt ${number(state.gamesB)}, ${number(state.gamesA)}"
    }

    private fun gamesFor(gamesA: Int, gamesB: Int, first: Team): String =
        if (first == Team.A) "${number(gamesA)}, ${number(gamesB)}" else "${number(gamesB)}, ${number(gamesA)}"

    private fun MatchState.points(team: Team): Int = if (team == Team.A) pointsA else pointsB

    private fun name(team: Team): String = team.label.lowercase()

    private fun pointWord(points: Int): String = when (points) {
        0 -> "nul"
        1 -> "vijftien"
        2 -> "dertig"
        else -> "veertig"
    }

    private val numbers = listOf(
        "nul", "een", "twee", "drie", "vier", "vijf", "zes", "zeven", "acht", "negen", "tien",
        "elf", "twaalf", "dertien", "veertien", "vijftien", "zestien", "zeventien", "achttien", "negentien", "twintig",
    )

    private fun number(n: Int): String = numbers.getOrNull(n) ?: n.toString()
}
