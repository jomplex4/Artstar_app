package com.digitalminds.artstar

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Muestra grabada (PCM 16 bits) con su nota base y su zona de bucle. */
class Sample(val data: ShortArray, val root: Int, val ls: Int, val le: Int) {
    val looped get() = ls in 0 until le
}

/** Banco de sonidos reales (FluidR3 GM, licencia CC BY 3.0). */
object Bank {
    var rate = 22050
    private val inst = HashMap<String, List<Sample>>()
    @Volatile var ready = false

    fun load(ctx: Context) {
        if (ready) return
        try {
            val idx = JSONObject(ctx.assets.open("sounds/index.json").bufferedReader().use { it.readText() })
            rate = idx.getInt("rate")
            val io = idx.getJSONObject("inst")
            for (name in io.keys()) {
                val arr = io.getJSONArray(name)
                val list = ArrayList<Sample>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val bytes = ctx.assets.open("sounds/" + o.getString("file")).use { it.readBytes() }
                    val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val d = ShortArray(sb.remaining()); sb.get(d)
                    list.add(Sample(d, o.getInt("root"), o.getInt("ls"), o.getInt("le")))
                }
                inst[name] = list.sortedBy { it.root }
            }
            ready = true
        } catch (e: Exception) {
            ready = false
        }
    }

    fun pick(name: String, midi: Int): Sample? {
        val l = inst[name] ?: return null
        var best: Sample? = null
        var bd = 999
        for (s in l) { val d = kotlin.math.abs(s.root - midi); if (d < bd) { bd = d; best = s } }
        return best
    }
}

/**
 * Motor de audio de ART STAR. Mezcla en tiempo real:
 * voces con muestras reales (instrumento practicado + acompañamiento de banda),
 * percusion de marcha sintetizada (bombo, tarola, napoleon, platillo, pandereta) y metronomo.
 */
class Synth {
    companion object {
        const val RATE = 44100
        const val BOMBO = 0; const val TAROLA = 1; const val NAPOLEON = 2; const val PLATILLO = 3; const val PANDERETA = 4
    }

    private class Cmd(val type: Int, val ch: Int = 0, val a: Int = 0, val b: Float = 1f, val inst: String = "")

    private class Voice {
        var s: Sample? = null
        var pos = 0.0
        var step = 0.0
        var gain = 1f
        var env = 0f
        var releasing = false
        var ch = -1
        var active = false
        var autoOff = 0
    }

    private class Drum {
        var type = 0
        var t = 0
        var delay = 0
        var level = 1f
        var active = false
        var lp = 0f
        var hp = 0f
        var prev = 0f
        var phase = 0.0
    }

    private val queue = ConcurrentLinkedQueue<Cmd>()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    @Volatile var volume = 0.9f
    @Volatile var metroOn = false
    @Volatile var metroBpm = 100
    @Volatile var metroBeats = 4
    @Volatile var metroSub = 1
    @Volatile var metroAccent = true
    @Volatile var metroTick = 0
    @Volatile var metroBeatIndex = 0

    fun start() {
        if (running) return
        try {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            track = t
            running = true
            t.play()
            thread = Thread({ render(t) }, "artstar-audio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        running = false
        try { thread?.join(400) } catch (_: InterruptedException) { }
        thread = null
        try { track?.stop() } catch (_: IllegalStateException) { }
        track?.release()
        track = null
    }

    /** Nota en un canal (cada canal es monofonico). midi = altura que suena. */
    fun noteOn(ch: Int, inst: String, midi: Int, vel: Float) { queue.add(Cmd(1, ch, midi, vel, inst)) }
    fun noteOff(ch: Int) { queue.add(Cmd(2, ch)) }
    fun allOff() { queue.add(Cmd(5)) }
    fun drum(type: Int, level: Float, roll: Boolean = false) { queue.add(Cmd(6, if (roll) 1 else 0, type, level)) }
    fun click(level: Int) { queue.add(Cmd(3, 0, level)) }
    /** Nota corta de prueba (explorador, tablas, afinador). */
    fun play(inst: String, midi: Int, ms: Int = 700) { queue.add(Cmd(4, 15, midi, 1f, inst + "|" + ms)) }

    private fun render(t: AudioTrack) {
        val n = 256
        val buf = ShortArray(n)
        val mix = FloatArray(n)
        val voices = Array(16) { Voice() }
        val drums = Array(16) { Drum() }
        var clickPos = -1; var clickFreq = 1500.0; var clickAmp = 0.0
        var metroSamples = 0.0; var metroCount = 0; var wasMetro = false
        var seed = 0x12345678
        val atk = 1f / (0.004f * RATE)
        val rel = 1f / (0.09f * RATE)
        val ratio = Bank.rate.toDouble() / RATE

        fun freeVoice(): Voice {
            for (v in voices) if (!v.active) return v
            var best = voices[0]
            for (v in voices) if (v.releasing && (!best.releasing || v.env < best.env)) best = v
            return best
        }
        fun startNote(ch: Int, inst: String, midi: Int, vel: Float, autoMs: Int) {
            for (v in voices) if (v.active && v.ch == ch && !v.releasing) v.releasing = true
            val s = Bank.pick(inst, midi) ?: return
            val v = freeVoice()
            v.s = s; v.pos = 0.0; v.step = ratio * 2.0.pow((midi - s.root) / 12.0)
            v.gain = vel; v.env = 0f; v.releasing = false; v.ch = ch; v.active = true
            v.autoOff = if (autoMs > 0) autoMs * RATE / 1000 else 0
        }

        while (running) {
            var c = queue.poll()
            while (c != null) {
                when (c.type) {
                    1 -> startNote(c.ch, c.inst, c.a, c.b, 0)
                    2 -> for (v in voices) if (v.active && v.ch == c.ch) v.releasing = true
                    3 -> { clickPos = 0; val lv = c.a; clickFreq = if (lv >= 2) 2000.0 else if (lv == 1) 1400.0 else 1000.0; clickAmp = if (lv >= 2) 0.85 else if (lv == 1) 0.6 else 0.3 }
                    4 -> { val p = c.inst.split("|"); startNote(15, p[0], c.a, 1f, p.getOrNull(1)?.toIntOrNull() ?: 700) }
                    5 -> { for (v in voices) if (v.active) v.releasing = true }
                    6 -> {
                        val count = if (c.ch == 1) 3 else 1
                        for (k in 0 until count) {
                            val d = drums.firstOrNull { !it.active } ?: drums[0]
                            d.type = c.a; d.t = 0; d.delay = k * (RATE * 35 / 1000); d.level = c.b * (if (count > 1) 0.7f else 1f)
                            d.active = true; d.lp = 0f; d.hp = 0f; d.prev = 0f; d.phase = 0.0
                        }
                    }
                }
                c = queue.poll()
            }
            java.util.Arrays.fill(mix, 0f)

            // voces con muestras
            for (v in voices) {
                if (!v.active) continue
                val s = v.s ?: continue
                val d = s.data
                for (i in 0 until n) {
                    if (v.autoOff > 0) { v.autoOff--; if (v.autoOff == 0) v.releasing = true }
                    v.env = if (v.releasing) v.env - rel else min(1f, v.env + atk)
                    if (v.env <= 0f && v.releasing) { v.active = false; break }
                    val ip = v.pos.toInt()
                    if (ip + 1 >= d.size) { v.active = false; break }
                    val fr = (v.pos - ip).toFloat()
                    val smp = (d[ip] + (d[ip + 1] - d[ip]) * fr) / 32768f
                    mix[i] += smp * v.gain * v.env
                    v.pos += v.step
                    if (s.looped && v.pos >= s.le - 1) v.pos -= (s.le - s.ls)
                }
            }

            // percusion sintetizada
            for (d in drums) {
                if (!d.active) continue
                for (i in 0 until n) {
                    if (d.delay > 0) { d.delay--; continue }
                    val tt = d.t.toFloat() / RATE
                    seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
                    val noise = (seed and 0xFFFF) / 32768f - 1f
                    var o = 0f
                    when (d.type) {
                        BOMBO -> {
                            val f = 48.0 + 70.0 * exp(-tt * 28.0)
                            d.phase += 2.0 * PI * f / RATE
                            o = (sin(d.phase) * exp(-tt * 6.5)).toFloat() * 1.1f + (if (tt < 0.004f) noise * 0.3f else 0f)
                            if (tt > 0.6f) d.active = false
                        }
                        TAROLA -> {
                            d.hp = noise - d.prev; d.prev = noise
                            d.phase += 2.0 * PI * 205.0 / RATE
                            o = d.hp * exp(-tt * 18f) * 0.55f + (sin(d.phase) * exp(-tt * 30.0)).toFloat() * 0.45f
                            if (tt > 0.3f) d.active = false
                        }
                        NAPOLEON -> {
                            d.lp += (noise - d.lp) * 0.25f
                            d.phase += 2.0 * PI * 135.0 / RATE
                            o = d.lp * exp(-tt * 10f) * 0.9f + (sin(d.phase) * exp(-tt * 16.0)).toFloat() * 0.55f
                            if (tt > 0.45f) d.active = false
                        }
                        PLATILLO -> {
                            d.hp = noise - d.prev; d.prev = noise
                            val metal = (sin(2.0 * PI * 3200.0 * tt) * sin(2.0 * PI * 5130.0 * tt)).toFloat()
                            o = (d.hp * 0.6f + metal * 0.25f) * exp(-tt * 4.2f) * 0.5f
                            if (tt > 1.1f) d.active = false
                        }
                        PANDERETA -> {
                            d.hp = noise - d.prev; d.prev = noise
                            val jingle = (0.6 + 0.4 * sin(2.0 * PI * 31.0 * tt)).toFloat()
                            o = d.hp * jingle * exp(-tt * 11f) * 0.45f
                            if (tt > 0.35f) d.active = false
                        }
                    }
                    mix[i] += o * d.level * 0.8f
                    d.t++
                }
            }

            // metronomo independiente (contado por muestras)
            val mOn = metroOn
            if (mOn && !wasMetro) { metroSamples = 0.0; metroCount = 0 }
            wasMetro = mOn
            val interval = RATE * 60.0 / max(20, metroBpm) / max(1, metroSub)
            for (i in 0 until n) {
                if (mOn) {
                    if (metroSamples <= 0.0) {
                        val sub = max(1, metroSub)
                        val within = metroCount % sub
                        val beatNo = metroCount / sub
                        val lv = if (within != 0) 0 else if (metroAccent && beatNo % max(1, metroBeats) == 0) 2 else 1
                        clickPos = 0
                        clickFreq = if (lv >= 2) 2000.0 else if (lv == 1) 1400.0 else 1000.0
                        clickAmp = if (lv >= 2) 0.85 else if (lv == 1) 0.6 else 0.3
                        if (within == 0) { metroBeatIndex = beatNo % max(1, metroBeats); metroTick++ }
                        metroCount++
                        metroSamples += interval
                    }
                    metroSamples -= 1.0
                }
                if (clickPos >= 0) {
                    val tt = clickPos.toDouble() / RATE
                    mix[i] += (sin(2.0 * PI * clickFreq * tt) * exp(-tt * 150.0) * clickAmp).toFloat()
                    clickPos++
                    if (clickPos > RATE * 0.05) clickPos = -1
                }
            }

            val vol = volume
            for (i in 0 until n) {
                var o = mix[i] * vol
                o = if (o > 1f) 1f else if (o < -1f) -1f else o
                buf[i] = (o * 32000f).toInt().toShort()
            }
            if (t.write(buf, 0, n) < 0) break
        }
    }
}

fun midiToFreq(midi: Double, a4: Double = 440.0): Double = a4 * Math.pow(2.0, (midi - 69.0) / 12.0)
