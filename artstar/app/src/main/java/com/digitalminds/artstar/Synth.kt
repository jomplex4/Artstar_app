package com.digitalminds.artstar

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Sintetizador ligero. Un hilo genera audio continuo: una voz melodica (sintesis aditiva con timbre de metal)
 * y clics de metronomo. El metronomo propio cuenta muestras, asi que no se desfasa.
 */
class Synth {
    companion object {
        const val RATE = 44100
    }

    private class Cmd(val type: Int, val a: Double = 0.0)

    private val queue = ConcurrentLinkedQueue<Cmd>()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    @Volatile var volume = 0.55f

    // metronomo propio (pantalla Metrónomo)
    @Volatile var metroOn = false
    @Volatile var metroBpm = 100
    @Volatile var metroBeats = 4
    @Volatile var metroSub = 1
    @Volatile var metroTick = 0      // se incrementa en cada pulso
    @Volatile var metroBeatIndex = 0 // pulso dentro del compas

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val size = max(minBuf, 4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t
        running = true
        t.play()
        thread = Thread({ render(t) }, "artstar-synth").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun stop() {
        running = false
        try { thread?.join(400) } catch (_: InterruptedException) { }
        thread = null
        try { track?.stop() } catch (_: IllegalStateException) { }
        track?.release()
        track = null
    }

    /** Inicia una nota a la frecuencia dada (Hz). */
    fun noteOn(freq: Double) { queue.add(Cmd(1, freq)) }
    fun noteOff() { queue.add(Cmd(2)) }

    /** Clic de metronomo: nivel 2 = acento, 1 = pulso, 0 = subdivision suave. */
    fun click(level: Int) { queue.add(Cmd(3, level.toDouble())) }

    /** Tono corto de prueba (explorador de notas, afinador de referencia). */
    fun beep(freq: Double) { queue.add(Cmd(4, freq)) }

    fun silence() { queue.add(Cmd(5)) }

    // ---- hilo de render ----
    private fun render(t: AudioTrack) {
        val buf = ShortArray(256)
        var phase = 0.0
        var freq = 440.0
        var gate = false
        var env = 0.0
        var beepLeft = 0
        var clickPos = -1
        var clickFreq = 1500.0
        var clickAmp = 0.0
        var metroSamples = 0.0
        var metroCount = 0
        val harm = doubleArrayOf(1.0, 0.62, 0.48, 0.34, 0.2, 0.12, 0.07)
        val norm = harm.sum()
        val atk = 1.0 / (0.012 * RATE)
        val rel = 1.0 / (0.07 * RATE)
        var wasMetro = false

        while (running) {
            var c = queue.poll()
            while (c != null) {
                when (c.type) {
                    1 -> { if (gate) env *= 0.35; freq = c.a; gate = true; beepLeft = 0 }
                    2 -> gate = false
                    3 -> { clickPos = 0; val lv = c.a.toInt(); clickFreq = if (lv >= 2) 2200.0 else if (lv == 1) 1500.0 else 1000.0; clickAmp = if (lv >= 2) 0.9 else if (lv == 1) 0.7 else 0.35 }
                    4 -> { freq = c.a; gate = true; beepLeft = (0.45 * RATE).toInt() }
                    5 -> { gate = false; beepLeft = 0; env = 0.0 }
                }
                c = queue.poll()
            }
            val vol = volume.toDouble()
            val mOn = metroOn
            if (mOn && !wasMetro) { metroSamples = 0.0; metroCount = 0 }
            wasMetro = mOn
            val interval = RATE * 60.0 / max(20, metroBpm) / max(1, metroSub)

            for (i in buf.indices) {
                if (beepLeft > 0) { beepLeft--; if (beepLeft == 0) gate = false }
                env = if (gate) minOf(1.0, env + atk) else maxOf(0.0, env - rel)
                var s = 0.0
                if (env > 0.0001) {
                    val inc = 2.0 * PI * freq / RATE
                    var v = 0.0
                    for (k in harm.indices) v += harm[k] * sin(phase * (k + 1))
                    s += v / norm * env * 0.8
                    phase += inc
                    if (phase > 2.0 * PI * 1000) phase -= 2.0 * PI * 1000
                }
                if (mOn) {
                    if (metroSamples <= 0.0) {
                        val sub = max(1, metroSub)
                        val within = metroCount % sub
                        val beatNo = metroCount / sub
                        val lv = if (within != 0) 0 else if (beatNo % max(1, metroBeats) == 0) 2 else 1
                        clickPos = 0
                        clickFreq = if (lv >= 2) 2200.0 else if (lv == 1) 1500.0 else 1000.0
                        clickAmp = if (lv >= 2) 0.9 else if (lv == 1) 0.7 else 0.35
                        if (within == 0) { metroBeatIndex = beatNo % max(1, metroBeats); metroTick++ }
                        metroCount++
                        metroSamples += interval
                    }
                    metroSamples -= 1.0
                }
                if (clickPos >= 0) {
                    val tt = clickPos.toDouble() / RATE
                    s += sin(2.0 * PI * clickFreq * tt) * exp(-tt * 140.0) * clickAmp
                    clickPos++
                    if (clickPos > RATE * 0.06) clickPos = -1
                }
                var o = s * vol
                if (o > 1.0) o = 1.0 else if (o < -1.0) o = -1.0
                buf[i] = (o * 32000).toInt().toShort()
            }
            val w = t.write(buf, 0, buf.size)
            if (w < 0) break
        }
    }
}

fun midiToFreq(midi: Double, a4: Double = 440.0): Double = a4 * Math.pow(2.0, (midi - 69.0) / 12.0)
