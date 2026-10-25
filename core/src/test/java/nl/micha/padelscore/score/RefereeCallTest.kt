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
        assertEquals("vijftien gelijk", call(fifteenAll, Team.B))
    }

    @Test
    fun advantageAndDisadvantageForTheServer() {
        var s = MatchState(MatchSettings(goldenPoint = false, firstServer = Team.A))
        repeat(3) { s = s.pointFor(Team.A).pointFor(Team.B) }
        assertEquals("Veertig gelijk", RefereeCall.scoreCall(s))
        assertEquals("Voordeel", call(s, Team.A))
        assertEquals("Nadeel", call(s, Team.B))
    }

    @Test
    fun goldenPoint() {
        var s = MatchState(MatchSettings(goldenPoint = true))
        repeat(2) { s = s.pointFor(Team.A).pointFor(Team.B) }
        s = s.pointFor(Team.A)
        assertEquals("Veertig gelijk. Gouden punt", call(s, Team.B))
    }

    @Test
    fun gameThenPauseThenGames() {
        var s = MatchState()
        repeat(4) { s = s.pointFor(Team.B) }
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\neen gelijk", call(s, Team.A))
        s = s.pointFor(Team.A)
        repeat(4) { s = s.pointFor(Team.A) }
        repeat(3) { s = s.pointFor(Team.A) }
        assertEquals("Game\ndrie, een", call(s, Team.A))
    }

    @Test
    fun setAndMatch() {
        var s = MatchState(MatchSettings(setsToWin = 1))
        repeat(4) { s = s.pointFor(Team.B) }
        repeat(4) { s = s.pointFor(Team.A) }
        repeat(4 * 4) { s = s.pointFor(Team.B) }
        repeat(3) { s = s.pointFor(Team.B) }
        assertEquals("Game, set en match\neen, zes", call(s, Team.B))
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
}
