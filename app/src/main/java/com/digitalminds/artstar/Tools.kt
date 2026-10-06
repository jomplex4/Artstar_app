package com.digitalminds.artstar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Circulos de pulso: el activo se enciende en rojo. */
class BeatDots(ctx: Context) : android.view.View(ctx) {
    var count = 4
        set(v) { field = v; invalidate() }
    var active = -1
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = min(h * 0.32f, w / (count * 2.6f))
        val gap = w / count
        for (i in 0 until count) {
            val cx = gap * i + gap / 2f
            p.style = Paint.Style.FILL
            p.color = if (i == active) (if (i == 0) C.RED_HI else C.RED) else C.CARD2
            c.drawCircle(cx, h / 2f, if (i == active) r * 1.15f else r, p)
        }
    }
}

class MetronomeTab(private val host: Host) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private var bpm = host.prefs.getInt("metro_bpm", 100)
    private var sigIdx = host.prefs.getInt("metro_sig", 2)
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val bpmV = label(ctx, "", 88f, C.TEXT, Fonts.bold)
    private val unitV = label(ctx, "", 13f, C.MUTED)
    private val dots = BeatDots(ctx)
    private val seek = SeekView(ctx) { f -> setBpm(30 + (f * 190).toInt()) }
    private val playBtn = FrameLayout(ctx)
    private var lastTick = 0
    private val taps = ArrayList<Long>()
    private val sigs = arrayOf("2/4", "3/4", "4/4", "6/8")
    private val beatsOf = intArrayOf(2, 3, 4, 2)
    private val subOf = intArrayOf(1, 1, 1, 3)
    private val sigChips = ArrayList<Chip>()

    private val poll = object : Runnable {
        override fun run() {
            val t = host.synth.metroTick
            if (t != lastTick) { lastTick = t; dots.active = host.synth.metroBeatIndex }
            if (running) handler.postDelayed(this, 20)
        }
    }

    init {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(ctx.dpi(20f), ctx.dpi(10f), ctx.dpi(20f), ctx.dpi(10f))
        addView(col, LayoutParams(MATCH, MATCH))

        col.addView(View0(), lp(MATCH, 0, 0.6f))
        col.addView(bpmV, lp(WRAP, WRAP))
        col.addView(unitV, lp(WRAP, WRAP))
        col.addView(View0(), lp(MATCH, ctx.dpi(14f)))

        val adj = LinearLayout(ctx)
        adj.gravity = Gravity.CENTER
        for (d in intArrayOf(-5, -1, 1, 5)) {
            val ch = Chip(ctx, if (d > 0) "+$d" else "−${-d}")
            ch.tap { setBpm(bpm + d) }
            adj.addView(ch, lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(5f), 0, ctx.dpi(5f), 0) })
        }
        col.addView(adj, lp(MATCH, WRAP))
        col.addView(seek, lp(MATCH, ctx.dpi(34f)).also { it.topMargin = ctx.dpi(14f) })
        col.addView(dots, lp(MATCH, ctx.dpi(60f)).also { it.topMargin = ctx.dpi(10f) })

        val sg = LinearLayout(ctx)
        sg.gravity = Gravity.CENTER
        for (i in sigs.indices) {
            val ch = Chip(ctx, sigs[i])
            ch.setActive(i == sigIdx)
            ch.tap { setSig(i) }
            sigChips.add(ch)
            sg.addView(ch, lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(5f), 0, ctx.dpi(5f), 0) })
        }
        col.addView(sg, lp(MATCH, WRAP))

        col.addView(View0(), lp(MATCH, 0, 0.5f))
        playBtn.background = roundRect(C.RED, ctx.dp(40f))
        playBtn.tap { toggle() }
        col.addView(playBtn, lp(ctx.dpi(150f), ctx.dpi(64f)))
        val tap = Chip(ctx, "Marcar el tempo con toques")
        tap.tap { tapTempo() }
        col.addView(tap, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(14f) })
        col.addView(View0(), lp(MATCH, 0, 0.4f))
        refresh()
        drawPlay()
    }

    @Suppress("FunctionName")
    private fun View0(): android.view.View = android.view.View(ctx)

    private fun setBpm(v: Int) {
        bpm = v.coerceIn(30, 220)
        host.prefs.edit().putInt("metro_bpm", bpm).apply()
        host.synth.metroBpm = bpm
        refresh()
    }

    private fun setSig(i: Int) {
        sigIdx = i
        host.prefs.edit().putInt("metro_sig", i).apply()
        for ((k, c) in sigChips.withIndex()) c.setActive(k == i)
        host.synth.metroBeats = beatsOf[i]
        host.synth.metroSub = subOf[i]
        dots.count = beatsOf[i]
        dots.active = -1
        refresh()
        if (running) { host.synth.metroOn = false; host.synth.metroOn = true }
    }

    private fun refresh() {
        bpmV.text = bpm.toString()
        unitV.text = if (sigIdx == 3) "pulsos por minuto (negra con puntillo)" else "negras por minuto"
        seek.value = (bpm - 30) / 190f
        dots.count = beatsOf[sigIdx]
    }

    private fun drawPlay() {
        playBtn.removeAllViews()
        playBtn.addView(IconView(ctx, if (running) IconView.PAUSE else IconView.PLAY), LayoutParams(ctx.dpi(34f), ctx.dpi(34f), Gravity.CENTER))
    }

    private fun toggle() {
        running = !running
        val s = host.synth
        s.metroBpm = bpm
        s.metroBeats = beatsOf[sigIdx]
        s.metroSub = subOf[sigIdx]
        s.metroOn = running
        if (running) { lastTick = s.metroTick; handler.post(poll) } else { dots.active = -1; handler.removeCallbacks(poll) }
        drawPlay()
    }

    private fun tapTempo() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 6) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60000.0 / avg).toInt())
        }
    }

    fun activate() { refresh() }

    fun deactivate() {
        running = false
        host.synth.metroOn = false
        handler.removeCallbacks(poll)
    }
}

/** Barra de afinacion: aguja de -50 a +50 centesimas. */
class CentsBar(ctx: Context) : android.view.View(ctx) {
    var cents = 0f
        set(v) { field = v; invalidate() }
    var live = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val u = context.dp(1f)
        val y = h * 0.62f
        p.color = C.CARD2
        r.set(0f, y - u * 6, w, y + u * 6)
        c.drawRoundRect(r, u * 6, u * 6, p)
        // zona afinada
        p.color = C.CARD
        val zw = w * 0.1f
        r.set(w / 2 - zw / 2, y - u * 6, w / 2 + zw / 2, y + u * 6)
        c.drawRoundRect(r, u * 3, u * 3, p)
        p.color = C.DIM
        for (i in -5..5) {
            val x = w / 2 + i * (w / 11f)
            val len = if (i == 0) u * 20 else if (i % 5 == 0) u * 14 else u * 9
            c.drawRect(x - u * 0.8f, y - u * 6 - len, x + u * 0.8f, y - u * 6, p)
        }
        if (live) {
            val cl = cents.coerceIn(-50f, 50f)
            val x = w / 2 + cl / 50f * (w / 2 - u * 8)
            val inTune = abs(cents) <= 5f
            p.color = if (inTune) C.TEXT else C.RED_HI
            c.drawCircle(x, y, u * 12, p)
            p.color = C.BG
            c.drawCircle(x, y, u * 4, p)
        }
        p.color = C.MUTED
        p.textSize = u * 13
        p.typeface = Fonts.medium
        p.textAlign = Paint.Align.CENTER
        c.drawText("bajo", w * 0.08f, h - u * 6, p)
        c.drawText("alto", w * 0.92f, h - u * 6, p)
    }
}

class TunerTab(private val host: Host) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private var a4 = host.prefs.getInt("a4", 440)
    private var detector: PitchDetector? = null
    private var active = false
    private var smoothCents = 0f
    private var lastVoiced = 0L
    private val noteV = label(ctx, "-", 96f, C.TEXT, Fonts.bold)
    private val octV = label(ctx, "", 22f, C.MUTED, Fonts.medium)
    private val freqV = label(ctx, "", 14f, C.MUTED)
    private val writtenV = label(ctx, "", 15f, C.TEXT, Fonts.medium)
    private val statusV = label(ctx, "", 13f, C.MUTED)
    private val bar = CentsBar(ctx)
    private val refV = label(ctx, "", 15f, C.TEXT, Fonts.medium)
    private val handler = Handler(Looper.getMainLooper())
    private val fade = Runnable { if (System.currentTimeMillis() - lastVoiced > 600) showIdle() }

    init {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(ctx.dpi(20f), ctx.dpi(10f), ctx.dpi(20f), ctx.dpi(10f))
        addView(col, LayoutParams(MATCH, MATCH))
        col.addView(android.view.View(ctx), lp(MATCH, 0, 0.5f))
        val nrow = LinearLayout(ctx)
        nrow.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        nrow.addView(noteV, lp(WRAP, WRAP))
        octV.setPadding(ctx.dpi(6f), 0, 0, ctx.dpi(14f))
        nrow.addView(octV, lp(WRAP, WRAP))
        col.addView(nrow, lp(WRAP, WRAP))
        col.addView(freqV, lp(WRAP, WRAP))
        col.addView(bar, lp(MATCH, ctx.dpi(90f)).also { it.topMargin = ctx.dpi(16f) })
        writtenV.setPadding(0, ctx.dpi(10f), 0, 0)
        col.addView(writtenV, lp(WRAP, WRAP))
        statusV.setPadding(0, ctx.dpi(6f), 0, 0)
        col.addView(statusV, lp(WRAP, WRAP))
        col.addView(android.view.View(ctx), lp(MATCH, 0, 0.4f))

        val ref = LinearLayout(ctx)
        ref.gravity = Gravity.CENTER_VERTICAL
        val minus = Chip(ctx, "−"); minus.tap { setA4(a4 - 1) }
        val plus = Chip(ctx, "+"); plus.tap { setA4(a4 + 1) }
        ref.addView(minus)
        ref.addView(refV, lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(14f), 0, ctx.dpi(14f), 0) })
        ref.addView(plus)
        col.addView(ref, lp(WRAP, WRAP))
        val play = Chip(ctx, "Escuchar el La de referencia")
        play.tap { host.synth.beep(a4.toDouble()) }
        col.addView(play, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(12f) })
        col.addView(android.view.View(ctx), lp(MATCH, 0, 0.3f))
        setA4(a4)
        showIdle()
    }

    private fun setA4(v: Int) {
        a4 = v.coerceIn(430, 450)
        host.prefs.edit().putInt("a4", a4).apply()
        refV.text = "La = $a4 Hz"
    }

    private fun showIdle() {
        noteV.text = "-"
        octV.text = ""
        freqV.text = ""
        writtenV.text = ""
        bar.live = false
        statusV.text = if (detector != null) "Toca una nota larga y firme" else "Micrófono apagado"
    }

    fun activate() {
        active = true
        host.ensureMic { ok ->
            if (!active) return@ensureMic
            if (!ok) { statusV.text = "Falta el permiso del micrófono"; return@ensureMic }
            val d = PitchDetector({ a4.toDouble() }, { 0.008f }) { f -> onFrame(f) }
            if (d.start()) { detector = d; statusV.text = "Toca una nota larga y firme" } else statusV.text = "No se pudo abrir el micrófono"
        }
    }

    fun deactivate() {
        active = false
        detector?.stop()
        detector = null
        handler.removeCallbacks(fade)
    }

    private fun onFrame(f: PitchFrame) {
        if (!active) return
        if (!f.voiced) { handler.postDelayed(fade, 650); return }
        lastVoiced = System.currentTimeMillis()
        val nearest = Math.round(f.midi)
        noteV.text = Names.pitchClass(nearest)
        octV.text = (Math.floorDiv(nearest, 12) - 1).toString()
        freqV.text = String.format("%.1f Hz", f.freq)
        smoothCents = if (abs(smoothCents - f.cents) > 30f) f.cents else smoothCents * 0.6f + f.cents * 0.4f
        bar.cents = smoothCents
        bar.live = true
        val c = smoothCents
        statusV.text = if (abs(c) <= 5f) "Afinado" else if (c > 0) "Alto: baja un poco (${c.toInt()} centésimas)" else "Bajo: sube un poco (${(-c).toInt()} centésimas)"
        statusV.setTextColor(if (abs(c) <= 5f) C.TEXT else C.MUTED)
        // el instrumento esta en Si bemol: la nota escrita es un tono mas alta que la que suena
        writtenV.text = "Suena " + Names.pitchClass(nearest) + ". Lo lees como " + Names.pitchClass(nearest + 2) + " en la partitura."
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, 900)
    }
}
