package com.digitalminds.artstar

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Servicios que ofrece la actividad a las pantallas. */
interface Host {
    val synth: Synth
    val prefs: SharedPreferences
    fun ensureMic(cb: (Boolean) -> Unit)
    fun toast(msg: String)
    fun closePlayer()
}

/** Tres circulos que muestran que pistones se presionan. */
class ValveView(ctx: Context) : View(ctx) {
    private var v = BooleanArray(3)
    private var known = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(f: String?) {
        known = f != null
        v = Fingering.valves(f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val r = min(h * 0.4f, width / 7f)
        val gap = width / 3f
        for (i in 0 until 3) {
            val cx = gap * i + gap / 2f
            val cy = h / 2f
            p.style = Paint.Style.FILL
            p.color = if (v[i]) C.RED_HI else C.CARD2
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.12f
            p.color = if (v[i]) C.RED_HI else C.DIM
            c.drawCircle(cx, cy, r, p)
            p.style = Paint.Style.FILL
            p.color = if (v[i]) C.TEXT else C.MUTED
            p.typeface = Fonts.bold
            p.textSize = r * 1.0f
            p.textAlign = Paint.Align.CENTER
            c.drawText((i + 1).toString(), cx, cy + r * 0.36f, p)
        }
    }
}

/** Barra de progreso tocable. */
class SeekView(ctx: Context, private val onSeek: (Float) -> Unit) : View(ctx) {
    var value = 0f
        set(x) { field = x.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        val th = context.dp(4f)
        r.set(0f, h / 2 - th / 2, w, h / 2 + th / 2)
        p.color = C.CARD2
        c.drawRoundRect(r, th, th, p)
        r.set(0f, h / 2 - th / 2, w * value, h / 2 + th / 2)
        p.color = C.RED
        c.drawRoundRect(r, th, th, p)
        p.color = C.TEXT
        c.drawCircle(w * value, h / 2, context.dp(7f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val f = (e.x / width).coerceIn(0f, 1f)
                value = f
                onSeek(f)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

class PlayerScreen(private val host: Host, private val song: Song, startPart: String, private val startMode: Int) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private val speeds = doubleArrayOf(0.5, 0.7, 0.85, 1.0, 1.15)
    private val speedNames = arrayOf("50%", "70%", "85%", "Real", "115%")

    private var part: Part = song.parts.firstOrNull { it.id == startPart } ?: song.parts[0]
    private var orderMode = 0       // 0 ejecucion, 1 hoja
    private var sectionIdx = -1
    private var loop = false
    private lateinit var stream: Stream

    private var tick = 0.0
    private var playing = false
    private var waiting = false
    private var speedIdx = host.prefs.getInt("speed", 3).coerceIn(0, 4)
    private var baseBpm = host.prefs.getInt("bpm_" + song.id, song.bpm)
    private var metro = host.prefs.getBoolean("metro", false)
    private var soundOn = host.prefs.getBoolean("sound", true)
    private var namesOn = host.prefs.getBoolean("names", song.cat == "lesson")
    private var fingerOn = host.prefs.getBoolean("finger", true)
    private var sens = host.prefs.getInt("sens", 1)
    private var mode = 0            // 0 libre, 1 espera, 2 evaluar

    // punteros del reloj
    private var evPtr = 0
    private var beatPtr = 0
    private var soundEnd = -1.0
    private var hudPtr = 0
    private var pendIdx = 0
    private var lastConsumed = -1
    private var evalPtr = 0
    private lateinit var evalCount: IntArray
    private lateinit var owner: IntArray
    private var lastNanos = 0L
    private var attached = false
    private var detector: PitchDetector? = null
    private var lastFrame: PitchFrame? = null

    // vistas
    private val score = ScoreView(ctx)
    private val titleV = label(ctx, song.title, 16f, C.TEXT, Fonts.bold)
    private val subV = label(ctx, "", 12f, C.MUTED)
    private val valve = ValveView(ctx)
    private val noteV = label(ctx, "", 22f, C.TEXT, Fonts.bold)
    private val statusV = label(ctx, "", 12f, C.MUTED, Fonts.medium)
    private val measureV = label(ctx, "", 12f, C.MUTED, Fonts.medium)
    private val seek = SeekView(ctx) { f -> seekTo(f) }
    private val playBtn = FrameLayout(ctx)
    private val playIcon = IconView(ctx, IconView.PLAY)
    private val speedChips = ArrayList<Chip>()
    private val modeChips = ArrayList<Chip>()
    private lateinit var metroChip: Chip
    private lateinit var soundChip: Chip
    private lateinit var namesChip: Chip
    private lateinit var sectionRow: LinearLayout
    private val sectionChips = ArrayList<Chip>()
    private lateinit var loopChip: Chip

    init {
        setBackgroundColor(C.BG)
        buildUi()
        rebuild(true)
        if (startMode != 0) setMode(startMode)
    }

    // ---------- construccion de la interfaz ----------
    private fun buildUi() {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(MATCH, MATCH))

        // barra superior
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(ctx.dpi(4f), 0, ctx.dpi(12f), 0)
        val back = FrameLayout(ctx)
        val bi = IconView(ctx, IconView.BACK)
        back.addView(bi, FrameLayout.LayoutParams(ctx.dpi(28f), ctx.dpi(28f), Gravity.CENTER))
        back.tap { host.closePlayer() }
        bar.addView(back, lp(ctx.dpi(44f), ctx.dpi(44f)))
        val titles = LinearLayout(ctx)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(titleV)
        subV.setPadding(0, ctx.dpi(2f), 0, 0)
        titles.addView(subV)
        bar.addView(titles, lp(0, WRAP, 1f))
        bar.addView(statusV, lp(WRAP, WRAP))
        statusV.setPadding(0, 0, ctx.dpi(14f), 0)
        bar.addView(noteV, lp(WRAP, WRAP))
        noteV.setPadding(0, 0, ctx.dpi(12f), 0)
        bar.addView(valve, lp(ctx.dpi(96f), ctx.dpi(34f)))
        root.addView(bar, lp(MATCH, ctx.dpi(46f)))

        // partitura
        score.keySig = song.key
        score.num = song.num
        score.den = song.den
        score.showNames = namesOn
        score.showFinger = fingerOn
        score.onTap = { togglePlay() }
        root.addView(score, lp(MATCH, 0, 1f))

        // progreso
        val prog = LinearLayout(ctx)
        prog.orientation = LinearLayout.HORIZONTAL
        prog.gravity = Gravity.CENTER_VERTICAL
        prog.setPadding(ctx.dpi(14f), 0, ctx.dpi(14f), 0)
        measureV.setPadding(0, 0, ctx.dpi(10f), 0)
        prog.addView(measureV, lp(ctx.dpi(92f), WRAP))
        prog.addView(seek, lp(0, ctx.dpi(26f), 1f))
        root.addView(prog, lp(MATCH, ctx.dpi(28f)))

        // controles
        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dpi(10f), ctx.dpi(4f), ctx.dpi(10f), ctx.dpi(6f))
        scroll.addView(row, FrameLayout.LayoutParams(WRAP, WRAP))
        root.addView(scroll, lp(MATCH, ctx.dpi(56f)))

        val prev = IconView(ctx, IconView.PREV)
        prev.tap { stepMeasure(-1) }
        row.addView(prev, lp(ctx.dpi(40f), ctx.dpi(40f)))
        playBtn.background = roundRect(C.RED, ctx.dp(24f))
        playBtn.addView(playIcon, FrameLayout.LayoutParams(ctx.dpi(30f), ctx.dpi(30f), Gravity.CENTER))
        playBtn.tap { togglePlay() }
        val pl = lp(ctx.dpi(64f), ctx.dpi(44f))
        pl.setMargins(ctx.dpi(4f), 0, ctx.dpi(4f), 0)
        row.addView(playBtn, pl)
        val next = IconView(ctx, IconView.NEXT)
        next.tap { stepMeasure(1) }
        row.addView(next, lp(ctx.dpi(40f), ctx.dpi(40f)))
        row.addView(sep())

        for ((i, n) in speedNames.withIndex()) {
            val ch = Chip(ctx, n)
            ch.setActive(i == speedIdx)
            ch.tap { setSpeed(i) }
            speedChips.add(ch)
            row.addView(ch, chipLp())
        }
        row.addView(sep())
        val modeNames = arrayOf("Libre", "Espera", "Evaluar")
        for ((i, n) in modeNames.withIndex()) {
            val ch = Chip(ctx, n)
            ch.setActive(i == 0)
            ch.tap { setMode(i) }
            modeChips.add(ch)
            row.addView(ch, chipLp())
        }
        row.addView(sep())
        metroChip = Chip(ctx, "Metrónomo"); metroChip.setActive(metro)
        metroChip.tap { metro = !metro; host.prefs.edit().putBoolean("metro", metro).apply(); metroChip.setActive(metro) }
        row.addView(metroChip, chipLp())
        soundChip = Chip(ctx, "Sonido"); soundChip.setActive(soundOn)
        soundChip.tap { soundOn = !soundOn; host.prefs.edit().putBoolean("sound", soundOn).apply(); soundChip.setActive(soundOn); if (!soundOn) host.synth.noteOff() }
        row.addView(soundChip, chipLp())
        namesChip = Chip(ctx, "Nombres"); namesChip.setActive(namesOn)
        namesChip.tap { namesOn = !namesOn; host.prefs.edit().putBoolean("names", namesOn).apply(); namesChip.setActive(namesOn); score.showNames = namesOn; score.invalidate() }
        row.addView(namesChip, chipLp())
        if (song.sections.isNotEmpty()) {
            row.addView(sep())
            sectionRow = LinearLayout(ctx)
            sectionRow.orientation = LinearLayout.HORIZONTAL
            val all = Chip(ctx, "Todo"); all.setActive(true)
            all.tap { selectSection(-1) }
            sectionChips.add(all)
            sectionRow.addView(all, chipLp())
            for ((i, s) in song.sections.withIndex()) {
                val ch = Chip(ctx, s.name)
                ch.tap { selectSection(i) }
                sectionChips.add(ch)
                sectionRow.addView(ch, chipLp())
            }
            row.addView(sectionRow, lp(WRAP, WRAP))
            loopChip = Chip(ctx, "Bucle"); loopChip.setActive(false)
            loopChip.tap { loop = !loop; loopChip.setActive(loop) }
            row.addView(loopChip, chipLp())
        }
        row.addView(sep())
        val gear = FrameLayout(ctx)
        gear.addView(IconView(ctx, IconView.GEAR), FrameLayout.LayoutParams(ctx.dpi(26f), ctx.dpi(26f), Gravity.CENTER))
        gear.tap { showSettings() }
        row.addView(gear, lp(ctx.dpi(40f), ctx.dpi(40f)))
    }

    private fun sep(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.CARD2)
        val l = lp(ctx.dpi(1f), ctx.dpi(26f))
        l.setMargins(ctx.dpi(8f), 0, ctx.dpi(8f), 0)
        v.layoutParams = l
        return v
    }

    private fun chipLp(): LinearLayout.LayoutParams {
        val l = lp(WRAP, WRAP)
        l.setMargins(ctx.dpi(3f), 0, ctx.dpi(3f), 0)
        return l
    }

    // ---------- flujo y estado ----------
    private fun currentOrder(): List<Int> {
        if (sectionIdx >= 0) return song.sections[sectionIdx].order
        return if (orderMode == 0) part.order else part.measures.map { it.n }
    }

    private fun rebuild(resetPos: Boolean) {
        stream = StreamBuilder.build(song, part, currentOrder())
        score.stream = stream
        owner = IntArray(stream.events.size)
        var last = 0
        for (i in stream.events.indices) { if (!stream.events[i].cont) last = i; owner[i] = last }
        evalCount = IntArray(stream.events.size)
        if (resetPos) { tick = 0.0; setPlaying(false) }
        resetPointers()
        updateHud()
    }

    private fun ticksPerSecond(): Double = baseBpm * speeds[speedIdx] * 48.0 / 60.0

    private fun resetPointers() {
        val ev = stream.events
        evPtr = ev.indices.firstOrNull { ev[it].t0 >= tick - 0.5 } ?: ev.size
        beatPtr = ceil(tick / stream.beat - 1e-9).toInt()
        soundEnd = -1.0
        host.synth.noteOff()
        val at = stream.attacks
        pendIdx = at.indices.firstOrNull { ev[at[it]].t0 >= tick - 0.5 } ?: at.size
        for (e in ev) { e.matched = false; e.hit = false }
        evalCount.fill(0)
        evalPtr = ev.indices.firstOrNull { ev[it].t1 > tick } ?: ev.size
        hudPtr = evalPtr
        lastConsumed = -1
    }

    private fun setPlaying(p: Boolean) {
        playing = p
        playIcon.let { }
        removeView(playIcon)
        val icon = IconView(ctx, if (p) IconView.PAUSE else IconView.PLAY)
        playBtn.removeAllViews()
        playBtn.addView(icon, FrameLayout.LayoutParams(ctx.dpi(30f), ctx.dpi(30f), Gravity.CENTER))
        if (!p) { host.synth.noteOff(); waiting = false }
    }

    private fun togglePlay() {
        if (playing) { setPlaying(false); return }
        if (tick >= stream.total - 1) { tick = 0.0; resetPointers() }
        if (tick <= 0.0) {
            tick = -stream.mlen.toDouble()
            resetPointers()
            if (mode == 2) { for (e in stream.events) e.hit = false }
        }
        lastNanos = 0L
        setPlaying(true)
    }

    private fun setSpeed(i: Int) {
        speedIdx = i
        host.prefs.edit().putInt("speed", i).apply()
        for ((k, c) in speedChips.withIndex()) c.setActive(k == i)
        updateHud()
    }

    private fun setMode(m: Int) {
        if (m == mode) return
        if (m == 0) {
            mode = 0
            stopDetector()
            refreshModeChips()
            return
        }
        host.ensureMic { ok ->
            if (!ok) {
                host.toast("Necesito el permiso del micrófono para este modo")
                mode = 0; stopDetector(); refreshModeChips()
            } else {
                mode = m
                if (mode != 0 && soundOn) host.synth.noteOff()
                startDetector()
                refreshModeChips()
                if (playing) { }
            }
        }
    }

    private fun refreshModeChips() {
        for ((i, c) in modeChips.withIndex()) c.setActive(i == mode)
        updateHud()
    }

    private fun selectSection(i: Int) {
        sectionIdx = i
        for ((k, c) in sectionChips.withIndex()) c.setActive(k == i + 1)
        rebuild(true)
    }

    private fun stepMeasure(d: Int) {
        val cur = floorDiv(tick.toInt(), stream.mlen)
        val target = (cur + d).coerceIn(0, stream.slots.size - 1)
        tick = (target * stream.mlen).toDouble()
        resetPointers()
        updateHud()
    }

    private fun floorDiv(a: Int, b: Int): Int = Math.floorDiv(a, b)

    private fun seekTo(f: Float) {
        val m = (f * stream.slots.size).toInt().coerceIn(0, stream.slots.size - 1)
        tick = (m * stream.mlen).toDouble()
        resetPointers()
        updateHud()
    }

    private fun sensThreshold(): Float = when (sens) { 0 -> 0.03f; 1 -> 0.012f; else -> 0.005f }

    private fun startDetector() {
        if (detector != null) return
        val d = PitchDetector({ 440.0 }, { sensThreshold() }) { f -> onPitch(f) }
        if (!d.start()) { host.toast("No se pudo abrir el micrófono"); mode = 0; refreshModeChips(); return }
        detector = d
    }

    private fun stopDetector() {
        detector?.stop()
        detector = null
        lastFrame = null
    }

    // ---------- reloj ----------
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!attached) return
            if (lastNanos == 0L) lastNanos = frameTimeNanos
            val dt = min(0.1, (frameTimeNanos - lastNanos) / 1e9)
            lastNanos = frameTimeNanos
            step(dt)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun step(dt: Double) {
        if (playing) {
            val tps = ticksPerSecond()
            var nt = tick + dt * tps
            waiting = false
            if (mode == 1) {
                val pe = pendingEv()
                if (pe != null && nt >= pe.t0 && !pe.matched) { nt = pe.t0.toDouble(); waiting = true }
            }
            trigger(nt, tps)
            tick = nt
            if (mode == 2) evalStep()
            if (tick >= stream.total) finish()
        }
        score.waiting = waiting
        score.curTick = tick.toFloat()
        updateHud()
    }

    private fun pendingEv(): Ev? {
        val at = stream.attacks
        return if (pendIdx < at.size) stream.events[at[pendIdx]] else null
    }

    private fun targetPc(e: Ev): Int = ((e.note.midi - 2) % 12 + 12) % 12

    /** Lanza sonidos y clics con una pequena anticipacion para compensar la latencia de audio. */
    private fun trigger(nt: Double, tps: Double) {
        val la = 0.05 * tps
        val synth = host.synth
        val ev = stream.events
        val melody = soundOn && mode == 0
        while (evPtr < ev.size && ev[evPtr].t0 <= nt + la) {
            val e = ev[evPtr]
            if (melody) {
                if (!e.cont) synth.noteOn(midiToFreq((e.note.midi - 2).toDouble()))
                soundEnd = if (e.note.staccato) e.t0 + e.note.dur * 0.5 else e.t1 - min(3.0, e.note.dur * 0.1)
            }
            evPtr++
        }
        if (soundEnd >= 0 && nt + la >= soundEnd) { synth.noteOff(); soundEnd = -1.0 }
        val beat = stream.beat
        while (beatPtr * beat <= nt + la) {
            val bt = beatPtr * beat
            if (metro || bt < 0) {
                val inMeasure = Math.floorMod(bt, stream.mlen)
                synth.click(if (inMeasure == 0) 2 else 1)
            }
            beatPtr++
        }
    }

    private fun finish() {
        setPlaying(false)
        val wasEval = mode == 2
        if (loop && !wasEval) {
            tick = 0.0
            resetPointers()
            lastNanos = 0L
            setPlaying(true)
            return
        }
        tick = stream.total.toDouble()
        if (wasEval) showResult()
        tick = 0.0
        resetPointers()
    }

    // ---------- micrófono ----------
    private fun onPitch(f: PitchFrame) {
        lastFrame = f
        if (!playing) return
        if (mode == 1) {
            val pe = pendingEv() ?: return
            val tol = 0.3 * ticksPerSecond()
            if (f.voiced && f.noteId != lastConsumed && f.pc == targetPc(pe) && tick >= pe.t0 - tol) {
                pe.matched = true
                lastConsumed = f.noteId
                pendIdx++
            }
        } else if (mode == 2) {
            if (!f.voiced || tick < 0) return
            val tps = ticksPerSecond()
            val before = 0.10 * tps
            val after = 0.15 * tps
            val ev = stream.events
            var i = evalPtr
            while (i < ev.size && ev[i].t0 - before <= tick) {
                val e = ev[i]
                if (e.t1 + after > tick && f.pc == targetPc(e)) {
                    val o = owner[i]
                    evalCount[o]++
                    val need = if (stream.events[o].t1 - stream.events[o].t0 < 0.22 * tps) 1 else 2
                    if (evalCount[o] >= need) stream.events[o].hit = true
                }
                i++
            }
        }
    }

    private fun evalStep() {
        val ev = stream.events
        val after = 0.15 * ticksPerSecond()
        while (evalPtr < ev.size && ev[evalPtr].t1 + after < tick) evalPtr++
    }

    private fun showResult() {
        var total = 0; var hits = 0
        for (i in stream.attacks) { total++; if (stream.events[i].hit) hits++ }
        val pct = if (total == 0) 0 else hits * 100 / total
        val stars = if (pct >= 90) 3 else if (pct >= 70) 2 else if (pct >= 40) 1 else 0
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(ctx.dpi(28f), ctx.dpi(22f), ctx.dpi(28f), ctx.dpi(20f))
        box.background = roundRect(C.CARD, ctx.dp(22f))
        val star = StringBuilder()
        for (i in 0 until 3) star.append(if (i < stars) "★ " else "☆ ")
        val s = label(ctx, star.toString().trim(), 34f, if (stars > 0) C.RED_HI else C.DIM, Fonts.bold)
        box.addView(s)
        val t = label(ctx, "$hits de $total notas correctas ($pct%)", 17f, C.TEXT, Fonts.bold)
        t.setPadding(0, ctx.dpi(10f), 0, ctx.dpi(4f))
        box.addView(t)
        val msg = when (stars) {
            3 -> "Excelente. Sube la velocidad para el siguiente reto."
            2 -> "Muy bien. Repite las partes donde fallaste."
            1 -> "Vas avanzando. Practica más despacio y vuelve a intentar."
            else -> "Prueba con una velocidad menor o en modo Espera."
        }
        val m = label(ctx, msg, 13f, C.MUTED)
        m.gravity = Gravity.CENTER
        box.addView(m)
        val ok = Chip(ctx, "Cerrar")
        ok.setActive(true)
        ok.tap { d.dismiss() }
        val ol = lp(WRAP, WRAP); ol.topMargin = ctx.dpi(16f)
        box.addView(ok, ol)
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        d.show()
    }

    // ---------- ajustes ----------
    private fun showSettings() {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ctx.dpi(22f), ctx.dpi(18f), ctx.dpi(22f), ctx.dpi(18f))
        box.background = roundRect(C.CARD, ctx.dp(22f))
        sv.addView(box)

        fun title(t: String) { val l = label(ctx, t, 13f, C.MUTED, Fonts.medium); l.setPadding(0, ctx.dpi(12f), 0, ctx.dpi(6f)); box.addView(l) }

        box.addView(label(ctx, "Ajustes", 18f, C.TEXT, Fonts.bold))

        // tempo base
        title("Tempo base de la pieza")
        val tr = LinearLayout(ctx); tr.gravity = Gravity.CENTER_VERTICAL
        val bpmL = label(ctx, "$baseBpm negras por minuto", 15f, C.TEXT, Fonts.medium)
        fun bump(dv: Int) {
            baseBpm = (baseBpm + dv).coerceIn(30, 220)
            host.prefs.edit().putInt("bpm_" + song.id, baseBpm).apply()
            bpmL.text = "$baseBpm negras por minuto"
        }
        val minus = Chip(ctx, "−"); minus.tap { bump(-2) }
        val plus = Chip(ctx, "+"); plus.tap { bump(2) }
        tr.addView(minus); tr.addView(bpmL, lp(0, WRAP, 1f).also { it.setMargins(ctx.dpi(14f), 0, ctx.dpi(14f), 0) }); tr.addView(plus)
        box.addView(tr)
        val officialL = label(ctx, "Tempo oficial: ${song.bpm}", 12f, C.DIM)
        officialL.setPadding(0, ctx.dpi(4f), 0, 0)
        box.addView(officialL)
        val reset = Chip(ctx, "Volver al oficial")
        reset.tap { baseBpm = song.bpm; host.prefs.edit().putInt("bpm_" + song.id, baseBpm).apply(); bpmL.text = "$baseBpm negras por minuto" }
        val rl = lp(WRAP, WRAP); rl.topMargin = ctx.dpi(6f)
        box.addView(reset, rl)

        if (song.parts.size > 1) {
            title("Parte")
            val pr = LinearLayout(ctx)
            val chips = ArrayList<Chip>()
            for (p in song.parts) {
                val ch = Chip(ctx, p.name); ch.setActive(p.id == part.id)
                ch.tap {
                    part = p
                    for (c in chips) c.setActive(false)
                    ch.setActive(true)
                    rebuild(true)
                    subV.text = ""
                }
                chips.add(ch); pr.addView(ch, chipLp())
            }
            box.addView(pr)
        }

        if (song.cat == "song") {
            title("Orden de ejecución")
            val orow = LinearLayout(ctx)
            val a = Chip(ctx, "Con repeticiones y saltos"); val b = Chip(ctx, "Como está en la hoja")
            a.setActive(orderMode == 0); b.setActive(orderMode == 1)
            a.tap { orderMode = 0; a.setActive(true); b.setActive(false); rebuild(true) }
            b.tap { orderMode = 1; a.setActive(false); b.setActive(true); rebuild(true) }
            orow.addView(a, chipLp()); orow.addView(b, chipLp())
            box.addView(orow)
        }

        title("Sensibilidad del micrófono")
        val sr = LinearLayout(ctx)
        val sc = ArrayList<Chip>()
        for ((i, n) in arrayOf("Baja", "Media", "Alta").withIndex()) {
            val ch = Chip(ctx, n); ch.setActive(i == sens)
            ch.tap { sens = i; host.prefs.edit().putInt("sens", i).apply(); for ((k, c) in sc.withIndex()) c.setActive(k == i) }
            sc.add(ch); sr.addView(ch, chipLp())
        }
        box.addView(sr)
        val hint = label(ctx, "Sube la sensibilidad si no te detecta. Bájala si hay mucho ruido. Usa audífonos si activas el sonido de la melodía.", 12f, C.DIM)
        hint.setPadding(0, ctx.dpi(6f), 0, 0)
        box.addView(hint)

        title("Pistones")
        val fr = LinearLayout(ctx)
        val fc = Chip(ctx, "Mostrar números sobre las notas"); fc.setActive(fingerOn)
        fc.tap { fingerOn = !fingerOn; host.prefs.edit().putBoolean("finger", fingerOn).apply(); fc.setActive(fingerOn); score.showFinger = fingerOn; score.invalidate() }
        fr.addView(fc, chipLp())
        box.addView(fr)

        val close = Chip(ctx, "Listo"); close.setActive(true)
        close.tap { d.dismiss() }
        val cl = lp(WRAP, WRAP); cl.topMargin = ctx.dpi(18f); cl.gravity = Gravity.END
        box.addView(close, cl)

        d.setContentView(sv)
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.62f).toInt(), (resources.displayMetrics.heightPixels * 0.92f).toInt())
        d.show()
    }

    // ---------- HUD ----------
    private fun updateHud() {
        val eff = (baseBpm * speeds[speedIdx]).toInt()
        setIf(subV, part.name + " · " + eff + " bpm")
        val slotIdx = if (tick < 0) 0 else min(stream.slots.size - 1, (tick / stream.mlen).toInt())
        val m = stream.slots[slotIdx].m
        setIf(measureV, if (tick < 0) "Preparado" else "Compás ${m.n}")
        seek.value = if (stream.total == 0) 0f else (max(0.0, tick) / stream.total).toFloat()

        // nota actual o siguiente
        val ev = stream.events
        while (hudPtr < ev.size && ev[hudPtr].t1 <= tick) hudPtr++
        val e = if (hudPtr < ev.size) ev[hudPtr] else null
        if (e != null) {
            valve.set(Fingering.forMidi(e.note.midi))
            setIf(noteV, Names.ofNote(e.note))
        }
        setIf(statusV, when {
            waiting -> "Toca la nota para avanzar"
            mode == 1 && detector != null -> "Espera: " + heard()
            mode == 2 && detector != null -> "Evaluando: " + heard()
            else -> ""
        })
        statusV.setTextColor(if (waiting) C.RED_HI else C.MUTED)
    }

    private fun setIf(tv: TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }

    private fun heard(): String {
        val f = lastFrame
        return if (f != null && f.voiced) Names.pitchClass(Math.round(f.midi)) else "..."
    }

    // ---------- ciclo de vida ----------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    override fun onDetachedFromWindow() {
        attached = false
        release()
        super.onDetachedFromWindow()
    }

    fun pause() {
        if (playing) setPlaying(false)
    }

    fun release() {
        host.synth.noteOff()
        host.synth.silence()
        stopDetector()
    }
}
