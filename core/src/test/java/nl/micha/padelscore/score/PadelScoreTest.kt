package nl.micha.padelscore.score

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PadelScoreTest {

    private fun MatchState.points(team: Team, n: Int): MatchState {
        var s = this
        repeat(n) { s = s.pointFor(team) }
        return s
    }

    // 4 punten op rij is altijd een game, ongeacht de regel.
    private fun MatchState.games(team: Team, n: Int): MatchState = points(team, 4 * n)

    @Test
    fun pointLabelsCountUp() {
        var s = MatchState()
        assertEquals("0", s.pointLabel(Team.A))
        s = s.pointFor(Team.A)
        assertEquals("15", s.pointLabel(Team.A))
        s = s.pointFor(Team.A)
        assertEquals("30", s.pointLabel(Team.A))
        s = s.pointFor(Team.A)
        assertEquals("40", s.pointLabel(Team.A))
        s = s.pointFor(Team.A)
        assertEquals(1, s.gamesA)
        assertEquals("0", s.pointLabel(Team.A))
    }

    @Test
    fun goldenPointDecidesAtDeuce() {
        var s = MatchState(MatchSettings(goldenPoint = true))
        s = s.points(Team.A, 3).points(Team.B, 3)
        assertTrue(s.isGoldenPointMoment)
        s = s.pointFor(Team.B)
        assertEquals(0, s.gamesA)
        assertEquals(1, s.gamesB)
    }

    @Test
    fun advantageRuleNeedsTwoPoints() {
        var s = MatchState(MatchSettings(goldenPoint = false))
        s = s.points(Team.A, 3).points(Team.B, 3)
        s = s.pointFor(Team.A)
        assertEquals("AD", s.pointLabel(Team.A))
        assertEquals("40", s.pointLabel(Team.B))
        s = s.pointFor(Team.B)
        assertEquals("40", s.pointLabel(Team.A))
        assertEquals("40", s.pointLabel(Team.B))
        s = s.pointFor(Team.B).pointFor(Team.B)
        assertEquals(1, s.gamesB)
    }

    @Test
    fun setWonAtSixFour() {
        var s = MatchState()
        repeat(4) { s = s.games(Team.A, 1).games(Team.B, 1) }
        assertEquals(4, s.gamesA)
        assertEquals(4, s.gamesB)
        s = s.games(Team.A, 2)
        assertEquals(listOf(SetScore(6, 4)), s.sets)
        assertEquals(0, s.gamesA)
    }

    @Test
    fun noSetAtSixFive() {
        var s = MatchState()
        repeat(5) { s = s.games(Team.A, 1).games(Team.B, 1) }
        s = s.games(Team.A, 1)
        assertEquals(6, s.gamesA)
        assertEquals(5, s.gamesB)
        assertTrue(s.sets.isEmpty())
        s = s.games(Team.A, 1)
        assertEquals(listOf(SetScore(7, 5)), s.sets)
    }

    @Test
    fun tiebreakAtSixAllToSevenWithTwoLead() {
        var s = MatchState()
        repeat(6) { s = s.games(Team.A, 1).games(Team.B, 1) }
        assertTrue(s.tiebreak)
        s = s.points(Team.A, 6).points(Team.B, 6)
        assertEquals("6", s.pointLabel(Team.A))
        s = s.pointFor(Team.A)
        assertTrue(s.tiebreak)
        s = s.pointFor(Team.A)
        assertEquals(listOf(SetScore(7, 6)), s.sets)
        assertTrue(!s.tiebreak)
    }

    @Test
    fun serveAlternatesPerGameAndInTiebreak() {
        var s = MatchState(MatchSettings(firstServer = Team.A))
        assertEquals(Team.A, s.server)
        s = s.games(Team.A, 1)
        assertEquals(Team.B, s.server)
        s = MatchState(gamesA = 6, gamesB = 6, tiebreak = true, gameServer = Team.A)
        val order = mutableListOf(s.server)
        repeat(5) {
            s = s.pointFor(Team.A)
            order.add(s.server)
        }
        assertEquals(listOf(Team.A, Team.B, Team.B, Team.A, Team.A, Team.B), order)
    }

    @Test
    fun matchEndsAfterTwoSets() {
        var s = MatchState(MatchSettings(setsToWin = 2))
        s = s.games(Team.B, 6)
        assertNull(s.winner)
        s = s.games(Team.B, 6)
        assertEquals(Team.B, s.winner)
        assertEquals(s, s.pointFor(Team.A))
    }

    @Test
    fun oneSetMatch() {
        val s = MatchState(MatchSettings(setsToWin = 1)).games(Team.A, 6)
        assertEquals(Team.A, s.winner)
    }

    @Test
    fun gamesOnlyKeepsCountingWithoutSetsOrTiebreak() {
        var s = MatchState(MatchSettings(setsToWin = 0))
        repeat(7) { s = s.games(Team.A, 1).games(Team.B, 1) }
        s = s.games(Team.A, 2)
        assertEquals(9, s.gamesA)
        assertEquals(7, s.gamesB)
        assertTrue(s.sets.isEmpty())
        assertTrue(!s.tiebreak)
        assertNull(s.winner)
        assertEquals("15", s.pointFor(Team.B).pointLabel(Team.B))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        var s = MatchState(MatchSettings(goldenPoint = false, setsToWin = 2, firstServer = Team.B))
        s = s.games(Team.A, 6).games(Team.B, 3).points(Team.A, 2)
        assertEquals(s, MatchState.decode(s.encode()))
        assertNull(MatchState.decode("rommel"))
    }
}
