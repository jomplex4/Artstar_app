package com.digitalminds.artstar

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.PopupWindow
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

interface Host {
    val synth: Synth
    val prefs: SharedPreferences
    fun ensureMic(cb: (Boolean) -> Unit)
    fun toast(msg: String)
    fun closePlayer()
}

/** Percusion de marcha: 3 formas de tocar por instrumento, escritas sobre una celda de 2 tiempos en semicorcheas. */
object Band {
    val drums = arrayOf("Bombo", "Tarola", "Napoleón", "Platillo", "Pandereta")
    val types = intArrayOf(Synth.BOMBO, Synth.TAROLA, Synth.NAPOLEON, Synth.PLATILLO, Synth.PANDERETA)
    val styles = arrayOf(
        arrayOf("1 y 2", "Tiempo fuerte", "Marcha doble"),
        arrayOf("Ta-ra-ta-ta", "Contratiempo", "Redoble"),
        arrayOf("Básico", "Galope", "Contratiempo"),
        arrayOf("Choque en 2", "Con el bombo", "Cada 2 compases"),
        arrayOf("Contratiempos", "Sacudido", "Golpe en 2")
    )
    // 0 nada, 1 suave, 2 normal, 3 acento, 4 redoble, 5 acento solo cada 2 compases
    val patterns = arrayOf(
        arrayOf(intArrayOf(3, 0, 0, 0, 2, 0, 0, 0), intArrayOf(3, 0, 0, 0, 0, 0, 0, 0), intArrayOf(3, 0, 0, 0, 2, 0, 2, 0)),
        arrayOf(intArrayOf(3, 0, 1, 1, 2, 0, 1, 1), intArrayOf(0, 0, 3, 0, 0, 0, 3, 0), intArrayOf(3, 0, 0, 0, 4, 4, 3, 0)),
        arrayOf(intArrayOf(3, 0, 2, 0, 3, 0, 0, 0), intArrayOf(3, 0, 2, 2, 3, 0, 2, 2), intArrayOf(0, 0, 3, 0, 0, 0, 3, 0)),
        arrayOf(intArrayOf(0, 0, 0, 0, 3, 0, 0, 0), intArrayOf(3, 0, 0, 0, 2, 0, 0, 0), intArrayOf(5, 0, 0, 0, 0, 0, 0, 0)),
        arrayOf(intArrayOf(0, 0, 2, 0, 0, 0, 2, 0), intArrayOf(2, 1, 2, 1, 2, 1, 2, 1), intArrayOf(0, 0, 0, 0, 3, 0, 0, 0))
    )
    val liraStyles = arrayOf("Melodía", "Solo tiempos", "Octava alta")
}

/** Menu desplegable anclado a un boton. Se abre hacia arriba cuando el boton esta en la parte baja de la pantalla. */
fun dropdown(anchor: View, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val ctx = anchor.context
    val box = LinearLayout(ctx)
    box.orientation = LinearLayout.VERTICAL
    box.background = roundRect(C.CARD, ctx.dp(16f), C.CARD2, ctx.dpi(1f))
    box.setPadding(ctx.dpi(6f), ctx.dpi(6f), ctx.dpi(6f), ctx.dpi(6f))
    val pop = PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    pop.elevation = ctx.dp(8f)
    for ((i, t) in items.withIndex()) {
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(ctx.dpi(14f), ctx.dpi(11f), ctx.dpi(18f), ctx.dpi(11f))
        if (i == selected) row.background = roundRect(C.RED, ctx.dp(12f))
        val tv = label(ctx, t, 15f, if (i == selected) C.TEXT else C.MUTED, if (i == selected) Fonts.bold else Fonts.medium)
        row.addView(tv)
        row.tap { pop.dismiss(); onPick(i) }
        box.addView(row, lp(MATCH, WRAP))
    }
    box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val loc = IntArray(2)
    anchor.getLocationOnScreen(loc)
    val dm = ctx.resources.displayMetrics
    val up = loc[1] > dm.heightPixels / 2
    val yoff = if (up) -(box.measuredHeight + anchor.height + ctx.dpi(4f)) else ctx.dpi(4f)
    pop.showAsDropDown(anchor, 0, yoff)
}

/** Boton que muestra la opcion actual y abre un menu al tocarlo. */
class MenuButton(ctx: Context, private val title: String) : LinearLayout(ctx) {
    private val lab = label(ctx, "", 13f, C.TEXT, Fonts.medium)
    private val arrow = IconView(ctx, IconView.CHEVRON_DOWN)

    init {
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(C.CARD2, ctx.dp(20f))
        setPadding(ctx.dpi(14f), ctx.dpi(9f), ctx.dpi(8f), ctx.dpi(9f))
        addView(label(ctx, "$title ", 12f, C.MUTED, Fonts.medium))
        addView(lab)
        addView(arrow, lp(ctx.dpi(20f), ctx.dpi(20f)))
    }

    fun setText(t: String) { lab.text = t }
}

class PlayerScreen(private val host: Host, private val song: Song, startPart: String, private val startMode: Int) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private val prefs = host.prefs
    private val speeds = doubleArrayOf(0.5, 0.7, 0.85, 1.0, 1.1)

    private var part: Part = song.parts.firstOrNull { it.id == startPart } ?: song.parts[0]
    private var orderMode = 0
    private var sectionIdx = -1
    private var loop = false
    private lateinit var stream: Stream

    private class Acc(val part: Part, val stream: Stream, val ch: Int) { var ptr = 0; var end = -1.0 }
    private val accs = ArrayList<Acc>()
    private var lira: Acc? = null

    private var tick = 0.0
    private var playing = false
    private var waiting = false
    private var speedIdx = prefs.getInt("speed", 3).coerceIn(0, 4)
    private var baseBpm = prefs.getInt("bpm_" + song.id, song.bpm)
    private var metro = prefs.getBoolean("metro", false)
    private var guide = prefs.getBoolean("guide", true)
    private var namesOn = prefs.getBoolean("names", song.cat == "lesson")
    private var fingerOn = prefs.getBoolean("finger", true)
    private var sens = prefs.getInt("sens", 1)
    private var mode = 0
    private val isSong = song.cat == "song"
    private var accOn = prefs.getBoolean("acc", true)
    private var accVol = prefs.getInt("accvol", 1)
    private val drumSel = IntArray(5) { prefs.getInt("drum$it", if (isSong && (it == 0 || it == 1 || it == 3)) 1 else 0) }
    private var liraSel = prefs.getInt("lira", 0)

    private var evPtr = 0
    private var beatPtr = 0
    private var gridPtr = 0
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

    private val score = ScoreView(ctx)
    private val titleV = label(ctx, song.title, 16f, C.TEXT, Fonts.bold)
    private val subV = label(ctx, "", 12f, C.MUTED)
    private val finger = FingerView(ctx)
    private val noteV = label(ctx, "", 20f, C.TEXT, Fonts.bold)
    private val statusV = label(ctx, "", 12f, C.MUTED, Fonts.medium)
    private val measureV = label(ctx, "", 12f, C.MUTED, Fonts.medium)
    private val seek = SeekView(ctx) { f -> seekTo(f) }
    private val playBtn = FrameLayout(ctx)
    private lateinit var speedBtn: MenuButton
    private lateinit var modeBtn: MenuButton
    private lateinit var guideChip: Chip
    private lateinit var metroChip: Chip

    init {
        setBackgroundColor(C.BG)
        buildUi()
        rebuild(true)
        if (startMode != 0) setMode(startMode)
    }

    // ---------------- interfaz ----------------
    private fun buildUi() {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(MATCH, MATCH))

        val bar = LinearLayout(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(ctx.dpi(4f), 0, ctx.dpi(10f), 0)
        val back = FrameLayout(ctx)
        back.addView(IconView(ctx, IconView.BACK), FrameLayout.LayoutParams(ctx.dpi(28f), ctx.dpi(28f), Gravity.CENTER))
        back.tap { host.closePlayer() }
        bar.addView(back, lp(ctx.dpi(44f), ctx.dpi(44f)))
        val titles = LinearLayout(ctx)
        titles.orientation = LinearLayout.VERTICAL
        titleV.maxLines = 1
        titles.addView(titleV)
        subV.setPadding(0, ctx.dpi(2f), 0, 0)
        subV.maxLines = 1
        titles.addView(subV)
        bar.addView(titles, lp(0, WRAP, 1f))
        statusV.maxLines = 1
        bar.addView(statusV, lp(WRAP, WRAP).also { it.marginEnd = ctx.dpi(8f) })
        guideChip = Chip(ctx, "Guía"); guideChip.setActive(guide)
        guideChip.tap { guide = !guide; prefs.edit().putBoolean("guide", guide).apply(); guideChip.setActive(guide); if (!guide) host.synth.noteOff(0) }
        bar.addView(guideChip, chipLp())
        metroChip = Chip(ctx, "Metrónomo"); metroChip.setActive(metro)
        metroChip.tap { metro = !metro; prefs.edit().putBoolean("metro", metro).apply(); metroChip.setActive(metro) }
        bar.addView(metroChip, chipLp())
        noteV.gravity = Gravity.END
        bar.addView(noteV, lp(ctx.dpi(66f), WRAP).also { it.marginEnd = ctx.dpi(8f) })
        bar.addView(finger, lp(fingerWidth(), ctx.dpi(40f)))
        root.addView(bar, lp(MATCH, ctx.dpi(48f)))

        score.bass = part.inst.bass
        score.keySig = part.key
        score.num = song.num
        score.den = song.den
        score.inst = part.inst
        score.showNames = namesOn
        score.showFinger = fingerOn
        score.onTap = { togglePlay() }
        root.addView(score, lp(MATCH, 0, 1f))

        val prog = LinearLayout(ctx)
        prog.gravity = Gravity.CENTER_VERTICAL
        prog.setPadding(ctx.dpi(14f), 0, ctx.dpi(10f), 0)
        prog.addView(measureV, lp(ctx.dpi(92f), WRAP))
        prog.addView(seek, lp(0, ctx.dpi(26f), 1f))
        root.addView(prog, lp(MATCH, ctx.dpi(28f)))

        val scroll = HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.isFillViewport = true
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER
        row.setPadding(ctx.dpi(8f), ctx.dpi(2f), ctx.dpi(8f), ctx.dpi(6f))
        scroll.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, lp(MATCH, ctx.dpi(54f)))

        row.addView(IconView(ctx, IconView.PREV).also { it.tap { stepMeasure(-1) } }, lp(ctx.dpi(38f), ctx.dpi(38f)))
        playBtn.background = roundRect(C.RED, ctx.dp(22f))
        row.addView(playBtn, lp(ctx.dpi(60f), ctx.dpi(42f)).also { it.setMargins(ctx.dpi(4f), 0, ctx.dpi(4f), 0) })
        playBtn.tap { togglePlay() }
        drawPlay()
        row.addView(IconView(ctx, IconView.NEXT).also { it.tap { stepMeasure(1) } }, lp(ctx.dpi(38f), ctx.dpi(38f)))
        row.addView(sep())
        speedBtn = MenuButton(ctx, "Velocidad")
        speedBtn.tap { dropdown(speedBtn, speedItems(), speedIdx) { setSpeed(it) } }
        row.addView(speedBtn, chipLp())
        modeBtn = MenuButton(ctx, "Modo")
        modeBtn.tap { dropdown(modeBtn, listOf("Libre", "Espera (micrófono)", "Evaluar (micrófono)"), mode) { setMode(it) } }
        row.addView(modeBtn, chipLp())
        refreshSpeedChips()
        refreshModeChips()
        row.addView(sep())
        val band = LinearLayout(ctx)
        band.gravity = Gravity.CENTER_VERTICAL
        band.background = roundRect(C.CARD2, ctx.dp(20f))
        band.setPadding(ctx.dpi(10f), ctx.dpi(4f), ctx.dpi(12f), ctx.dpi(4f))
        band.addView(IconView(ctx, IconView.DRUM).also { it.color = C.TEXT }, lp(ctx.dpi(24f), ctx.dpi(24f)))
        band.addView(label(ctx, "Banda", 13f, C.TEXT, Fonts.medium).also { it.setPadding(ctx.dpi(6f), 0, 0, 0) })
        band.tap { showBand() }
        row.addView(band, chipLp())
        val gear = FrameLayout(ctx)
        gear.addView(IconView(ctx, IconView.GEAR), FrameLayout.LayoutParams(ctx.dpi(26f), ctx.dpi(26f), Gravity.CENTER))
        gear.tap { showSettings() }
        row.addView(gear, lp(ctx.dpi(42f), ctx.dpi(42f)))
    }

    private fun fingerWidth(): Int = when (part.inst.fam) { 1 -> ctx.dpi(170f); 3 -> ctx.dpi(150f); else -> ctx.dpi(96f) }

    private fun sep(): View {
        val v = View(ctx)
        v.setBackgroundColor(C.CARD2)
        v.layoutParams = lp(ctx.dpi(1f), ctx.dpi(24f)).also { it.setMargins(ctx.dpi(6f), 0, ctx.dpi(6f), 0) }
        return v
    }

    private fun chipLp(): LinearLayout.LayoutParams = lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(2f), 0, ctx.dpi(2f), 0) }

    private fun drawPlay() {
        playBtn.removeAllViews()
        playBtn.addView(IconView(ctx, if (playing) IconView.PAUSE else IconView.PLAY), FrameLayout.LayoutParams(ctx.dpi(28f), ctx.dpi(28f), Gravity.CENTER))
    }

    private fun speedItems(): List<String> = speeds.indices.map { i ->
        val bpm = Math.round(baseBpm * speeds[i]).toInt()
        (if (i == 3) "Real" else "${(speeds[i] * 100).toInt()} %") + "  ·  $bpm bpm"
    }

    private fun refreshSpeedChips() {
        val bpm = Math.round(baseBpm * speeds[speedIdx]).toInt()
        speedBtn.setText(if (speedIdx == 3) "Real $bpm" else "$bpm bpm")
    }

    // ---------------- flujo ----------------
    private fun currentOrder(): List<Int> {
        if (sectionIdx >= 0) return song.sections[sectionIdx].order
        return if (orderMode == 0) part.order else part.measures.map { it.n }
    }

    private fun melodyPart(): Part? {
        for (id in arrayOf("tpt1", "alto1", "bar1", "bajo")) song.parts.firstOrNull { it.id == id }?.let { return it }
        return song.parts.firstOrNull()
    }

    private fun rebuild(resetPos: Boolean) {
        val order = currentOrder()
        stream = StreamBuilder.build(song, part, order)
        score.stream = stream
        accs.clear()
        var ch = 1
        for (p in song.parts) if (p !== part && ch < 12) { accs.add(Acc(p, StreamBuilder.build(song, p, order), ch)); ch++ }
        lira = melodyPart()?.let { Acc(it, StreamBuilder.build(song, it, order), 14) }
        owner = IntArray(stream.events.size)
        var last = 0
        for (i in stream.events.indices) { if (!stream.events[i].cont) last = i; owner[i] = last }
        evalCount = IntArray(stream.events.size)
        if (resetPos) { tick = 0.0; setPlaying(false) }
        resetPointers()
        updateHud()
    }

    private fun ticksPerSecond(): Double = baseBpm * speeds[speedIdx] * 48.0 / 60.0

    private fun firstAt(s: Stream, t: Double): Int = s.events.indices.firstOrNull { s.events[it].t0 >= t - 0.5 } ?: s.events.size

    private fun resetPointers() {
        val ev = stream.events
        evPtr = firstAt(stream, tick)
        for (a in accs) { a.ptr = firstAt(a.stream, tick); a.end = -1.0 }
        lira?.let { it.ptr = firstAt(it.stream, tick); it.end = -1.0 }
        beatPtr = ceil(tick / stream.beat - 1e-9).toInt()
        gridPtr = ceil(tick / 12.0 - 1e-9).toInt()
        soundEnd = -1.0
        host.synth.allOff()
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
        drawPlay()
        if (!p) { host.synth.allOff(); waiting = false }
    }

    private fun togglePlay() {
        if (playing) { setPlaying(false); return }
        if (tick >= stream.total - 1) { tick = 0.0; resetPointers() }
        if (tick <= 0.0) { tick = -stream.mlen.toDouble(); resetPointers() }
        lastNanos = 0L
        setPlaying(true)
    }

    private fun setSpeed(i: Int) {
        speedIdx = i
        prefs.edit().putInt("speed", i).apply()
        refreshSpeedChips()
        updateHud()
    }

    private fun setMode(m: Int) {
        if (m == mode) return
        if (m == 0) { mode = 0; stopDetector(); refreshModeChips(); return }
        host.ensureMic { ok ->
            if (!ok) { host.toast("Necesito el permiso del micrófono para este modo"); mode = 0; stopDetector() }
            else { mode = m; host.synth.allOff(); startDetector() }
            refreshModeChips()
        }
    }

    private fun refreshModeChips() {
        modeBtn.setText(arrayOf("Libre", "Espera", "Evaluar")[mode])
        updateHud()
    }

    private fun stepMeasure(d: Int) {
        val cur = Math.floorDiv(max(0.0, tick).toInt(), stream.mlen)
        tick = ((cur + d).coerceIn(0, stream.slots.size - 1) * stream.mlen).toDouble()
        resetPointers(); updateHud()
    }

    private fun seekTo(f: Float) {
        val m = (f * stream.slots.size).toInt().coerceIn(0, stream.slots.size - 1)
        tick = (m * stream.mlen).toDouble()
        resetPointers(); updateHud()
    }

    private fun sensThreshold(): Float = when (sens) { 0 -> 0.03f; 1 -> 0.012f; else -> 0.005f }

    private fun startDetector() {
        if (detector != null) return
        val d = PitchDetector({ prefs.getInt("a4", 440).toDouble() }, { sensThreshold() }) { f -> onPitch(f) }
        if (!d.start()) { host.toast("No se pudo abrir el micrófono"); mode = 0; refreshModeChips(); return }
        detector = d
    }

    private fun stopDetector() { detector?.stop(); detector = null; lastFrame = null }

    // ---------------- reloj ----------------
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

    private fun pendingEv(): Ev? { val at = stream.attacks; return if (pendIdx < at.size) stream.events[at[pendIdx]] else null }

    private fun targetPc(e: Ev): Int = ((e.note.midi + part.inst.transpose) % 12 + 12) % 12

    private fun playLine(a: Acc, nt: Double, la: Double, inst: InstDef, vel: Float, liraMode: Int) {
        val ev = a.stream.events
        val synth = host.synth
        while (a.ptr < ev.size && ev[a.ptr].t0 <= nt + la) {
            val e = ev[a.ptr]
            var midi = e.note.midi + inst.transpose
            var play = !e.cont
            if (liraMode > 0) {
                midi = e.note.midi + a.part.inst.transpose
                while (midi < 72) midi += 12
                if (liraMode == 3) midi += 12
                if (liraMode == 2 && Math.floorMod(e.t0, stream.beat) != 0) play = false
            }
            if (play) synth.noteOn(a.ch, if (liraMode > 0) Insts.glock.sample else inst.sample, midi, vel)
            a.end = if (liraMode > 0) e.t0 + min(e.note.dur.toDouble(), 48.0) else if (e.note.staccato) e.t0 + e.note.dur * 0.5 else e.t1 - min(3.0, e.note.dur * 0.1)
            a.ptr++
        }
        if (a.end >= 0 && nt + la >= a.end) { synth.noteOff(a.ch); a.end = -1.0 }
    }

    /** Sonidos con una pequena anticipacion para compensar la latencia de audio. */
    private fun trigger(nt: Double, tps: Double) {
        val la = 0.05 * tps
        val synth = host.synth
        val ev = stream.events
        val micMode = mode != 0
        while (evPtr < ev.size && ev[evPtr].t0 <= nt + la) {
            val e = ev[evPtr]
            if (guide && !micMode) {
                if (!e.cont) synth.noteOn(0, part.inst.sample, e.note.midi + part.inst.transpose, 0.95f)
                soundEnd = if (e.note.staccato) e.t0 + e.note.dur * 0.5 else e.t1 - min(3.0, e.note.dur * 0.1)
            }
            evPtr++
        }
        if (soundEnd >= 0 && nt + la >= soundEnd) { synth.noteOff(0); soundEnd = -1.0 }
        if (accOn && !micMode && tick >= 0) {
            val v = when (accVol) { 0 -> 0.3f; 1 -> 0.5f; else -> 0.75f }
            for (a in accs) playLine(a, nt, la, a.part.inst, v, 0)
        }
        if (liraSel > 0 && mode != 1 && tick >= 0) lira?.let { playLine(it, nt, la, Insts.glock, 0.55f, liraSel) }
        val beat = stream.beat
        while (beatPtr * beat <= nt + la) {
            val bt = beatPtr * beat
            if (metro || bt < 0) synth.click(if (Math.floorMod(bt, stream.mlen) == 0) 2 else 1)
            beatPtr++
        }
        val compound = song.den == 8
        while (gridPtr * 12 <= nt + la) {
            val gt = gridPtr * 12
            if (gt >= 0 && !compound && mode != 1) {
                val pos = Math.floorMod(gt, stream.mlen) / 12
                val measure = gt / stream.mlen
                for (i in 0 until 5) {
                    val sel = drumSel[i]
                    if (sel == 0) continue
                    val v = Band.patterns[i][sel - 1][pos % 8]
                    when {
                        v == 0 -> {}
                        v == 5 -> if (pos == 0 && measure % 2 == 0) synth.drum(Band.types[i], 1f)
                        v == 4 -> synth.drum(Band.types[i], 0.6f, true)
                        else -> synth.drum(Band.types[i], if (v == 3) 1f else if (v == 2) 0.72f else 0.45f)
                    }
                }
            }
            gridPtr++
        }
    }

    private fun finish() {
        setPlaying(false)
        val wasEval = mode == 2
        if (loop && !wasEval) { tick = 0.0; resetPointers(); lastNanos = 0L; setPlaying(true); return }
        if (wasEval) showResult()
        tick = 0.0
        resetPointers()
    }

    // ---------------- microfono ----------------
    private fun onPitch(f: PitchFrame) {
        lastFrame = f
        if (!playing) return
        if (mode == 1) {
            val pe = pendingEv() ?: return
            val tol = 0.3 * ticksPerSecond()
            if (f.voiced && f.noteId != lastConsumed && f.pc == targetPc(pe) && tick >= pe.t0 - tol) {
                pe.matched = true; lastConsumed = f.noteId; pendIdx++
            }
        } else if (mode == 2) {
            if (!f.voiced || tick < 0) return
            val tps = ticksPerSecond()
            val ev = stream.events
            var i = evalPtr
            while (i < ev.size && ev[i].t0 - 0.10 * tps <= tick) {
                val e = ev[i]
                if (e.t1 + 0.15 * tps > tick && f.pc == targetPc(e)) {
                    val o = owner[i]
                    evalCount[o]++
                    val need = if (ev[o].t1 - ev[o].t0 < 0.22 * tps) 1 else 2
                    if (evalCount[o] >= need) ev[o].hit = true
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

    private fun sheet(): Pair<Dialog, LinearLayout> {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ctx.dpi(22f), ctx.dpi(18f), ctx.dpi(22f), ctx.dpi(18f))
        box.background = roundRect(C.CARD, ctx.dp(22f))
        sv.addView(box)
        d.setContentView(sv)
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.7f).toInt(), (resources.displayMetrics.heightPixels * 0.92f).toInt())
        return Pair(d, box)
    }

    private fun section(box: LinearLayout, t: String) {
        val l = label(ctx, t, 13f, C.MUTED, Fonts.medium)
        l.setPadding(0, ctx.dpi(14f), 0, ctx.dpi(6f))
        box.addView(l)
    }

    private fun chipRow(box: LinearLayout, names: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val sc = HorizontalScrollView(ctx)
        sc.isHorizontalScrollBarEnabled = false
        val r = LinearLayout(ctx)
        val chips = ArrayList<Chip>()
        for ((i, n) in names.withIndex()) {
            val c = Chip(ctx, n); c.setActive(i == selected)
            c.tap { for ((k, x) in chips.withIndex()) x.setActive(k == i); onPick(i) }
            chips.add(c); r.addView(c, chipLp())
        }
        sc.addView(r)
        box.addView(sc)
    }

    private fun showResult() {
        var total = 0; var hits = 0
        for (i in stream.attacks) { total++; if (stream.events[i].hit) hits++ }
        val pct = if (total == 0) 0 else hits * 100 / total
        val stars = if (pct >= 90) 3 else if (pct >= 70) 2 else if (pct >= 40) 1 else 0
        val (d, box) = sheet()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.5f).toInt(), WRAP)
        box.gravity = Gravity.CENTER_HORIZONTAL
        val st = StringBuilder(); for (i in 0 until 3) st.append(if (i < stars) "★ " else "☆ ")
        box.addView(label(ctx, st.toString().trim(), 34f, if (stars > 0) C.RED_HI else C.DIM, Fonts.bold))
        box.addView(label(ctx, "$hits de $total notas correctas ($pct%)", 17f, C.TEXT, Fonts.bold).also { it.setPadding(0, ctx.dpi(10f), 0, ctx.dpi(4f)) })
        val msg = when (stars) { 3 -> "Excelente. Sube la velocidad para el siguiente reto."; 2 -> "Muy bien. Repite las partes donde fallaste."; 1 -> "Vas avanzando. Practica más despacio y vuelve a intentar."; else -> "Prueba con una velocidad menor o en modo Espera." }
        box.addView(label(ctx, msg, 13f, C.MUTED).also { it.gravity = Gravity.CENTER })
        val ok = Chip(ctx, "Cerrar"); ok.setActive(true); ok.tap { d.dismiss() }
        box.addView(ok, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(16f) })
        d.show()
    }

    private fun showBand() {
        val (d, box) = sheet()
        box.addView(label(ctx, "Banda", 18f, C.TEXT, Fonts.bold))
        box.addView(label(ctx, "Acompañamiento para practicar como en el desfile. En el modo Espera la banda descansa.", 12f, C.DIM).also { it.setPadding(0, ctx.dpi(4f), 0, 0) })
        if (isSong) {
            section(box, "Instrumentos de viento")
            val others = song.parts.filter { it !== part }.joinToString(", ") { it.name }
            chipRow(box, listOf("Apagado", "Encendido"), if (accOn) 1 else 0) { accOn = it == 1; prefs.edit().putBoolean("acc", accOn).apply(); if (!accOn) host.synth.allOff() }
            box.addView(label(ctx, "Suenan: $others", 12f, C.DIM).also { it.setPadding(0, ctx.dpi(6f), 0, 0) })
            section(box, "Volumen de la banda")
            chipRow(box, listOf("Bajo", "Medio", "Alto"), accVol) { accVol = it; prefs.edit().putInt("accvol", it).apply() }
        }
        if (song.den == 8) box.addView(label(ctx, "La percusión de marcha se usa en compases de 2/4, 3/4 y 4/4.", 12f, C.DIM).also { it.setPadding(0, ctx.dpi(10f), 0, 0) })
        for (i in 0 until 5) {
            section(box, Band.drums[i])
            chipRow(box, listOf("No") + Band.styles[i].toList(), drumSel[i]) { drumSel[i] = it; prefs.edit().putInt("drum$i", it).apply() }
        }
        section(box, "Lira")
        chipRow(box, listOf("No") + Band.liraStyles.toList(), liraSel) { liraSel = it; prefs.edit().putInt("lira", it).apply(); host.synth.noteOff(14) }
        val close = Chip(ctx, "Listo"); close.setActive(true); close.tap { d.dismiss() }
        box.addView(close, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(18f); it.gravity = Gravity.END })
        d.show()
    }

    private fun showSettings() {
        val (d, box) = sheet()
        box.addView(label(ctx, "Ajustes", 18f, C.TEXT, Fonts.bold))
        if (song.parts.size > 1) {
            section(box, "Parte")
            chipRow(box, song.parts.map { it.name }, song.parts.indexOf(part)) {
                part = song.parts[it]
                score.bass = part.inst.bass; score.inst = part.inst; score.keySig = part.key
                (finger.layoutParams as LinearLayout.LayoutParams).width = fingerWidth()
                finger.requestLayout()
                rebuild(true)
            }
        }
        section(box, "Tempo base")
        val tr = LinearLayout(ctx); tr.gravity = Gravity.CENTER_VERTICAL
        val bpmL = label(ctx, "$baseBpm negras por minuto", 15f, C.TEXT, Fonts.medium)
        fun bump(dv: Int) { baseBpm = (baseBpm + dv).coerceIn(30, 220); prefs.edit().putInt("bpm_" + song.id, baseBpm).apply(); bpmL.text = "$baseBpm negras por minuto"; refreshSpeedChips() }
        tr.addView(Chip(ctx, "−").also { it.tap { bump(-2) } })
        tr.addView(bpmL, lp(0, WRAP, 1f).also { it.setMargins(ctx.dpi(14f), 0, ctx.dpi(14f), 0) })
        tr.addView(Chip(ctx, "+").also { it.tap { bump(2) } })
        box.addView(tr)
        val reset = Chip(ctx, "Tempo oficial: ${song.bpm}")
        reset.tap { baseBpm = song.bpm; prefs.edit().putInt("bpm_" + song.id, baseBpm).apply(); bpmL.text = "$baseBpm negras por minuto"; refreshSpeedChips() }
        box.addView(reset, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(6f) })
        if (song.sections.isNotEmpty()) {
            section(box, "Sección para practicar")
            chipRow(box, listOf("Todo") + song.sections.map { it.name }, sectionIdx + 1) { sectionIdx = it - 1; rebuild(true) }
            chipRow(box, listOf("Una vez", "En bucle"), if (loop) 1 else 0) { loop = it == 1 }
        }
        if (isSong) {
            section(box, "Orden de ejecución")
            chipRow(box, listOf("Con repeticiones y saltos", "Como está en la hoja"), orderMode) { orderMode = it; rebuild(true) }
        }
        section(box, "Ayudas en la partitura")
        chipRow(box, listOf("Digitación: sí", "Digitación: no"), if (fingerOn) 0 else 1) { fingerOn = it == 0; prefs.edit().putBoolean("finger", fingerOn).apply(); score.showFinger = fingerOn; score.invalidate() }
        chipRow(box, listOf("Nombres: no", "Nombres: sí"), if (namesOn) 1 else 0) { namesOn = it == 1; prefs.edit().putBoolean("names", namesOn).apply(); score.showNames = namesOn; score.invalidate() }
        section(box, "Sensibilidad del micrófono")
        chipRow(box, listOf("Baja", "Media", "Alta"), sens) { sens = it; prefs.edit().putInt("sens", it).apply() }
        box.addView(label(ctx, "Súbela si no te detecta y bájala si hay mucho ruido. Para los modos con micrófono usa audífonos si quieres oír la guía.", 12f, C.DIM).also { it.setPadding(0, ctx.dpi(6f), 0, 0) })
        val close = Chip(ctx, "Listo"); close.setActive(true); close.tap { d.dismiss() }
        box.addView(close, lp(WRAP, WRAP).also { it.topMargin = ctx.dpi(18f); it.gravity = Gravity.END })
        d.show()
    }

    // ---------------- HUD ----------------
    private fun setIf(tv: TextView, s: String) { if (tv.text.toString() != s) tv.text = s }

    private fun updateHud() {
        val eff = Math.round(baseBpm * speeds[speedIdx]).toInt()
        setIf(subV, part.name + " · " + eff + " bpm")
        val slotIdx = if (tick < 0) 0 else min(stream.slots.size - 1, (tick / stream.mlen).toInt())
        setIf(measureV, if (tick < 0) "Preparado" else "Compás ${stream.slots[slotIdx].m.n}")
        seek.value = if (stream.total == 0) 0f else (max(0.0, tick) / stream.total).toFloat()
        val ev = stream.events
        while (hudPtr < ev.size && ev[hudPtr].t1 <= tick) hudPtr++
        val e = if (hudPtr < ev.size) ev[hudPtr] else null
        if (e != null) { finger.set(part.inst, e.note.midi); setIf(noteV, Names.ofNote(e.note)) }
        setIf(statusV, when {
            waiting -> "Toca la nota"
            mode != 0 && detector != null -> "Oigo: " + heard()
            else -> ""
        })
        statusV.setTextColor(if (waiting) C.RED_HI else C.MUTED)
    }

    private fun heard(): String {
        val f = lastFrame
        return if (f != null && f.voiced) Names.pitchClass(Math.round(f.midi) - part.inst.transpose) else "..."
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true; lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    override fun onDetachedFromWindow() { attached = false; release(); super.onDetachedFromWindow() }

    fun pause() { if (playing) setPlaying(false) }

    fun release() { host.synth.allOff(); stopDetector() }
}
