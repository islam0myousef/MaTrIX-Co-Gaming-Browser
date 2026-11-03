package com.matrixco.browser

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/** مؤثرات صوتية مولّدة بالكود (بدون ملفات) + اهتزاز خفيف — أسلوب ألعاب */
object Fx {
    private const val RATE = 22050
    private val cache = HashMap<String, ByteArray>()

    private fun tone(freqs: List<Double>, ms: Int, square: Boolean): ByteArray {
        val n = RATE * ms / 1000
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val seg = (i * freqs.size / n).coerceAtMost(freqs.size - 1)
            val f = freqs[seg]
            var v = sin(2 * PI * f * t)
            if (square) v = if (v >= 0) 0.6 else -0.6
            val env = 1.0 - i.toDouble() / n
            val s = (v * env * env * 9000).toInt()
            out[i * 2] = (s and 0xFF).toByte(); out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun data(kind: String, pack: String): ByteArray = cache.getOrPut("$pack/$kind") {
        val sq = pack == "retro"
        when (kind) {
            "open" -> tone(listOf(520.0, 780.0), 90, sq)
            "close" -> tone(listOf(700.0, 440.0), 90, sq)
            "alert" -> tone(listOf(900.0, 600.0, 900.0), 160, sq)
            else -> tone(listOf(if (sq) 660.0 else 880.0), 45, sq)
        }
    }

    fun play(kind: String) {
        val pack = Cfg.str(Cfg.SOUND, "off")
        if (pack == "off") return
        Thread {
            try {
                val d = data(kind, pack)
                val at = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    d.size, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                at.write(d, 0, d.size)
                at.setVolume(0.35f)
                at.play()
                Thread.sleep(d.size * 1000L / (RATE * 2) + 80)
                at.release()
            } catch (_: Throwable) {}
        }.start()
    }

    /** اهتزاز + صوت عند اللمس */
    fun tap(v: View?, kind: String = "click") {
        if (Cfg.bool(Cfg.HAPTIC, true)) v?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        play(kind)
    }
}
