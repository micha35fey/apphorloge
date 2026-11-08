package nl.micha.padelscore.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import nl.micha.padelscore.audio.Chime
import nl.micha.padelscore.audio.WavJoin
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.RefereeCall
import nl.micha.padelscore.sync.SyncedScore
import java.io.File
import java.util.Locale

/** Roept de stand om als een scheidsrechter, met de spraak van de telefoon. */
object Announcer {
    private const val PREFS = "phone"
    private const val KEY_ENABLED = "announce"
    private const val KEY_VOICE = "voice"
    private const val KEY_APPLAUSE = "applause_uri"
    private const val APPLAUSE_MS = 10_000L
    private const val PAUSE_MS = 700L

    /**
     * Stilte vooraf, zodat een Bluetooth-speaker uit zijn slaapstand komt en het begin niet mist.
     * Per speaker verschillend, daarom in te stellen ("Speaker-vertraging").
     */
    private const val KEY_LEAD = "lead_ms"
    private const val KEY_CHIME = "chime"
    private val LEAD_OPTIONS = listOf(500, 1000, 1500, 2000, 3000)
    private const val DEFAULT_LEAD = 1000
    private const val LAST_PART = "last"
    private const val SAMPLE = "Dertig, vijftien\nGame\ndrie, één"

    // Nederlandse mannenstemmen van Spraakservices van Google, herkenbaar aan hun naam.
    private val MALE_VOICE_HINTS = listOf("nl-nl-x-bmh", "nl-nl-x-dma")

    /** Lagere toon als er geen mannenstem is, zodat de stem zwaarder klinkt. */
    private const val DEEP_PITCH = 0.75f

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var last: MatchState? = null
    private var onReady: (() -> Unit)? = null
    private var audio: AudioManager? = null
    private var appContext: Context? = null

    /** Na de omroep van het einde van de partij volgt applaus. */
    private var applauseNext = false
    private var player: MediaPlayer? = null

    /** Speelt de samengevoegde omroep af. */
    private var voicePlayer: MediaPlayer? = null
    private var batch = 0
    private var spokenText: String? = null
    private const val SYNTH = "synth:"

    /** Onhoorbaar zacht geluid tijdens de partij, zodat een Bluetooth-speaker niet in slaap valt. */
    private var keepAlive: MediaPlayer? = null
    private var matchActive = false
    private var appVisible = false
    private val main = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        // Media-geluid: werkt ook als de telefoon op stil of trillen staat, en gaat naar een Bluetooth-speaker.
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // Muziek gaat even zachter zolang de scheidsrechter praat, zoals bij een navigatie-app.
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        if (!on) {
            tts?.stop()
            main.post { stopKeepAlive() }
        }
    }

    /** Start de spraak alvast, zodat de stemmenlijst klaar is. [ready] wordt aangeroepen als dat zo is. */
    fun prepare(context: Context, ready: () -> Unit) {
        if (this.ready) ready() else onReady = ready
        engine(context.applicationContext)
    }

    fun onScore(context: Context, score: SyncedScore) {
        val appContext = context.applicationContext
        matchActive = score.active
        main.post { updateKeepAlive(appContext) }
        if (!score.active) {
            last = null
            return
        }
        val text = RefereeCall.announce(last, score.state)
        val matchEnded = last != null && last?.winner == null && score.state.winner != null
        last = score.state
        if (text != null && isEnabled(context)) {
            applauseNext = matchEnded
            speak(context.applicationContext, text)
        }
    }

    /** Beschrijving van de gekozen stem, bijvoorbeeld "Stem 2 van 5". */
    fun voiceLabel(): String {
        val voices = dutchVoices()
        val index = voices.indexOfFirst { it.name == tts?.voice?.name }
        if (voices.isEmpty() || index < 0) return "Standaardstem, lager"
        val voice = voices[index]
        val details = listOfNotNull(
            voice.shortName(),
            if (voice.looksMale()) "man" else "lager",
            if (voice.isNetworkConnectionRequired) "online" else null,
        ).joinToString(", ")
        return "Stem ${index + 1} van ${voices.size}: $details"
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
        engine.setAudioAttributes(attributes)
        audio = context.getSystemService(AudioManager::class.java)
        appContext = context
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                when {
                    // Laatste stuk van een omroep is als bestand klaar: samenvoegen en afspelen.
                    utteranceId?.startsWith(SYNTH) == true -> {
                        val (id, index, count) = utteranceId!!.removePrefix(SYNTH).split(":").map { it.toInt() }
                        if (index == count - 1) main.post { playJoined(id, count) }
                    }
                    utteranceId == LAST_PART -> main.post { finishedSpeaking() }
                }
            }

            @Deprecated("Vereist door de interface")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith(SYNTH) == true) {
                    // Opnemen lukt niet: dan toch direct uitspreken.
                    main.post { spokenText?.let { speakDirect(it) } }
                } else {
                    audio?.abandonAudioFocusRequest(focus)
                }
            }
        })
        val voices = dutchVoices()
        val saved = prefs(context).getString(KEY_VOICE, null)
        // Eigen keuze, anders een stem die zich als man herkenbaar maakt, anders de standaard.
        val voice = voices.firstOrNull { it.name == saved }
            ?: voices.firstOrNull { it.looksMale() }
        if (voice != null) engine.voice = voice
        engine.setPitch(if (voice?.looksMale() == true) 1f else DEEP_PITCH)
    }

    /**
     * Nederlandse stemmen uit Nederland (Belgische alleen als er geen andere zijn).
     * Stemmen die zonder internet werken eerst, online stemmen achteraan.
     */
    private fun dutchVoices(): List<Voice> {
        val dutch = tts?.voices.orEmpty().filter { it.locale.language == "nl" }
        val fromNetherlands = dutch.filter { it.locale.country.equals("NL", ignoreCase = true) }
        return fromNetherlands.ifEmpty { dutch }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
    }

    /** Herkenbaar stukje van de naam, bijvoorbeeld "bmh" uit "nl-nl-x-bmh-local". */
    private fun Voice.shortName(): String =
        name.substringAfter("-x-", name).substringBefore("-").take(12)

    private fun Voice.looksMale(): Boolean {
        val text = (name + " " + features.orEmpty().joinToString(" ")).lowercase()
        return MALE_VOICE_HINTS.any { it in text } || ("male" in text && "female" !in text)
    }

    // Een nieuwe stand onderbreekt de vorige, zodat je bij snelle punten altijd de actuele hoort.
    // Elke regel is een los stuk met een korte pauze ertussen.
    /** Eigen applausgeluid van de telefoon, of null voor het ingebouwde. */
    fun customApplause(context: Context): Uri? =
        prefs(context).getString(KEY_APPLAUSE, null)?.let(Uri::parse)

    fun setCustomApplause(context: Context, uri: Uri?) {
        prefs(context).edit().putString(KEY_APPLAUSE, uri?.toString()).apply()
    }

    /** Applaus laten horen, om het geluid uit te proberen. */
    fun testApplause(context: Context) {
        appContext = context.applicationContext
        if (audio == null) audio = context.getSystemService(AudioManager::class.java)
        audio?.requestAudioFocus(focus)
        playApplause()
    }

    /**
     * Ongeveer 10 seconden applaus: een eigen geluid als dat gekozen is, anders het ingebouwde
     * (zelf gegenereerd, zie mobile/applause_gen.py). Een lang eigen geluid stopt na 10 seconden.
     */
    private fun playApplause() {
        val context = appContext ?: return
        stopApplause()
        val done = {
            stopApplause()
            audio?.abandonAudioFocusRequest(focus)
        }
        player = customApplause(context)?.let { uri ->
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(context, uri)
                    prepare()
                }
            }.getOrNull()
        } ?: run {
            val session = audio?.generateAudioSessionId() ?: return
            MediaPlayer.create(context, R.raw.applause, attributes, session)
        }
        player?.apply {
            setOnCompletionListener { done() }
            start()
        }
        main.postDelayed({ if (player != null) done() }, APPLAUSE_MS + 500)
    }

    /**
     * Houdt de geluidsverbinding open met een geluid van ongeveer -50 dB (buiten niet te horen,
     * maar ook geen echte stilte, die speakers als "uit" zien). Zonder dit valt een
     * Bluetooth-speaker tussen de punten in slaap en mist hij het begin van de stand.
     */
    private fun startKeepAlive(context: Context) {
        if (keepAlive != null) return
        val session = context.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: return
        keepAlive = MediaPlayer.create(context, R.raw.keepalive, attributes, session)?.apply {
            isLooping = true
            start()
        }
    }

    private fun stopKeepAlive() {
        keepAlive?.release()
        keepAlive = null
    }

    private fun stopApplause() {
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    /**
     * Spreekt [text] uit. Elk stuk wordt eerst naar een WAV-bestand opgenomen; die worden met de
     * stilte vooraf en de pauzes tot één bestand samengevoegd en in één keer afgespeeld. Zo start
     * de geluidsstroom naar een Bluetooth-speaker maar één keer en valt er niets weg.
     */
    private fun say(text: String) {
        val engine = tts ?: return
        val context = appContext ?: return speakDirect(text)
        // Een nieuwe stand onderbreekt de vorige omroep en het applaus.
        main.post {
            stopApplause()
            stopVoice()
        }
        engine.stop()
        spokenText = text
        val id = ++batch
        val parts = text.split(RefereeCall.PAUSE)
        parts.forEachIndexed { i, part ->
            val result = engine.synthesizeToFile(part, null, partFile(context, id, i), "$SYNTH$id:$i:${parts.size}")
            if (result != TextToSpeech.SUCCESS) return speakDirect(text)
        }
    }

    private fun playJoined(id: Int, count: Int) {
        val context = appContext ?: return
        if (id != batch) return  // er is intussen alweer een nieuwere stand
        val files = (0 until count).map { partFile(context, id, it) }
        val pcm = files.map { runCatching { WavJoin.read(it.readBytes()) }.getOrNull() }
        files.forEach { it.delete() }
        if (pcm.any { it == null }) return spokenText?.let { speakDirect(it) } ?: Unit
        val joined = File(context.cacheDir, "omroep.wav")
        val parts = pcm.filterNotNull()
        val chime = if (isChimeEnabled(context)) Chime.pcm(parts.first().sampleRate, parts.first().channels) else null
        joined.writeBytes(WavJoin.join(parts, leadMs(context), PAUSE_MS.toInt(), prefix = chime))
        stopVoice()
        audio?.requestAudioFocus(focus)
        voicePlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(joined.path)
                prepare()
                setOnCompletionListener {
                    stopVoice()
                    finishedSpeaking()
                }
                start()
            }
        }.getOrElse {
            spokenText?.let { text -> speakDirect(text) }
            null
        }
    }

    private fun stopVoice() {
        voicePlayer?.release()
        voicePlayer = null
    }

    private fun partFile(context: Context, id: Int, index: Int) = File(context.cacheDir, "omroep_${id}_$index.wav")

    /** Na de omroep: applaus als de partij net is afgelopen, anders muziek weer op volume. */
    private fun finishedSpeaking() {
        if (applauseNext) {
            applauseNext = false
            playApplause()
        } else {
            audio?.abandonAudioFocusRequest(focus)
        }
    }

    /** Reserve: rechtstreeks uitspreken, in stukken met pauzes ertussen. */
    private fun speakDirect(text: String) {
        val engine = tts ?: return
        audio?.requestAudioFocus(focus)
        engine.playSilentUtterance(leadMs(appContext).toLong(), TextToSpeech.QUEUE_FLUSH, "wake")
        val parts = text.split(RefereeCall.PAUSE)
        parts.forEachIndexed { i, part ->
            if (i > 0) engine.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_ADD, "pause$i")
            val id = if (i == parts.lastIndex) LAST_PART else "part$i"
            engine.speak(part, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ingestelde stilte vooraf in ms. */
    fun leadMs(context: Context?): Int =
        context?.let { prefs(it).getInt(KEY_LEAD, DEFAULT_LEAD) } ?: DEFAULT_LEAD

    /** Volgende stand van de speaker-vertraging kiezen en meteen een voorbeeld laten horen. */
    fun nextLead(context: Context): Int {
        val next = LEAD_OPTIONS.firstOrNull { it > leadMs(context) } ?: LEAD_OPTIONS.first()
        prefs(context).edit().putInt(KEY_LEAD, next).apply()
        testAnnouncement(context)
        return next
    }

    /** Gong vóór elke omroep, zoals in een sporthal. */
    fun isChimeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_CHIME, false)

    fun setChimeEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_CHIME, on).apply()
        testAnnouncement(context)
    }

    /**
     * De app staat open (of niet). Ook dan de speaker wakker houden, zodat "Test omroep" laat
     * horen hoe het tijdens een partij klinkt.
     */
    fun setAppVisible(context: Context, visible: Boolean) {
        appVisible = visible
        val appContext = context.applicationContext
        main.post { updateKeepAlive(appContext) }
    }

    private fun updateKeepAlive(context: Context) {
        if ((matchActive || appVisible) && isEnabled(context)) startKeepAlive(context) else stopKeepAlive()
    }

    /** Een voorbeeld-omroep, om stem en speaker-vertraging uit te proberen. */
    fun testAnnouncement(context: Context) {
        appContext = context.applicationContext
        if (ready) say(SAMPLE) else speak(context.applicationContext, SAMPLE)
    }
}
