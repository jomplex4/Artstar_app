package com.digitalminds.artstar

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/** Resultado de un cuadro de analisis. voiced = hay una nota estable; pc = clase de altura 0..11 (Do=0). */
class PitchFrame(val voiced: Boolean, val freq: Float, val midi: Float, val pc: Int, val cents: Float, val level: Float, val noteId: Int, val clarity: Float = 0f)

/**
 * Detector monofonico de altura (YIN). Trabaja a 22050 Hz tras decimar, con ventana de 3072 muestras (llega hasta unos 15 Hz, suficiente para la tuba).
 * Para comparar con la partitura se usa la clase de altura (octava ignorada), asi funciona igual con bajo,
 * barítono o trompeta aunque el instrumento suene en otra octava.
 */
class PitchDetector(private val a4: () -> Double, private val sensitivity: () -> Float, private val yinThreshold: Float = 0.12f, private val onFrame: (PitchFrame) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var thread: Thread? = null
    private var rec: AudioRecord? = null

    private var noteId = 0
    private var lastPc = -1
    private var gap = 99

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 16384))
        } catch (e: Exception) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        rec = r
        running = true
        noteId = 0; lastPc = -1; gap = 99
        r.startRecording()
        thread = Thread({ loop(r) }, "artstar-mic").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        try { thread?.join(400) } catch (_: InterruptedException) { }
        thread = null
        try { rec?.stop() } catch (_: IllegalStateException) { }
        rec?.release()
        rec = null
    }

    private fun loop(r: AudioRecord) {
        val hop = 2048                     // muestras a 44100 por lectura
        val raw = ShortArray(hop)
        val n = 3072                       // ventana a 22050 (mas larga para las notas graves)
        val win = FloatArray(n)
        var filled = 0
        val half = n / 2
        val diff = FloatArray(half)
        val cmnd = FloatArray(half)
        while (running) {
            var got = 0
            while (got < hop && running) {
                val k = r.read(raw, got, hop - got)
                if (k <= 0) { running = false; break }
                got += k
            }
            if (!running) break
            // decimar por 2 (promedio de pares) y desplazar la ventana
            val newCount = hop / 2
            System.arraycopy(win, newCount, win, 0, n - newCount)
            var rms = 0.0
            for (i in 0 until newCount) {
                val v = (raw[2 * i] + raw[2 * i + 1]) / 65536f
                win[n - newCount + i] = v
                rms += (v * v).toDouble()
            }
            if (filled < n) filled += newCount
            if (filled < n) continue
            val level = sqrt(rms / newCount).toFloat()
            val frame = analyze(win, diff, cmnd, half, level)
            main.post { onFrame(frame) }
        }
    }

    private fun analyze(x: FloatArray, d: FloatArray, cm: FloatArray, w: Int, level: Float): PitchFrame {
        val gate = sensitivity()
        var voiced = false
        var f0 = 0f
        var clarity = 0f
        if (level > gate) {
            // funcion de diferencia
            for (tau in 1 until w) {
                var s = 0f
                var j = 0
                while (j < w) {
                    val dd = x[j] - x[j + tau]
                    s += dd * dd
                    j++
                }
                d[tau] = s
            }
            cm[0] = 1f
            var run = 0f
            for (tau in 1 until w) {
                run += d[tau]
                cm[tau] = if (run > 0f) d[tau] * tau / run else 1f
            }
            val tauMin = 14
            var tau = tauMin
            var found = -1
            while (tau < w - 1) {
                if (cm[tau] < yinThreshold) {   // mas bajo = mas exigente
                    while (tau + 1 < w - 1 && cm[tau + 1] < cm[tau]) tau++
                    found = tau
                    break
                }
                tau++
            }
            if (found > 0) {
                val a = cm[found - 1]; val b = cm[found]; val c = cm[found + 1]
                val den = a - 2 * b + c
                val shift = if (abs(den) > 1e-9f) 0.5f * (a - c) / den else 0f
                val t = found + shift
                f0 = 22050f / t
                clarity = (1f - b).coerceIn(0f, 1f)
                voiced = f0 in 25f..1500f
            }
        }
        if (!voiced) {
            gap++
            if (gap >= 2) lastPc = -1
            return PitchFrame(false, 0f, 0f, -1, 0f, level, noteId)
        }
        val midi = (69.0 + 12.0 * ln(f0 / a4()) / ln(2.0)).toFloat()
        val nearest = round(midi)
        val pc = ((nearest.toInt() % 12) + 12) % 12
        val cents = (midi - nearest) * 100f
        if (pc != lastPc || gap >= 2) noteId++
        lastPc = pc
        gap = 0
        return PitchFrame(true, f0, midi, pc, cents, level, noteId, clarity)
    }
}
