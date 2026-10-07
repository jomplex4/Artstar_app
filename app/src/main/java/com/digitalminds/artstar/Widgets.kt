package com.digitalminds.artstar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Muestra la digitacion de la nota: 3 pistones (bajo y trompeta) o las llaves del saxo. */
class FingerView(ctx: Context) : View(ctx) {
    private var inst: InstDef = Insts.bar
    private var midi = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun set(i: InstDef, writtenMidi: Int) {
        if (i === inst && writtenMidi == midi) return
        inst = i; midi = writtenMidi; invalidate()
    }

    private fun dot(c: Canvas, cx: Float, cy: Float, r: Float, on: Boolean, label: String) {
        p.style = Paint.Style.FILL
        p.color = if (on) C.RED_HI else C.CARD2
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.14f
        p.color = if (on) C.RED_HI else C.DIM
        c.drawCircle(cx, cy, r, p)
        if (label.isNotEmpty()) {
            p.style = Paint.Style.FILL
            p.color = if (on) C.TEXT else C.MUTED
            p.typeface = Fonts.bold
            p.textSize = r * 1.0f
            p.textAlign = Paint.Align.CENTER
            c.drawText(label, cx, cy + r * 0.36f, p)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (inst.fam == 1) {
            val sx = if (midi >= 0) Fingering.sax(midi) else null
            val r = min(h * 0.26f, w / 20f)
            val cy = h * 0.42f
            val gap = r * 2.5f
            var x = r * 1.4f
            dot(c, x, cy, r * 0.75f, sx?.oct == true, "8")
            x += gap
            for (k in 1..3) { dot(c, x, cy, r, sx?.lh?.contains(k.toString()) == true, k.toString()); x += gap }
            p.color = C.DIM; p.strokeWidth = r * 0.15f
            c.drawLine(x - gap * 0.5f, cy - r * 1.2f, x - gap * 0.5f, cy + r * 1.2f, p)
            for (k in 4..6) { dot(c, x, cy, r, sx?.rh?.contains(k.toString()) == true, k.toString()); x += gap }
            p.style = Paint.Style.FILL
            p.textAlign = Paint.Align.CENTER
            p.typeface = Fonts.medium
            p.textSize = h * 0.22f
            p.color = C.MUTED
            val extra = if (sx == null) (if (midi >= 0) "fuera de rango" else "") else sx.extra
            if (extra.isNotEmpty()) c.drawText(extra, w / 2f, h * 0.97f, p)
        } else if (inst.fam == 3) {
            val pos = if (midi >= 0) Fingering.slide(midi) else null
            val r = min(h * 0.32f, w / 18f)
            val x0 = r * 1.5f; val x1 = w - r * 1.5f
            val cy = h * 0.5f
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.3f; p.color = C.CARD2
            c.drawLine(x0, cy, x1, cy, p)
            for (k in 1..7) {
                val x = x0 + (x1 - x0) * (k - 1) / 6f
                p.style = Paint.Style.FILL
                p.color = if (pos == k) C.RED_HI else C.DIM
                c.drawCircle(x, cy, if (pos == k) r * 1.15f else r * 0.55f, p)
                p.color = if (pos == k) C.TEXT else C.MUTED
                p.typeface = Fonts.bold; p.textAlign = Paint.Align.CENTER
                p.textSize = if (pos == k) r * 1.2f else r * 0.85f
                c.drawText(k.toString(), x, if (pos == k) cy + r * 0.42f else cy + r * 2.2f, p)
            }
        } else {
            val code = if (midi < 0) null else if (inst.fam == 4) Fingering.tubaValves(midi) else Fingering.valves(midi)
            val v = Fingering.valveArray(code)
            val r = min(h * 0.4f, w / 7f)
            val gap = w / 3f
            for (i in 0 until 3) dot(c, gap * i + gap / 2f, h / 2f, r, v[i], (i + 1).toString())
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
        val pad = context.dp(8f)
        r.set(pad, h / 2 - th / 2, w - pad, h / 2 + th / 2)
        p.color = C.CARD2; c.drawRoundRect(r, th, th, p)
        val x = pad + (w - 2 * pad) * value
        r.set(pad, h / 2 - th / 2, x, h / 2 + th / 2)
        p.color = C.RED; c.drawRoundRect(r, th, th, p)
        p.color = C.TEXT; c.drawCircle(x, h / 2, context.dp(7f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val pad = context.dp(8f)
                val f = ((e.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
                value = f
                onSeek(f)
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}

/** Lista con barra lateral rapida: se arrastra el control rojo para llegar al final en un instante. */
class FastScrollView(ctx: Context) : android.widget.FrameLayout(ctx) {
    val sv = android.widget.ScrollView(ctx)
    private val bar = Bar(ctx)

    init {
        sv.isVerticalScrollBarEnabled = false
        sv.overScrollMode = OVER_SCROLL_NEVER
        addView(sv, LayoutParams(MATCH, MATCH))
        addView(bar, LayoutParams(ctx.dpi(26f), MATCH, android.view.Gravity.END))
        sv.setOnScrollChangeListener { _, _, _, _, _ -> bar.invalidate() }
    }

    fun setContent(v: View) {
        sv.addView(v, LayoutParams(MATCH, WRAP))
        post { bar.invalidate() }
    }

    private inner class Bar(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private var dragging = false

        private fun contentH(): Int = if (sv.childCount > 0) sv.getChildAt(0).height else 0
        private fun maxScroll(): Int = max(0, contentH() - sv.height)
        private fun thumbH(): Float {
            val ch = contentH().toFloat()
            if (ch <= 0f) return 0f
            return max(context.dp(52f), height * sv.height / ch).coerceAtMost(height.toFloat())
        }

        override fun onDraw(c: Canvas) {
            if (maxScroll() <= 0) return
            val w = width.toFloat()
            val th = thumbH()
            val frac = sv.scrollY.toFloat() / maxScroll()
            val top = frac * (height - th)
            val cx = w - context.dp(8f)
            p.color = C.CARD2
            r.set(cx - context.dp(2f), 0f, cx + context.dp(2f), height.toFloat())
            c.drawRoundRect(r, context.dp(2f), context.dp(2f), p)
            p.color = if (dragging) C.RED_HI else C.RED
            r.set(cx - context.dp(4f), top, cx + context.dp(4f), top + th)
            c.drawRoundRect(r, context.dp(4f), context.dp(4f), p)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (maxScroll() <= 0) return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    val th = thumbH()
                    val f = ((e.y - th / 2f) / max(1f, height - th)).coerceIn(0f, 1f)
                    sv.scrollTo(0, (f * maxScroll()).toInt())
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
            }
            return true
        }
    }
}
