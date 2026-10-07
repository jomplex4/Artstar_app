package com.digitalminds.artstar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Paleta de las apps DigitalMinds. */
object C {
    const val BG = 0xFF000000.toInt()
    const val CARD = 0xFF151517.toInt()
    const val CARD2 = 0xFF1F2023.toInt()
    const val RED = 0xFFDC0000.toInt()
    const val RED_HI = 0xFFFF2A1F.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9A9CA4.toInt()
    const val DIM = 0xFF5A5C63.toInt()
    const val ICON_BG = 0xFFBEBEBE.toInt()
}

object Fonts {
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var bold: Typeface
    lateinit var music: Typeface
    private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        val am = ctx.assets
        regular = Typeface.createFromAsset(am, "fonts/SpaceGrotesk-Regular.ttf")
        medium = Typeface.createFromAsset(am, "fonts/SpaceGrotesk-Medium.ttf")
        bold = Typeface.createFromAsset(am, "fonts/SpaceGrotesk-Bold.ttf")
        music = Typeface.createFromAsset(am, "fonts/Bravura.otf")
        ready = true
    }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun roundRect(color: Int, radiusPx: Float, stroke: Int = 0, strokePx: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = radiusPx
    if (strokePx > 0) g.setStroke(strokePx, stroke)
    return g
}

fun label(ctx: Context, text: String, sp: Float, color: Int = C.TEXT, face: Typeface = Fonts.regular): TextView {
    val t = TextView(ctx)
    t.text = text
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    t.setTextColor(color)
    t.typeface = face
    t.includeFontPadding = false
    return t
}

/** Efecto de pulsacion simple y accion de toque. */
fun View.tap(action: () -> Unit) {
    isClickable = true
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.alpha = 0.6f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
        }
        false
    }
    setOnClickListener { action() }
}

fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams = LinearLayout.LayoutParams(w, h, weight)

val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Boton tipo pastilla: activo en rojo Ferrari, inactivo en grafito. */
class Chip(ctx: Context, text: String) : TextView(ctx) {
    private var active = false

    init {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Fonts.medium
        includeFontPadding = false
        gravity = Gravity.CENTER
        val ph = ctx.dpi(14f); val pv = ctx.dpi(8f)
        setPadding(ph, pv, ph, pv)
        paint()
    }

    fun setActive(a: Boolean) {
        active = a
        paint()
    }

    private fun paint() {
        setTextColor(if (active) C.TEXT else C.MUTED)
        background = roundRect(if (active) C.RED else C.CARD2, context.dp(20f))
    }
}

/** Iconos vectoriales simples dibujados con Canvas. */
class IconView(ctx: Context, private val kind: Int) : View(ctx) {
    companion object {
        const val PLAY = 0; const val PAUSE = 1; const val PREV = 2; const val NEXT = 3; const val BACK = 4
        const val METRO = 5; const val TUNER = 6; const val MIC = 7; const val GEAR = 8; const val CHECK = 9
        const val MINUS = 10; const val PLUS = 11; const val NOTE = 12; const val LEARN = 13; const val CLOSE = 14
        const val UP = 15; const val DOWN = 16; const val CHEVRON_DOWN = 17; const val CHEVRON_RIGHT = 18; const val DRUM = 19
    }

    var color = C.TEXT
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = minOf(w, h)
        val cx = w / 2; val cy = h / 2
        p.color = color
        p.style = Paint.Style.FILL
        p.strokeWidth = s * 0.09f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        when (kind) {
            PLAY -> { path.reset(); path.moveTo(cx - s * 0.2f, cy - s * 0.3f); path.lineTo(cx + s * 0.32f, cy); path.lineTo(cx - s * 0.2f, cy + s * 0.3f); path.close(); c.drawPath(path, p) }
            PAUSE -> { r.set(cx - s * 0.25f, cy - s * 0.28f, cx - s * 0.07f, cy + s * 0.28f); c.drawRoundRect(r, 3f, 3f, p); r.set(cx + s * 0.07f, cy - s * 0.28f, cx + s * 0.25f, cy + s * 0.28f); c.drawRoundRect(r, 3f, 3f, p) }
            PREV -> { r.set(cx - s * 0.3f, cy - s * 0.26f, cx - s * 0.2f, cy + s * 0.26f); c.drawRect(r, p); path.reset(); path.moveTo(cx + s * 0.28f, cy - s * 0.28f); path.lineTo(cx - s * 0.12f, cy); path.lineTo(cx + s * 0.28f, cy + s * 0.28f); path.close(); c.drawPath(path, p) }
            NEXT -> { r.set(cx + s * 0.2f, cy - s * 0.26f, cx + s * 0.3f, cy + s * 0.26f); c.drawRect(r, p); path.reset(); path.moveTo(cx - s * 0.28f, cy - s * 0.28f); path.lineTo(cx + s * 0.12f, cy); path.lineTo(cx - s * 0.28f, cy + s * 0.28f); path.close(); c.drawPath(path, p) }
            BACK -> { p.style = Paint.Style.STROKE; path.reset(); path.moveTo(cx + s * 0.1f, cy - s * 0.28f); path.lineTo(cx - s * 0.18f, cy); path.lineTo(cx + s * 0.1f, cy + s * 0.28f); c.drawPath(path, p) }
            CLOSE -> { p.style = Paint.Style.STROKE; c.drawLine(cx - s * 0.22f, cy - s * 0.22f, cx + s * 0.22f, cy + s * 0.22f, p); c.drawLine(cx + s * 0.22f, cy - s * 0.22f, cx - s * 0.22f, cy + s * 0.22f, p) }
            MINUS -> { p.style = Paint.Style.STROKE; c.drawLine(cx - s * 0.25f, cy, cx + s * 0.25f, cy, p) }
            PLUS -> { p.style = Paint.Style.STROKE; c.drawLine(cx - s * 0.25f, cy, cx + s * 0.25f, cy, p); c.drawLine(cx, cy - s * 0.25f, cx, cy + s * 0.25f, p) }
            CHECK -> { p.style = Paint.Style.STROKE; path.reset(); path.moveTo(cx - s * 0.25f, cy); path.lineTo(cx - s * 0.07f, cy + s * 0.2f); path.lineTo(cx + s * 0.27f, cy - s * 0.2f); c.drawPath(path, p) }
            METRO -> {
                p.style = Paint.Style.STROKE
                path.reset(); path.moveTo(cx - s * 0.16f, cy + s * 0.3f); path.lineTo(cx - s * 0.06f, cy - s * 0.3f); path.lineTo(cx + s * 0.06f, cy - s * 0.3f); path.lineTo(cx + s * 0.16f, cy + s * 0.3f); path.close(); c.drawPath(path, p)
                c.drawLine(cx, cy + s * 0.15f, cx + s * 0.14f, cy - s * 0.22f, p)
            }
            TUNER -> {
                p.style = Paint.Style.STROKE
                r.set(cx - s * 0.32f, cy - s * 0.28f, cx + s * 0.32f, cy + s * 0.36f); c.drawArc(r, 200f, 140f, false, p)
                c.drawLine(cx, cy + s * 0.1f, cx + s * 0.14f, cy - s * 0.2f, p)
                p.style = Paint.Style.FILL; c.drawCircle(cx, cy + s * 0.12f, s * 0.05f, p)
            }
            MIC -> {
                r.set(cx - s * 0.12f, cy - s * 0.32f, cx + s * 0.12f, cy + s * 0.08f); c.drawRoundRect(r, s * 0.12f, s * 0.12f, p)
                p.style = Paint.Style.STROKE
                r.set(cx - s * 0.22f, cy - s * 0.18f, cx + s * 0.22f, cy + s * 0.22f); c.drawArc(r, 20f, 140f, false, p)
                c.drawLine(cx, cy + s * 0.22f, cx, cy + s * 0.34f, p)
            }
            GEAR -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(cx, cy, s * 0.14f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine((cx + Math.cos(a) * s * 0.22f).toFloat(), (cy + Math.sin(a) * s * 0.22f).toFloat(), (cx + Math.cos(a) * s * 0.32f).toFloat(), (cy + Math.sin(a) * s * 0.32f).toFloat(), p)
                }
            }
            NOTE -> { drawGlyph(c, 0xE1D7, s * 1.6f, cx - s * 0.2f, cy + s * 0.25f) }
            UP -> { path.reset(); path.moveTo(cx, cy - s * 0.3f); path.lineTo(cx + s * 0.36f, cy + s * 0.24f); path.lineTo(cx - s * 0.36f, cy + s * 0.24f); path.close(); c.drawPath(path, p) }
            DOWN -> { path.reset(); path.moveTo(cx, cy + s * 0.3f); path.lineTo(cx + s * 0.36f, cy - s * 0.24f); path.lineTo(cx - s * 0.36f, cy - s * 0.24f); path.close(); c.drawPath(path, p) }
            CHEVRON_DOWN -> { p.style = Paint.Style.STROKE; path.reset(); path.moveTo(cx - s * 0.25f, cy - s * 0.1f); path.lineTo(cx, cy + s * 0.15f); path.lineTo(cx + s * 0.25f, cy - s * 0.1f); c.drawPath(path, p) }
            CHEVRON_RIGHT -> { p.style = Paint.Style.STROKE; path.reset(); path.moveTo(cx - s * 0.1f, cy - s * 0.25f); path.lineTo(cx + s * 0.15f, cy); path.lineTo(cx - s * 0.1f, cy + s * 0.25f); c.drawPath(path, p) }
            DRUM -> {
                p.style = Paint.Style.STROKE
                r.set(cx - s * 0.32f, cy - s * 0.2f, cx + s * 0.32f, cy - s * 0.02f); c.drawOval(r, p)
                c.drawLine(cx - s * 0.32f, cy - s * 0.11f, cx - s * 0.32f, cy + s * 0.2f, p)
                c.drawLine(cx + s * 0.32f, cy - s * 0.11f, cx + s * 0.32f, cy + s * 0.2f, p)
                r.set(cx - s * 0.32f, cy + s * 0.11f, cx + s * 0.32f, cy + s * 0.29f); c.drawArc(r, 0f, 180f, false, p)
                c.drawLine(cx - s * 0.1f, cy - s * 0.18f, cx - s * 0.3f, cy - s * 0.38f, p)
                c.drawLine(cx + s * 0.1f, cy - s * 0.18f, cx + s * 0.3f, cy - s * 0.38f, p)
            }
            LEARN -> { drawGlyph(c, 0xE050, s * 1.25f, cx - s * 0.2f, cy + s * 0.22f) }
        }
    }

    private fun drawGlyph(c: Canvas, code: Int, size: Float, x: Float, y: Float) {
        p.typeface = Fonts.music
        p.style = Paint.Style.FILL
        p.textSize = size
        c.drawText(String(Character.toChars(code)), x, y, p)
        p.typeface = Typeface.DEFAULT
    }
}
