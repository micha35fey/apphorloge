package nl.micha.padelscore.mobile

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.RefereeCall
import nl.micha.padelscore.sync.SyncedScore
import java.util.Locale

/** Roept de stand om als een scheidsrechter, met de spraak van de telefoon. */
object Announcer {
    private const val PREFS = "phone"
    private const val KEY_ENABLED = "announce"
    private const val KEY_VOICE = "voice"
    private const val PAUSE_MS = 700L
    private const val SAMPLE = "Dertig, vijftien\nGame\ndrie, één"

    // Nederlandse mannenstemmen van Spraakservices van Google, herkenbaar aan hun naam.
    private val MALE_VOICE_HINTS = listOf("nl-nl-x-bmh", "nl-nl-x-dma", "nl-be-x-bec")

    /** Lagere toon als er geen mannenstem is, zodat de stem zwaarder klinkt. */
    private const val DEEP_PITCH = 0.75f

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var last: MatchState? = null
    private var onReady: (() -> Unit)? = null

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) tts?.stop()
    }

    /** Start de spraak alvast, zodat de stemmenlijst klaar is. [ready] wordt aangeroepen als dat zo is. */
    fun prepare(context: Context, ready: () -> Unit) {
        if (this.ready) ready() else onReady = ready
        engine(context.applicationContext)
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

    /** Beschrijving van de gekozen stem, bijvoorbeeld "Stem 2 van 5". */
    fun voiceLabel(): String {
        val voices = dutchVoices()
        val index = voices.indexOfFirst { it.name == tts?.voice?.name }
        if (voices.isEmpty() || index < 0) return "Standaardstem, lager"
        val male = if (voices[index].looksMale()) "man" else "lager"
        return "Stem ${index + 1} van ${voices.size}, $male"
    }

    /** Volgende Nederlandse stem kiezen, onthouden en een voorbeeld laten horen. */
    fun nextVoice(context: Context) {
        val voices = dutchVoices()
        if (voices.isEmpty()) return
        val index = voices.indexOfFirst { it.name == tts?.voice?.name }
        val voice = voices[(index + 1) % voices.size]
        tts?.voice = voice
        tts?.setPitch(if (voice.looksMale()) 1f else DEEP_PITCH)
        prefs(context).edit().putString(KEY_VOICE, voice.name).apply()
        say(SAMPLE)
    }

    private fun speak(context: Context, text: String) {
        engine(context)
        if (ready) say(text) else pending = text
    }

    private fun engine(context: Context): TextToSpeech = tts ?: TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        tts?.let { setup(context, it) }
        pending?.let { say(it) }
        pending = null
        onReady?.invoke()
        onReady = null
    }.also { tts = it }

    private fun setup(context: Context, engine: TextToSpeech) {
        engine.language = Locale("nl", "NL")
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                // Media-geluid: werkt ook als de telefoon op stil of trillen staat.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val voices = dutchVoices()
        val saved = prefs(context).getString(KEY_VOICE, null)
        // Eigen keuze, anders een stem die zich als man herkenbaar maakt, anders de standaard.
        val voice = voices.firstOrNull { it.name == saved }
            ?: voices.firstOrNull { it.looksMale() }
        if (voice != null) engine.voice = voice
        engine.setPitch(if (voice?.looksMale() == true) 1f else DEEP_PITCH)
    }

    /** Nederlandse stemmen die zonder internet werken; als die er niet zijn, alle Nederlandse. */
    private fun dutchVoices(): List<Voice> {
        val dutch = tts?.voices.orEmpty().filter { it.locale.language == "nl" }.sortedBy { it.name }
        return dutch.filter { !it.isNetworkConnectionRequired }.ifEmpty { dutch }
    }

    private fun Voice.looksMale(): Boolean {
        val text = (name + " " + features.orEmpty().joinToString(" ")).lowercase()
        return MALE_VOICE_HINTS.any { it in text } || ("male" in text && "female" !in text)
    }

    // Een nieuwe stand onderbreekt de vorige, zodat je bij snelle punten altijd de actuele hoort.
    // Elke regel is een los stuk met een korte pauze ertussen.
    private fun say(text: String) {
        val engine = tts ?: return
        text.split(RefereeCall.PAUSE).forEachIndexed { i, part ->
            if (i > 0) engine.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_ADD, "pause$i")
            engine.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "score$i")
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
