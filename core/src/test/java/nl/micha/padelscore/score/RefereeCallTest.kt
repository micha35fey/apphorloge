package nl.micha.padelscore.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefereeCallTest {

    private fun call(previous: MatchState, team: Team): String? = RefereeCall.announce(previous, previous.pointFor(team))

    @Test
    fun pointsAreCalledFromTheServer() {
        val start = MatchState(MatchSettings(firstServer = Team.B))
        assertEquals("nul, vijftien", call(start, Team.A))
        assertEquals("vijftien, nul", call(start, Team.B))
        val fifteenAll = start.pointFor(Team.A)
        assertEquals("vijftien, vijftien", call(fifteenAll, Team.B))
    }

    @Test
    fun advantageAndDisadvantageForTheServer() {
        var s = MatchState(MatchSettings(deuceRule = DeuceRule.ADVANTAGE, firstServer = Team.A))
        repeat(3) { s = s.pointFor(Team.A).pointFor(Team.B) }
        assertEquals("Deuce", RefereeCall.scoreCall(s))
        assertEquals("Voordeel", call(s, Team.A))
        assertEquals("Nadeel", call(s, Team.B))
    }

    @Test
    fun goldenPoint() {
        var s = MatchState(MatchSettings(deuceRule = DeuceRule.GOLDEN))
        repeat(2) { s = s.pointFor(Team.A).pointFor(Team.B) }
        s = s.pointFor(Team.A)
        assertEquals("Deuce. Gouden punt", call(s, Team.B))
    }

    @Test
    fun gameThenPauseThenGames() {
        var s = MatchState()
        repeat(4) { s = s.pointFor(Team.B) }
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\néén gelijk", call(s, Team.A))
        s = s.pointFor(Team.A)
        repeat(4) { s = s.pointFor(Team.A) }
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\ndrie, één", call(s, Team.A))
    }

    @Test
    fun setAndMatch() {
        var s = MatchState(MatchSettings(setsToWin = 1))
        repeat(4) { s = s.pointFor(Team.B) }
        repeat(4) { s = s.pointFor(Team.A) }
        repeat(4 * 4) { s = s.pointFor(Team.B) }
        repeat(3) { s = s.pointFor(Team.B) }
        assertEquals("Game, set en match\néén, zes", call(s, Team.B))
        assertNull(RefereeCall.announce(s.pointFor(Team.B), s.pointFor(Team.B)))
    }

    @Test
    fun setInBestOfThree() {
        var s = MatchState()
        repeat(5 * 4 + 3) { s = s.pointFor(Team.A) }
        assertEquals("Game en set\nzes, nul", call(s, Team.A))
    }

    @Test
    fun tiebreakIsAnnounced() {
        var s = MatchState()
        repeat(5) { repeat(4) { s = s.pointFor(Team.A) }; repeat(4) { s = s.pointFor(Team.B) } }
        repeat(4) { s = s.pointFor(Team.A) }
        repeat(3) { s = s.pointFor(Team.B) }
        assertEquals("Game\nzes gelijk. Tiebreak", call(s, Team.B))
    }

    @Test
    fun undoCallsTheScoreAgain() {
        val before = MatchState().pointFor(Team.A)
        val after = before.pointFor(Team.A)
        assertEquals("vijftien, nul", RefereeCall.announce(after, before))
    }

    @Test
    fun changeEndsAfterOddGames() {
        var s = MatchState(MatchSettings(changeEnds = true))
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\néén, nul\nKant wisselen", call(s, Team.A))
        s = s.pointFor(Team.A)
        assertEquals(Team.B, s.leftTeam)
        // Na game 2 geen wissel; de games in de volgorde van het scherm (Zij links).
        repeat(3) { s = s.pointFor(Team.B) }
        assertEquals("Game\néén gelijk", call(s, Team.B))
        s = s.pointFor(Team.B)
        repeat(3) { s = s.pointFor(Team.B) }
        assertEquals("Game\ntwee, één\nKant wisselen", call(s, Team.B))
    }

    @Test
    fun noChangeEndsWhenOff() {
        var s = MatchState(MatchSettings(changeEnds = false))
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\néén, nul", call(s, Team.A))
        assertEquals(Team.A, s.pointFor(Team.A).leftTeam)
    }

    @Test
    fun undoDoesNotAnnounceChangeEnds() {
        var s = MatchState(MatchSettings(changeEnds = true))
        repeat(3) { s = s.pointFor(Team.A) }
        val after = s.pointFor(Team.A)
        assertEquals("veertig, nul", RefereeCall.announce(after, s))
    }

    @Test
    fun changeEndsEverySixTiebreakPoints() {
        var s = MatchState(MatchSettings(changeEnds = true), gamesA = 6, gamesB = 6, tiebreak = true)
        val before = s.endsSwapped
        repeat(5) { s = s.pointFor(if (it % 2 == 0) Team.A else Team.B) }
        val call = call(s, Team.A)
        assertEquals(true, call!!.endsWith("Kant wisselen"))
        assertEquals(!before, s.pointFor(Team.A).endsSwapped)
    }

    @Test
    fun welcomeAtTheStartOfAMatch() {
        assertEquals("Fijne wedstrijd\nMaak er een mooie pot van", RefereeCall.announce(null, MatchState()))
        // Terug naar 0-0 met "laatste punt terug" geeft gewoon de stand.
        val fifteen = MatchState().pointFor(Team.A)
        assertEquals("nul, nul", RefereeCall.announce(fifteen, MatchState()))
        // App opnieuw gestart midden in een partij: de stand, geen welkom.
        assertEquals("vijftien, nul", RefereeCall.announce(null, fifteen))
    }

    @Test
    fun equalPointsAreCalledTwice() {
        var s = MatchState(MatchSettings(deuceRule = DeuceRule.ADVANTAGE))
        s = s.pointFor(Team.A).pointFor(Team.B).pointFor(Team.A)
        assertEquals("dertig, dertig", call(s, Team.B))
        val tiebreak = MatchState(gamesA = 6, gamesB = 6, tiebreak = true).pointFor(Team.A).pointFor(Team.B)
        assertEquals("twee, twee", call(tiebreak.pointFor(Team.A), Team.B))
    }

    @Test
    fun starPointIsAnnounced() {
        var s = MatchState(MatchSettings(deuceRule = DeuceRule.STAR))
        repeat(2) { s = s.pointFor(Team.A).pointFor(Team.B) }
        s = s.pointFor(Team.A)
        assertEquals("Deuce", call(s, Team.B))
        s = s.pointFor(Team.B).pointFor(Team.A)
        assertEquals("Deuce", call(s, Team.B))
        s = s.pointFor(Team.B).pointFor(Team.A)
        assertEquals("Deuce. Star point", call(s, Team.B))
    }
}
