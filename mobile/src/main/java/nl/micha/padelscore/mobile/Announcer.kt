package nl.micha.padelscore.mobile

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.RefereeCall
import nl.micha.padelscore.sync.SyncedScore
import java.util.Locale

/** Roept de stand om als een scheidsrechter, met de spraak van de telefoon. */
object Announcer {
    private const val PREFS = "phone"
    private const val KEY_ENABLED = "announce"

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var last: MatchState? = null

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) tts?.stop()
    }

    fun onScore(context: Context, score: SyncedScore) {
        if (!score.active) {
            last = null
            return
        }
        val text = RefereeCall.announce(last, score.state)
        last = score.state
        if (text != null && isEnabled(context)) speak(context.applicationContext, text)
    }

    private fun speak(context: Context, text: String) {
        val engine = tts
        if (engine == null) {
            pending = text
            tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                tts?.language = Locale("nl", "NL")
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        // Media-geluid: werkt ook als de telefoon op stil of trillen staat.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                pending?.let { say(it) }
                pending = null
            }
        } else if (ready) {
            say(text)
        } else {
            pending = text
        }
    }

    // Nieuwe stand onderbreekt de vorige: bij snelle punten hoor je altijd de actuele stand.
    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "score")
    }
}
