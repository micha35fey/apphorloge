package nl.micha.padelscore

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import nl.micha.padelscore.score.MatchSettings
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.sync.ScoreSync
import nl.micha.padelscore.update.UpdateEvents
import nl.micha.padelscore.update.UpdateInfo
import nl.micha.padelscore.update.Updater

enum class Screen { Start, Match, ConfirmStop }

// Releases van de horloge-app op GitHub.
const val UPDATE_TAG_PREFIX = "v"
const val UPDATE_ASSET = "padelscore.apk"

class MatchViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("padel", Context.MODE_PRIVATE)

    var settings by mutableStateOf(MatchSettings())
        private set

    var state by mutableStateOf(MatchState())
        private set

    var screen by mutableStateOf(Screen.Start)
        private set

    /** Scherm aan houden tijdens een partij. Een app-voorkeur, dus los van de partij bewaard. */
    var keepScreenOn by mutableStateOf(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
        private set

    private val updater = Updater(app, tagPrefix = UPDATE_TAG_PREFIX, assetName = UPDATE_ASSET)

    /** Nieuwere versie op GitHub, of null als de app actueel is (of er geen internet is). */
    var update by mutableStateOf<UpdateInfo?>(null)
        private set

    /** Tekst over de voortgang van een update, voor op het startscherm. */
    var updateStatus by mutableStateOf<String?>(null)

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
            if (saved.winner == null) MatchNotification.show(app, saved)
        }
        viewModelScope.launch { update = updater.check(BuildConfig.VERSION_CODE) }
        viewModelScope.launch { UpdateEvents.status.collect { if (it != null) updateStatus = it } }
    }

    fun installUpdate() {
        val info = update ?: return
        updateStatus = "Downloaden..."
        viewModelScope.launch {
            runCatching { updater.install(info) }.onFailure { updateStatus = "Mislukt: ${it.message}" }
        }
    }

    fun updateKeepScreenOn(on: Boolean) {
        keepScreenOn = on
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, on).apply()
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
        ScoreSync.send(getApplication(), state, active = false)
        MatchNotification.hide(getApplication())
        screen = Screen.Start
    }

    private fun save() {
        prefs.edit()
            .putString(KEY_STATE, state.encode())
            .putString(KEY_HISTORY, history.joinToString("\n") { it.encode() })
            .apply()
        ScoreSync.send(getApplication(), state, active = true)
        if (state.winner == null) MatchNotification.show(getApplication(), state)
        else MatchNotification.hide(getApplication())
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_HISTORY = "history"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val MAX_HISTORY = 200
    }
}
