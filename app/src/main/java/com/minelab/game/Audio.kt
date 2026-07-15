package com.minelab.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Audio du jeu.
 *  - MUSIQUE : une piste par salle, choisie dans les REGLAGES (8 pistes dispo).
 *  - BRUITAGES : synthetises a la volee (aucun fichier necessaire).
 */
class Audio(private val ctx: Context) {

    companion object {
        /** Les zones du donjon (une musique par zone). */
        val ZONES = arrayOf(
            "Ecran titre",
            "Grande salle (demineur)",
            "Salle du coffre",
            "Salle de rangement",
            "Couloir du sous-sol",
            "Salle des couleurs",
            "Salle des torches",
            "Combat / Boss",
            "Ile - bord de mer",
            "Ile - village",
            "PUNK CLUB (concert)"
        )
        /** 8 pistes de donjon + 6 pistes de village. */
        val TRACKS = intArrayOf(
            R.raw.music1, R.raw.music2, R.raw.music3, R.raw.music4,
            R.raw.music5, R.raw.music6, R.raw.music7, R.raw.music8,
            R.raw.village1, R.raw.village2, R.raw.village3,
            R.raw.village4, R.raw.village5, R.raw.village6,
            R.raw.punk_slip,
            R.raw.punk_champi1, R.raw.punk_champi2, R.raw.taverne_song, R.raw.taverne_song2,
            R.raw.punk_champi3, R.raw.punk_champi4, R.raw.punk_champi5
        )
        val TRACK_NAMES = arrayOf(
            "donjon 1", "donjon 2", "donjon 3", "donjon 4",
            "donjon 5", "donjon 6", "donjon 7", "donjon 8",
            "village lent 1", "village lent 2", "village rapide 1",
            "village rapide 2", "village joyeux 1", "village joyeux 2",
            "Le slip a Pierre (PUNK!)",
            "Champis Magiques I", "Champis Magiques II", "Chanson du bar", "Chanson du bar 2",
            "Boucherie Antifasciste I", "Boucherie Antifasciste II", "Equarrissage"
        )
        const val T_CHAMPI1 = 15
        const val T_CHAMPI2 = 16
        const val T_TAVERNE = 17
        const val T_TAVERNE2 = 18
        const val T_CHAMPI3 = 19
        const val T_CHAMPI4 = 20
        const val T_CHAMPI5 = 21
        /** Toutes les chansons du concert (offrir un champi en pioche une). */
        val CONCERT = intArrayOf(T_CHAMPI1, T_CHAMPI2, T_CHAMPI3, T_CHAMPI4, T_CHAMPI5)
        const val NONE = -1
    }

    /** zoneTrack[zone] = index de piste (0..7) ou NONE. */
    val zoneTrack = IntArray(ZONES.size) { if (it < 8) it else if (it == 8) 8 else if (it == 10) 14 else 12 }

    var musicOn = true
    var sfxOn = true
    var musicVol = 0.55f

    private var player: MediaPlayer? = null
    private var currentZone = -1
    private var currentTrack = -2
    /** Piste ponctuelle (concert, chanson de bar) qui prime sur la zone. */
    private var eventTrack = NONE

    // ------------------------------------------------------------ musique

    fun setZone(zone: Int) {
        if (zone == currentZone) return
        currentZone = zone
        applyTrack()
    }

    /** A appeler quand les reglages changent. */
    fun refresh() {
        // Si la musique est coupee, on arrete aussi un titre evenementiel en cours
        if (!musicOn && eventTrack != NONE) { eventTrack = NONE; stopMusic() }
        currentTrack = -2
        applyTrack()
    }

    private fun applyTrack() {
        if (eventTrack != NONE) return    // un titre ponctuel a la priorite
        val z = currentZone
        if (z < 0 || z >= zoneTrack.size) return
        val t = if (musicOn) zoneTrack[z] else NONE
        if (t == currentTrack) return
        currentTrack = t
        stopMusic()
        if (t == NONE || t < 0 || t >= TRACKS.size) return
        try {
            val mp = MediaPlayer.create(ctx, TRACKS[t]) ?: return
            mp.isLooping = true
            mp.setVolume(musicVol, musicVol)
            mp.start()
            player = mp
        } catch (e: Exception) {
            player = null
        }
    }

    /**
     * Joue un TITRE ponctuel (une chanson du groupe, la chanson du bar...) qui
     * couvre la musique de zone. A la fin, la musique de zone reprend seule.
     * Ne re-declenche pas le meme titre s'il tourne deja.
     */
    fun playEvent(track: Int) {
        if (!musicOn) return
        if (eventTrack == track && player != null) return
        eventTrack = track
        currentTrack = -3            // force le rechargement quand on reviendra a la zone
        stopMusic()
        try {
            val mp = MediaPlayer.create(ctx, TRACKS[track]) ?: return
            mp.isLooping = false
            mp.setVolume(musicVol, musicVol)
            mp.setOnCompletionListener {
                eventTrack = NONE
                currentTrack = -2
                applyTrack()          // la musique de zone reprend
            }
            mp.start()
            player = mp
        } catch (e: Exception) {
            eventTrack = NONE
            player = null
        }
    }

    /** Coupe un titre evenementiel en cours (ex : on quitte la salle). */
    fun stopEvent() {
        if (eventTrack == NONE) return
        eventTrack = NONE
        currentTrack = -2
        applyTrack()
    }

    fun isEventPlaying() = eventTrack != NONE

    fun setVolume(v: Float) {
        musicVol = v.coerceIn(0f, 1f)
        try { player?.setVolume(musicVol, musicVol) } catch (e: Exception) { }
    }

    private fun stopMusic() {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) { }
        player = null
    }

    fun pause() { try { player?.pause() } catch (e: Exception) { } }
    fun resume() { if (musicOn) try { player?.start() } catch (e: Exception) { } }
    fun release() { stopMusic(); for (t in cache.values) try { t.release() } catch (e: Exception) { } ; cache.clear() }

    // ------------------------------------------------------------ bruitages

    private val rate = 22050
    private val cache = HashMap<String, AudioTrack>()
    private val rnd = Random(7)

    fun play(name: String) {
        if (!sfxOn) return
        try {
            val t = cache.getOrPut(name) { build(name) }
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) { }
    }

    private fun build(name: String): AudioTrack {
        val pcm = when (name) {
            "boom" -> boom()
            "pickup" -> sweep(600f, 1300f, 0.16f, 0.35f)
            "heart" -> arp(floatArrayOf(659f, 880f, 1175f), 0.09f)
            "flag" -> blip(1500f, 0.05f)
            "disarm" -> arp(floatArrayOf(880f, 1320f), 0.09f)
            "push" -> thud(120f, 0.18f)
            "chest" -> arp(floatArrayOf(523f, 659f, 784f, 1047f), 0.11f)
            "key" -> arp(floatArrayOf(1047f, 1319f, 1568f), 0.12f)
            "door" -> sweep(320f, 130f, 0.5f, 0.3f)
            "torch" -> noise(0.35f, 0.35f, true)
            "sword" -> noise(0.18f, 0.3f, false)
            "hit" -> hit()
            "win" -> arp(floatArrayOf(523f, 659f, 784f, 1047f, 1319f), 0.14f)
            "lose" -> sweep(500f, 90f, 0.9f, 0.35f)
            "simon0" -> blip(392f, 0.28f)
            "simon1" -> blip(523f, 0.28f)
            "simon2" -> blip(659f, 0.28f)
            "simon3" -> blip(784f, 0.28f)
            "error" -> square(160f, 0.18f)
            "splash" -> noise(0.22f, 0.32f, true)
            "bite" -> arp(floatArrayOf(988f, 1319f), 0.07f)
            else -> blip(800f, 0.06f)
        }
        val bytes = pcm.size * 2
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(pcm, 0, pcm.size)
        return track
    }

    // --- generateurs

    private fun env(i: Int, n: Int, decay: Float): Float =
        exp(-decay * 6f * i / n).toFloat()

    private fun blip(freq: Float, dur: Float): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val v = sin(2.0 * PI * freq * i / rate).toFloat() * env(i, n, 1f) * 0.4f
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun square(freq: Float, dur: Float): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val s = if (sin(2.0 * PI * freq * i / rate) > 0) 1f else -1f
            out[i] = (s * env(i, n, 1.2f) * 0.3f * 32767).toInt().toShort()
        }
        return out
    }

    private fun sweep(f0: Float, f1: Float, dur: Float, amp: Float): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = f0 + (f1 - f0) * t
            ph += 2.0 * PI * f / rate
            val v = sin(ph).toFloat() * env(i, n, 0.9f) * amp
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun arp(freqs: FloatArray, each: Float): ShortArray {
        val n = (rate * each).toInt()
        val out = ShortArray(n * freqs.size)
        for ((k, f) in freqs.withIndex()) {
            for (i in 0 until n) {
                val v = (sin(2.0 * PI * f * i / rate) * 0.7 +
                        sin(4.0 * PI * f * i / rate) * 0.3).toFloat() * env(i, n, 1.1f) * 0.35f
                out[k * n + i] = (v * 32767).toInt().toShort()
            }
        }
        return out
    }

    private fun thud(freq: Float, dur: Float): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = freq * (1f - 0.5f * t)
            val v = sin(2.0 * PI * f * i / rate).toFloat() * env(i, n, 2f) * 0.5f
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }

    private fun noise(dur: Float, amp: Float, rising: Boolean): ShortArray {
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val raw = rnd.nextFloat() * 2f - 1f
            lp += (raw - lp) * (if (rising) 0.05f + 0.5f * t else 0.6f - 0.5f * t)
            val e = if (rising) (1f - t) * (0.3f + t) else env(i, n, 2f)
            out[i] = (lp * e * amp * 32767).toInt().toShort()
        }
        return out
    }

    private fun boom(): ShortArray {
        val dur = 0.55f
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val raw = rnd.nextFloat() * 2f - 1f
            lp += (raw - lp) * 0.12f
            val sub = sin(2.0 * PI * (90f - 60f * t) * i / rate).toFloat()
            val v = (lp * 0.7f + sub * 0.6f) * exp(-4.0 * t).toFloat() * 0.75f
            out[i] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }

    private fun hit(): ShortArray {
        val dur = 0.22f
        val n = (rate * dur).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val sq = if (sin(2.0 * PI * (220f - 120f * t) * i / rate) > 0) 1f else -1f
            val nz = rnd.nextFloat() * 2f - 1f
            val v = (sq * 0.5f + nz * 0.5f) * exp(-8.0 * t).toFloat() * 0.5f
            out[i] = (v * 32767).toInt().toShort()
        }
        return out
    }
}
