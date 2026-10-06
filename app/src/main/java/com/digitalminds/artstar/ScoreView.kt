package com.digitalminds.artstar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pentagrama horizontal. El tiempo avanza de derecha a izquierda: la nota que cruza la linea roja "ahora"
 * es la que debes tocar. Encima de cada nota se escribe su digitacion (pistones 0, 1, 2, 3).
 * Usa la fuente Bravura (SMuFL): el tamano de fuente es 4 espacios de pentagrama.
 */
class ScoreView(ctx: Context) : View(ctx) {
    var stream: Stream? = null
        set(v) { field = v; cache.clear(); invalidate() }
    var curTick = 0f
        set(v) { field = v; invalidate() }
    var keySig = 0
    var num = 2
    var den = 4
    var showNames = false
    var showFinger = true
    var waiting = false
    var onTap: (() -> Unit)? = null

    private var sp = 10f
    private val TOP = 10f
    private val BOTTOM = 14f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.music }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()
    private val glyphStrings = HashMap<Int, String>()

    private class NL { var up = false; var group = -1 }
    private class MLay(val nl: Array<NL>, val groups: List<IntArray>, val tuplets: List<IntArray>)

    private val cache = HashMap<Measure, MLay>()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sp = h / 22f
        cache.clear()
    }

    private val ppt get() = sp * 0.19f
    private val headW get() = sp * 1.18f
    private val stemW get() = sp * 0.13f

    private fun clefEnd(): Float = sp * (0.6f + 3.0f + abs(keySig) * 1.05f + 0.5f + 2.3f + 1.0f)
    private fun nowX(): Float = max(clefEnd() + sp * 4.5f, width * 0.28f)
    private fun xOf(t: Float): Float = nowX() + (t - curTick) * ppt
    private fun yPos(pos: Int): Float = BOTTOM * sp - pos * sp / 2f
    private fun yLine(k: Int): Float = (TOP + k) * sp

    private fun gs(code: Int): String = glyphStrings.getOrPut(code) { String(Character.toChars(code)) }

    private fun glyph(c: Canvas, code: Int, x: Float, y: Float, color: Int, scale: Float = 1f) {
        glyphPaint.color = color
        glyphPaint.textSize = 4f * sp * scale
        c.drawText(gs(code), x, y, glyphPaint)
    }

    private fun colorFor(t0: Int, t1: Int): Int =
        if (curTick >= t1) C.MUTED else if (curTick >= t0) C.RED_HI else C.TEXT

    override fun onDraw(c: Canvas) {
        c.drawColor(C.BG)
        val w = width.toFloat()
        val h = height.toFloat()
        linePaint.strokeWidth = sp * 0.11f
        linePaint.color = C.MUTED
        for (k in 0 until 5) c.drawLine(0f, yLine(k), w, yLine(k), linePaint)

        val st = stream
        val ce = clefEnd()
        val nx = nowX()
        if (st != null && st.slots.isNotEmpty()) {
            c.save()
            c.clipRect(ce, 0f, w, h)
            val tMin = curTick - (nx - ce) / ppt - st.mlen
            val tMax = curTick + (w - nx) / ppt + st.mlen
            val first = max(0, floor(tMin / st.mlen).toInt())
            val last = min(st.slots.size - 1, floor(tMax / st.mlen).toInt())
            for (i in first..last) drawSlot(c, st, st.slots[i])
            c.restore()
        }

        // panel izquierdo fijo: clave, armadura y compas
        fillPaint.color = C.BG
        c.drawRect(0f, 0f, ce, h, fillPaint)
        linePaint.color = C.MUTED
        for (k in 0 until 5) c.drawLine(0f, yLine(k), ce, yLine(k), linePaint)
        glyph(c, 0xE050, sp * 0.6f, yLine(3), C.TEXT)
        var kx = sp * 3.6f
        if (keySig > 0) {
            val order = intArrayOf(8, 5, 9, 6, 3, 7, 4)
            for (i in 0 until min(keySig, 7)) { glyph(c, 0xE262, kx, yPos(order[i]), C.TEXT); kx += sp * 1.05f }
        } else if (keySig < 0) {
            val order = intArrayOf(4, 7, 3, 6, 2, 5, 1)
            for (i in 0 until min(-keySig, 7)) { glyph(c, 0xE260, kx, yPos(order[i]), C.TEXT); kx += sp * 1.05f }
        }
        kx += sp * 0.5f
        glyph(c, 0xE080 + num, kx, yLine(1), C.TEXT)
        glyph(c, 0xE080 + den, kx, yLine(3), C.TEXT)
        // borde del panel
        linePaint.color = C.CARD2
        c.drawLine(ce, 0f, ce, h, linePaint)

        // linea "ahora"
        linePaint.strokeWidth = sp * (if (waiting) 0.3f else 0.14f)
        linePaint.color = if (waiting) C.RED_HI else (C.RED_HI and 0x00FFFFFF) or (0xB0 shl 24)
        c.drawLine(nx, sp * 0.4f, nx, h - sp * 0.4f, linePaint)
        fillPaint.color = C.RED_HI
        path.reset()
        path.moveTo(nx - sp * 0.5f, sp * 0.1f); path.lineTo(nx + sp * 0.5f, sp * 0.1f); path.lineTo(nx, sp * 0.8f); path.close()
        c.drawPath(path, fillPaint)
    }

    private fun layoutOf(slot: Slot, beat: Int): MLay {
        cache[slot.m]?.let { return it }
        val notes = slot.m.notes
        val nl = Array(notes.size) { NL() }
        val byBeat = LinkedHashMap<Int, ArrayList<Int>>()
        for (i in notes.indices) {
            val n = notes[i]
            if (n.isRest || n.nominal >= 48) continue
            byBeat.getOrPut(slot.offsets[i] / beat) { ArrayList() }.add(i)
        }
        val groups = ArrayList<IntArray>()
        for ((_, list) in byBeat) {
            if (list.size >= 2) {
                var far = list[0]
                for (i in list) if (abs(notes[i].pos - 4) > abs(notes[far].pos - 4)) far = i
                val up = notes[far].pos < 4
                val gi = groups.size
                for (i in list) { nl[i].up = up; nl[i].group = gi }
                groups.add(list.toIntArray())
            }
        }
        for (i in notes.indices) if (nl[i].group < 0) nl[i].up = notes[i].pos < 4
        val tup = ArrayList<IntArray>()
        var i = 0
        while (i < notes.size) {
            if (notes[i].triplet && i + 2 < notes.size && notes[i + 1].triplet && notes[i + 2].triplet) {
                tup.add(intArrayOf(i, i + 1, i + 2)); i += 3
            } else i++
        }
        val lay = MLay(nl, groups, tup)
        cache[slot.m] = lay
        return lay
    }

    private fun flagsOf(n: Note): Int = if (n.nominal >= 24) 1 else if (n.nominal >= 12) 2 else 3

    private fun drawSlot(c: Canvas, st: Stream, slot: Slot) {
        val mlen = st.mlen
        val x0 = xOf(slot.t0.toFloat())
        val x1 = xOf((slot.t0 + mlen).toFloat())
        val barX0 = x0 - sp
        val barX1 = x1 - sp
        val top = yLine(0)
        val bot = yLine(4)
        val prevRepeatEnd = slot.index > 0 && st.slots[slot.index - 1].repeatEnd

        // barras de compas
        linePaint.color = C.MUTED
        linePaint.strokeWidth = sp * 0.11f
        if (slot.index > 0 && !prevRepeatEnd && !slot.repeatStart) c.drawLine(barX0, top, barX0, bot, linePaint)
        if (slot.repeatStart) drawRepeat(c, barX0, true)
        if (slot.repeatEnd) drawRepeat(c, barX1, false)
        if (slot.index == st.slots.size - 1 && !slot.repeatEnd) {
            c.drawLine(barX1 - sp * 0.35f, top, barX1 - sp * 0.35f, bot, linePaint)
            fillPaint.color = C.TEXT
            c.drawRect(barX1 - sp * 0.15f, top, barX1 + sp * 0.2f, bot, fillPaint)
        }

        // fila superior: numero de compas, casillas, letras de ensayo y textos
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Fonts.regular
        textPaint.textSize = sp * 1.05f
        textPaint.color = C.DIM
        c.drawText(slot.m.n.toString(), barX0 + sp * 0.4f, sp * 1.3f, textPaint)

        var labelX = barX0 + sp * 0.4f
        if (slot.voltaStart != 0) {
            val vx1 = barX0 + slot.voltaLen * mlen * ppt - sp * 0.5f
            linePaint.color = C.TEXT
            linePaint.strokeWidth = sp * 0.1f
            c.drawLine(barX0 + sp * 0.2f, sp * 1.9f, vx1, sp * 1.9f, linePaint)
            c.drawLine(barX0 + sp * 0.2f, sp * 1.9f, barX0 + sp * 0.2f, sp * 3.4f, linePaint)
            textPaint.typeface = Fonts.medium
            textPaint.textSize = sp * 1.3f
            textPaint.color = C.TEXT
            c.drawText(slot.voltaStart.toString() + ".", barX0 + sp * 0.6f, sp * 3.3f, textPaint)
            labelX += sp * 2.4f
        }
        if (slot.m.segno) { glyph(c, 0xE047, labelX, sp * 3.5f, C.TEXT, 0.8f); labelX += sp * 2.6f }
        if (slot.m.coda) { glyph(c, 0xE048, labelX, sp * 3.5f, C.TEXT, 0.8f); labelX += sp * 2.8f }
        if (slot.m.rehearsal.isNotEmpty()) {
            textPaint.typeface = Fonts.bold
            textPaint.textSize = sp * 1.5f
            val tw = textPaint.measureText(slot.m.rehearsal)
            linePaint.color = C.TEXT
            linePaint.strokeWidth = sp * 0.1f
            rect.set(labelX, sp * 1.5f, labelX + tw + sp * 0.9f, sp * 3.5f)
            c.drawRect(rect, linePaint)
            textPaint.color = C.TEXT
            c.drawText(slot.m.rehearsal, labelX + sp * 0.45f, sp * 3.15f, textPaint)
            labelX += tw + sp * 1.6f
        }
        if (slot.m.text.isNotEmpty()) {
            textPaint.typeface = Fonts.bold
            textPaint.textSize = sp * 1.3f
            textPaint.color = C.TEXT
            c.drawText(slot.m.text, labelX, sp * 3.3f, textPaint)
        }

        val lay = layoutOf(slot, st.beat)
        val notes = slot.m.notes
        val ce = clefEnd()

        // notas y silencios
        for (i in notes.indices) {
            val n = notes[i]
            val t = slot.t0 + slot.offsets[i]
            val cx = xOf(t.toFloat())
            if (cx < ce - sp * 4f || cx > width + sp * 4f) continue
            if (n.isRest) {
                val rc = if (n.fullRest) (barX0 + barX1) / 2f else cx
                drawRest(c, n, rc, colorFor(t, t + n.dur))
                continue
            }
            val ev = st.events[slot.evIdx[i]]
            val col = colorFor(ev.t0, ev.t1)
            val y = yPos(n.pos)
            val nlay = lay.nl[i]
            // lineas adicionales
            linePaint.color = C.MUTED
            linePaint.strokeWidth = sp * 0.11f
            if (n.pos <= -2) {
                var p = -2
                while (p >= n.pos) { c.drawLine(cx - sp * 1.0f, yPos(p), cx + sp * 1.0f, yPos(p), linePaint); p -= 2 }
            } else if (n.pos >= 10) {
                var p = 10
                while (p <= n.pos) { c.drawLine(cx - sp * 1.0f, yPos(p), cx + sp * 1.0f, yPos(p), linePaint); p += 2 }
            }
            // alteracion accidental
            if (n.show != 0) {
                val code = when (n.show) { 1 -> 0xE262; 2 -> 0xE260; else -> 0xE261 }
                glyph(c, code, cx - headW / 2f - sp * 1.3f, y, col)
            }
            // cabeza
            val head = when {
                n.nominal >= 192 -> 0xE0A2
                n.nominal >= 96 -> 0xE0A3
                else -> 0xE0A4
            }
            val hx = cx - (if (n.nominal >= 192) sp * 0.844f else headW / 2f)
            glyph(c, head, hx, y, col)
            // puntillo
            if (isDotted(n)) {
                val dy = if (Math.floorMod(n.pos, 2) == 0) y - sp * 0.5f else y
                glyph(c, 0xE1E7, cx + headW / 2f + sp * 0.35f, dy, col)
            }
            // plica y corchete (solo si no esta en un grupo con barra)
            if (n.nominal < 192 && nlay.group < 0) {
                val up = nlay.up
                val sx = if (up) cx + headW / 2f - stemW / 2f else cx - headW / 2f + stemW / 2f
                val tip = if (up) y - sp * 3.5f else y + sp * 3.5f
                linePaint.color = col
                linePaint.strokeWidth = stemW
                c.drawLine(sx, y, sx, tip, linePaint)
                if (n.nominal < 48) {
                    val fl = flagsOf(n)
                    val code = if (up) (if (fl == 1) 0xE240 else if (fl == 2) 0xE242 else 0xE244) else (if (fl == 1) 0xE241 else if (fl == 2) 0xE243 else 0xE245)
                    glyph(c, code, sx - stemW / 2f, tip, col)
                }
            }
            // acento y staccato
            if (n.accent) {
                if (nlay.up) glyph(c, 0xE4A1, cx - sp * 0.7f, y + sp * 1.3f, col)
                else glyph(c, 0xE4A0, cx - sp * 0.7f, y - sp * 1.3f, col)
            }
            if (n.staccato) {
                if (nlay.up) glyph(c, 0xE4A3, cx - sp * 0.2f, y + sp * 1.3f, col)
                else glyph(c, 0xE4A2, cx - sp * 0.2f, y - sp * 1.3f, col)
            }
            // digitacion (pistones) encima de la nota
            if (!ev.cont) {
                textPaint.textAlign = Paint.Align.CENTER
                if (showFinger) {
                    textPaint.typeface = Fonts.bold
                    textPaint.textSize = sp * 2.3f
                    textPaint.color = col
                    c.drawText(Fingering.forMidi(n.midi) ?: "?", cx, sp * 6.5f, textPaint)
                }
                if (showNames) {
                    textPaint.typeface = Fonts.medium
                    textPaint.textSize = sp * 1.45f
                    textPaint.color = if (col == C.TEXT) C.MUTED else col
                    c.drawText(Names.ofNote(n), cx, sp * 20.8f, textPaint)
                }
            }
        }

        // barras de corcheas y menores
        for (g in lay.groups) drawBeams(c, st, slot, lay, g)

        // tresillos
        for (g in lay.tuplets) drawTuplet(c, slot, lay, g)

        // ligaduras de union
        for (i in notes.indices) {
            val n = notes[i]
            if (n.isRest || !n.tie) continue
            val ev = st.events[slot.evIdx[i]]
            if (ev.tieTargetT < 0) continue
            val xa = xOf(ev.t0.toFloat()) + headW * 0.4f
            val xb = xOf(ev.tieTargetT.toFloat()) - headW * 0.4f
            if (xb < ce || xa > width) continue
            val y = yPos(n.pos)
            val below = lay.nl[i].up
            val yy = if (below) y + sp * 0.75f else y - sp * 0.75f
            val depth = if (below) sp * 0.9f else -sp * 0.9f
            path.reset()
            path.moveTo(xa, yy)
            path.quadTo((xa + xb) / 2f, yy + depth * 1.6f, xb, yy)
            linePaint.color = colorFor(ev.t0, ev.t1)
            linePaint.strokeWidth = sp * 0.16f
            c.drawPath(path, linePaint)
        }
    }

    private fun isDotted(n: Note): Boolean = !n.triplet && (n.dur == 36 || n.dur == 72 || n.dur == 144 || n.dur == 18 || n.dur == 9)

    private fun drawRepeat(c: Canvas, x: Float, start: Boolean) {
        val top = yLine(0); val bot = yLine(4)
        fillPaint.color = C.TEXT
        linePaint.color = C.TEXT
        linePaint.strokeWidth = sp * 0.11f
        if (start) {
            c.drawRect(x, top, x + sp * 0.4f, bot, fillPaint)
            c.drawLine(x + sp * 0.75f, top, x + sp * 0.75f, bot, linePaint)
            c.drawCircle(x + sp * 1.3f, yLine(1) + sp * 0.5f, sp * 0.18f, fillPaint)
            c.drawCircle(x + sp * 1.3f, yLine(2) + sp * 0.5f, sp * 0.18f, fillPaint)
        } else {
            c.drawRect(x - sp * 0.1f, top, x + sp * 0.3f, bot, fillPaint)
            c.drawLine(x - sp * 0.5f, top, x - sp * 0.5f, bot, linePaint)
            c.drawCircle(x - sp * 1.1f, yLine(1) + sp * 0.5f, sp * 0.18f, fillPaint)
            c.drawCircle(x - sp * 1.1f, yLine(2) + sp * 0.5f, sp * 0.18f, fillPaint)
        }
    }

    private fun drawRest(c: Canvas, n: Note, cx: Float, col: Int) {
        val nom = if (n.fullRest) 192 else n.nominal
        val dotted = !n.triplet && (nom == 36 || nom == 72 || nom == 144 || nom == 18 || nom == 9)
        val base = if (dotted) nom * 2 / 3 else nom
        val code: Int
        val y: Float
        when {
            base >= 192 -> { code = 0xE4E3; y = yLine(1) }
            base >= 96 -> { code = 0xE4E4; y = yLine(2) }
            base >= 48 -> { code = 0xE4E5; y = yLine(2) }
            base >= 24 -> { code = 0xE4E6; y = yLine(2) }
            base >= 12 -> { code = 0xE4E7; y = yLine(2) }
            else -> { code = 0xE4E8; y = yLine(2) }
        }
        glyphPaint.textSize = 4f * sp
        val w = glyphPaint.measureText(gs(code))
        glyph(c, code, cx - w / 2f, y, col)
        if (dotted) glyph(c, 0xE1E7, cx + w / 2f + sp * 0.3f, yLine(2) - sp * 0.5f, col)
    }

    private fun drawBeams(c: Canvas, st: Stream, slot: Slot, lay: MLay, g: IntArray) {
        val notes = slot.m.notes
        val up = lay.nl[g[0]].up
        var edge = if (up) Float.MAX_VALUE else -Float.MAX_VALUE
        for (i in g) {
            val y = yPos(notes[i].pos)
            edge = if (up) min(edge, y - sp * 3.3f) else max(edge, y + sp * 3.3f)
        }
        val xs = FloatArray(g.size)
        for ((k, i) in g.withIndex()) {
            val cx = xOf((slot.t0 + slot.offsets[i]).toFloat())
            xs[k] = if (up) cx + headW / 2f - stemW / 2f else cx - headW / 2f + stemW / 2f
            if (xs[k] < clefEnd() - sp * 3f || xs[k] > width + sp * 3f) { }
        }
        // plicas
        for ((k, i) in g.withIndex()) {
            val n = notes[i]
            val t = slot.t0 + slot.offsets[i]
            linePaint.color = colorFor(t, t + n.dur)
            linePaint.strokeWidth = stemW
            c.drawLine(xs[k], yPos(n.pos), xs[k], edge, linePaint)
        }
        // barras
        val gt0 = slot.t0 + slot.offsets[g[0]]
        val gt1 = slot.t0 + slot.offsets[g[g.size - 1]] + notes[g[g.size - 1]].dur
        fillPaint.color = if (curTick >= gt1) C.MUTED else C.TEXT
        val bt = sp * 0.5f
        val step = sp * 0.75f
        var maxLevel = 0
        for (i in g) maxLevel = max(maxLevel, flagsOf(notes[i]))
        for (lvl in 0 until maxLevel) {
            var a = 0
            while (a < g.size) {
                if (flagsOf(notes[g[a]]) > lvl) {
                    var b = a
                    while (b + 1 < g.size && flagsOf(notes[g[b + 1]]) > lvl) b++
                    val yTop = if (up) edge + lvl * step else edge - lvl * step - bt
                    if (b > a) {
                        rect.set(xs[a] - stemW / 2f, yTop, xs[b] + stemW / 2f, yTop + bt)
                    } else if (a == 0) {
                        rect.set(xs[a] - stemW / 2f, yTop, xs[a] + sp * 1.1f, yTop + bt)
                    } else {
                        rect.set(xs[a] - sp * 1.1f, yTop, xs[a] + stemW / 2f, yTop + bt)
                    }
                    c.drawRect(rect, fillPaint)
                    a = b + 1
                } else a++
            }
        }
        if (gt0 < 0) { }
    }

    private fun drawTuplet(c: Canvas, slot: Slot, lay: MLay, g: IntArray) {
        val notes = slot.m.notes
        val up = lay.nl[g[0]].up
        var tip = if (up) Float.MAX_VALUE else -Float.MAX_VALUE
        for (i in g) {
            val y = yPos(notes[i].pos)
            val t = if (up) y - sp * 3.5f else y + sp * 3.5f
            tip = if (up) min(tip, t) else max(tip, t)
        }
        val grouped = lay.nl[g[0]].group >= 0
        val yy = if (up) tip - sp * 0.9f else tip + sp * 0.9f
        val xa = xOf((slot.t0 + slot.offsets[g[0]]).toFloat()) - headW / 2f
        val xb = xOf((slot.t0 + slot.offsets[g[2]]).toFloat()) + headW / 2f
        val mid = (xa + xb) / 2f
        val t0 = slot.t0 + slot.offsets[g[0]]
        val t1 = slot.t0 + slot.offsets[g[2]] + notes[g[2]].dur
        val col = colorFor(t0, t1)
        linePaint.color = col
        linePaint.strokeWidth = sp * 0.1f
        val tick = if (up) sp * 0.6f else -sp * 0.6f
        if (!grouped) {
            c.drawLine(xa, yy, mid - sp * 0.9f, yy, linePaint)
            c.drawLine(mid + sp * 0.9f, yy, xb, yy, linePaint)
            c.drawLine(xa, yy, xa, yy + tick, linePaint)
            c.drawLine(xb, yy, xb, yy + tick, linePaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Fonts.bold
        textPaint.textSize = sp * 1.5f
        textPaint.color = col
        c.drawText("3", mid, if (up) yy + sp * 0.5f else yy + sp * 0.5f, textPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) { performClick(); onTap?.invoke() }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
