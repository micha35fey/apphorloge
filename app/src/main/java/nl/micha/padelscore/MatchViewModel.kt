package nl.micha.padelscore

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import nl.micha.padelscore.score.MatchSettings
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team

enum class Screen { Start, Match, ConfirmStop }

class MatchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("padel", Context.MODE_PRIVATE)

    var settings by mutableStateOf(MatchSettings())
        private set

    var state by mutableStateOf(MatchState())
        private set

    var screen by mutableStateOf(Screen.Start)
        private set

    // Eerdere standen voor "ongedaan maken"; het laatste element is de vorige stand.
    private val history = ArrayDeque<MatchState>()

    val canUndo: Boolean get() = history.isNotEmpty()

    init {
        // Een lopende partij terugzetten als de app is afgesloten door het systeem.
        val saved = prefs.getString(KEY_STATE, null)?.let { MatchState.decode(it) }
        if (saved != null) {
            prefs.getString(KEY_HISTORY, null)
                ?.split("\n")
                ?.mapNotNull { MatchState.decode(it) }
                ?.forEach { history.addLast(it) }
            state = saved
            settings = saved.settings
            screen = Screen.Match
        }
    }

    fun updateSettings(newSettings: MatchSettings) {
        settings = newSettings
    }

    fun startMatch() {
        history.clear()
        state = MatchState(settings)
        screen = Screen.Match
        save()
    }

    fun point(team: Team) {
        if (state.winner != null) return
        history.addLast(state)
        while (history.size > MAX_HISTORY) history.removeFirst()
        state = state.pointFor(team)
        save()
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        state = previous
        save()
    }

    fun askStop() {
        screen = Screen.ConfirmStop
    }

    fun cancelStop() {
        screen = Screen.Match
    }

    fun stopMatch() {
        history.clear()
        prefs.edit().remove(KEY_STATE).remove(KEY_HISTORY).apply()
        screen = Screen.Start
    }

    private fun save() {
        prefs.edit()
            .putString(KEY_STATE, state.encode())
            .putString(KEY_HISTORY, history.joinToString("\n") { it.encode() })
            .apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY = 200
    }
}
