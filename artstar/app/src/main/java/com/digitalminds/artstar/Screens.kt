package com.digitalminds.artstar

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun logoView(ctx: Context, heightDp: Float): ImageView {
    val iv = ImageView(ctx)
    val bmp = ctx.assets.open("dm_logo.png").use { BitmapFactory.decodeStream(it) }
    iv.setImageBitmap(bmp)
    iv.adjustViewBounds = true
    iv.scaleType = ImageView.ScaleType.FIT_START
    iv.layoutParams = LinearLayout.LayoutParams((ctx.dp(heightDp) * bmp.width / bmp.height).toInt(), ctx.dpi(heightDp))
    return iv
}

/** Icono de texto: usa Bravura (musica) o Space Grotesk. */
class GlyphIcon(ctx: Context, private val text: String, private val music: Boolean, private val boxed: Boolean = false, private val sizeDp: Float = 30f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        p.color = C.TEXT
        p.textAlign = Paint.Align.CENTER
        p.typeface = if (music) Fonts.music else Fonts.bold
        p.textSize = context.dp(sizeDp)
        val cx = width / 2f
        val cy = height / 2f
        if (boxed) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = context.dp(1.6f)
            r.set(cx - context.dp(16f), cy - context.dp(16f), cx + context.dp(16f), cy + context.dp(16f))
            c.drawRect(r, p)
        }
        p.style = Paint.Style.FILL
        val base = if (music) cy + p.textSize * 0.12f else cy + p.textSize * 0.35f
        c.drawText(text, cx, base, p)
    }
}

/** Dibujos para simbolos que no son un caracter de la fuente. */
class ShapeIcon(ctx: Context, private val kind: Int) : View(ctx) {
    companion object { const val TIE = 0; const val SLUR = 1; const val TUPLET = 2; const val VOLTA = 3; const val REPEAT = 4; const val HAIRPIN = 5 }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h / 2
        val u = context.dp(1f)
        p.color = C.TEXT
        p.strokeWidth = u * 2f
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.FILL
        when (kind) {
            TIE, SLUR -> {
                val y = cy + u * 6
                p.textSize = u * 34
                p.typeface = Fonts.music
                p.textAlign = Paint.Align.CENTER
                val a = if (kind == TIE) 0xE0A4 else 0xE0A4
                c.drawText(String(Character.toChars(a)), cx - u * 18, y, p)
                c.drawText(String(Character.toChars(a)), cx + u * 18, y - (if (kind == SLUR) u * 6 else 0f), p)
                p.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(cx - u * 18, y + u * 6)
                path.quadTo(cx, y + u * 20, cx + u * 18, y + u * 6 - (if (kind == SLUR) u * 6 else 0f))
                c.drawPath(path, p)
            }
            TUPLET -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - u * 24, cy + u * 4, cx - u * 8, cy + u * 4, p)
                c.drawLine(cx + u * 8, cy + u * 4, cx + u * 24, cy + u * 4, p)
                c.drawLine(cx - u * 24, cy + u * 4, cx - u * 24, cy + u * 11, p)
                c.drawLine(cx + u * 24, cy + u * 4, cx + u * 24, cy + u * 11, p)
                p.style = Paint.Style.FILL
                p.typeface = Fonts.bold
                p.textAlign = Paint.Align.CENTER
                p.textSize = u * 17
                c.drawText("3", cx, cy + u * 10, p)
            }
            VOLTA -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - u * 26, cy - u * 6, cx + u * 22, cy - u * 6, p)
                c.drawLine(cx - u * 26, cy - u * 6, cx - u * 26, cy + u * 8, p)
                p.style = Paint.Style.FILL
                p.typeface = Fonts.medium
                p.textAlign = Paint.Align.LEFT
                p.textSize = u * 15
                c.drawText("1.", cx - u * 21, cy + u * 8, p)
            }
            REPEAT -> {
                c.drawRect(cx - u * 4, cy - u * 18, cx - u * 1, cy + u * 18, p)
                c.drawRect(cx - u * 10, cy - u * 18, cx - u * 8.5f, cy + u * 18, p)
                c.drawCircle(cx - u * 15, cy - u * 6, u * 2.2f, p)
                c.drawCircle(cx - u * 15, cy + u * 6, u * 2.2f, p)
                c.drawRect(cx + u * 6, cy - u * 18, cx + u * 7.5f, cy + u * 18, p)
                c.drawRect(cx + u * 10, cy - u * 18, cx + u * 13, cy + u * 18, p)
                c.drawCircle(cx + u * 18, cy - u * 6, u * 2.2f, p)
                c.drawCircle(cx + u * 18, cy + u * 6, u * 2.2f, p)
            }
            HAIRPIN -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - u * 26, cy, cx + u * 26, cy - u * 9, p)
                c.drawLine(cx - u * 26, cy, cx + u * 26, cy + u * 9, p)
            }
        }
    }
}

/** Pantalla con barra superior y boton atras, usada para los detalles de Aprender. */
class DetailFrame(ctx: Context, title: String, content: View, onBack: () -> Unit) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        setBackgroundColor(C.BG)
        val bar = LinearLayout(ctx)
        bar.gravity = Gravity.CENTER_VERTICAL
        val back = FrameLayout(ctx)
        back.addView(IconView(ctx, IconView.BACK), FrameLayout.LayoutParams(ctx.dpi(28f), ctx.dpi(28f), Gravity.CENTER))
        back.tap { onBack() }
        bar.addView(back, lp(ctx.dpi(48f), ctx.dpi(48f)))
        bar.addView(label(ctx, title, 20f, C.TEXT, Fonts.bold), lp(WRAP, WRAP))
        addView(bar, lp(MATCH, ctx.dpi(52f)))
        addView(content, lp(MATCH, 0, 1f))
    }
}

fun card(ctx: Context): LinearLayout {
    val l = LinearLayout(ctx)
    l.orientation = LinearLayout.HORIZONTAL
    l.gravity = Gravity.CENTER_VERTICAL
    l.background = roundRect(C.CARD, ctx.dp(18f))
    l.setPadding(ctx.dpi(14f), ctx.dpi(14f), ctx.dpi(14f), ctx.dpi(14f))
    return l
}

fun cardLp(ctx: Context): LinearLayout.LayoutParams {
    val l = lp(MATCH, WRAP)
    l.setMargins(0, 0, 0, ctx.dpi(10f))
    return l
}

fun scrollOf(ctx: Context, build: (LinearLayout) -> Unit): ScrollView {
    val sv = ScrollView(ctx)
    sv.isVerticalScrollBarEnabled = false
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(ctx.dpi(16f), ctx.dpi(6f), ctx.dpi(16f), ctx.dpi(24f))
    build(col)
    sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
    return sv
}

// ====================== BIBLIOTECA ======================

class LibraryTab(private val host: Host, private val songs: List<SongInfo>, private val open: (SongInfo, String, Int) -> Unit) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private var partIdx = host.prefs.getInt("part", 0)

    init {
        build()
    }

    private fun build() {
        removeAllViews()
        val list = songs.filter { it.cat == "song" }
        addView(scrollOf(ctx) { col ->
            // selector de parte (pastilla como en COMET)
            val pill = LinearLayout(ctx)
            pill.background = roundRect(C.CARD2, ctx.dp(26f))
            pill.setPadding(ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f), ctx.dpi(4f))
            val names = arrayOf("Barítono 1", "Barítono 2")
            for ((i, n) in names.withIndex()) {
                val t = label(ctx, n, 15f, if (i == partIdx) C.TEXT else C.MUTED, Fonts.medium)
                t.gravity = Gravity.CENTER
                t.setPadding(0, ctx.dpi(11f), 0, ctx.dpi(11f))
                if (i == partIdx) t.background = roundRect(C.RED, ctx.dp(22f))
                t.tap { partIdx = i; host.prefs.edit().putInt("part", i).apply(); build() }
                pill.addView(t, lp(0, WRAP, 1f))
            }
            col.addView(pill, lp(MATCH, WRAP).also { it.setMargins(0, ctx.dpi(8f), 0, ctx.dpi(18f)) })

            val head = label(ctx, "${list.size} marchas", 17f, C.TEXT, Fonts.bold)
            head.setPadding(ctx.dpi(2f), 0, 0, ctx.dpi(10f))
            col.addView(head)

            for (s in list) {
                val c = card(ctx)
                val ic = FrameLayout(ctx)
                ic.background = roundRect(C.CARD2, ctx.dp(14f))
                ic.addView(GlyphIcon(ctx, "\uE1D7", true, false, 30f), FrameLayout.LayoutParams(MATCH, MATCH))
                c.addView(ic, lp(ctx.dpi(52f), ctx.dpi(52f)))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.setPadding(ctx.dpi(14f), 0, ctx.dpi(8f), 0)
                tx.addView(label(ctx, s.title, 17f, C.TEXT, Fonts.bold))
                val sub = label(ctx, s.subtitle + " · " + s.meta + (if (s.parts.size == 1) " · solo " + s.parts[0].second else ""), 13f, C.MUTED)
                sub.setPadding(0, ctx.dpi(3f), 0, 0)
                tx.addView(sub)
                c.addView(tx, lp(0, WRAP, 1f))
                val pl = FrameLayout(ctx)
                pl.background = roundRect(C.RED, ctx.dp(20f))
                pl.addView(IconView(ctx, IconView.PLAY), FrameLayout.LayoutParams(ctx.dpi(26f), ctx.dpi(26f), Gravity.CENTER))
                c.addView(pl, lp(ctx.dpi(40f), ctx.dpi(40f)))
                val pid = s.parts.getOrNull(partIdx)?.first ?: s.parts[0].first
                c.tap { open(s, pid, 0) }
                col.addView(c, cardLp(ctx))
            }
            val hint = label(ctx, "Cada marcha trae sus secciones de práctica, por ejemplo el tema principal, para repasar solo lo más bonito.", 12f, C.DIM)
            hint.setPadding(ctx.dpi(2f), ctx.dpi(8f), ctx.dpi(2f), 0)
            col.addView(hint)
        }, LayoutParams(MATCH, MATCH))
    }
}

// ====================== APRENDER ======================

class LearnTab(private val host: Host, private val songs: List<SongInfo>, private val open: (SongInfo, String, Int) -> Unit, private val push: (String, View) -> Unit) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context

    init {
        addView(scrollOf(ctx) { col ->
            col.addView(label(ctx, "Aprende a leer partitura", 22f, C.TEXT, Fonts.bold).also { it.setPadding(ctx.dpi(2f), ctx.dpi(8f), 0, ctx.dpi(4f)) })
            col.addView(label(ctx, "Empieza por las herramientas y sigue los ejercicios en orden.", 13f, C.MUTED).also { it.setPadding(ctx.dpi(2f), 0, 0, ctx.dpi(14f)) })

            fun tool(glyph: String, music: Boolean, title: String, text: String, action: () -> Unit) {
                val c = card(ctx)
                val ic = FrameLayout(ctx)
                ic.background = roundRect(C.CARD2, ctx.dp(14f))
                ic.addView(GlyphIcon(ctx, glyph, music, false, if (music) 30f else 22f), FrameLayout.LayoutParams(MATCH, MATCH))
                c.addView(ic, lp(ctx.dpi(52f), ctx.dpi(52f)))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.setPadding(ctx.dpi(14f), 0, ctx.dpi(6f), 0)
                tx.addView(label(ctx, title, 16f, C.TEXT, Fonts.bold))
                val s = label(ctx, text, 12.5f, C.MUTED)
                s.setPadding(0, ctx.dpi(3f), 0, 0)
                tx.addView(s)
                c.addView(tx, lp(0, WRAP, 1f))
                c.tap(action)
                col.addView(c, cardLp(ctx))
            }
            tool("\uE050", true, "Explorador de notas", "Toca el pentagrama: ves el nombre, los pistones y escuchas la nota.") { push("Explorador de notas", ExplorerView(host)) }
            tool("\uE1D5", true, "Símbolos de la partitura", "Figuras, silencios, repeticiones, casillas, ligaduras y más.") { push("Símbolos", symbolsView()) }
            tool("0 1 2", false, "Tabla de pistones", "Qué pistones presionar para cada nota de tu bajo.") { push("Tabla de pistones", chartView()) }

            val lessons = songs.filter { it.cat == "lesson" }
            var group = ""
            for (l in lessons) {
                if (l.group != group) {
                    group = l.group
                    val g = label(ctx, group, 15f, C.MUTED, Fonts.medium)
                    g.setPadding(ctx.dpi(2f), ctx.dpi(14f), 0, ctx.dpi(8f))
                    col.addView(g)
                }
                val c = card(ctx)
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.addView(label(ctx, l.title, 16f, C.TEXT, Fonts.bold))
                val s = label(ctx, l.meta + " · " + l.bars + " compases", 12.5f, C.MUTED)
                s.setPadding(0, ctx.dpi(3f), 0, 0)
                tx.addView(s)
                c.addView(tx, lp(0, WRAP, 1f))
                val go = FrameLayout(ctx)
                go.background = roundRect(C.CARD2, ctx.dp(18f))
                go.addView(IconView(ctx, IconView.PLAY).also { it.color = C.RED_HI }, FrameLayout.LayoutParams(ctx.dpi(22f), ctx.dpi(22f), Gravity.CENTER))
                c.addView(go, lp(ctx.dpi(36f), ctx.dpi(36f)))
                c.tap { push(l.title, lessonView(l)) }
                col.addView(c, cardLp(ctx))
            }
        }, LayoutParams(MATCH, MATCH))
    }

    private fun lessonView(info: SongInfo): View {
        val song = Library.load(ctx, info.id)
        return scrollOf(ctx) { col ->
            col.addView(label(ctx, info.group, 13f, C.MUTED, Fonts.medium))
            val d = label(ctx, song.desc, 16f, C.TEXT)
            d.setLineSpacing(0f, 1.2f)
            d.setPadding(0, ctx.dpi(8f), 0, ctx.dpi(18f))
            col.addView(d)
            val meta = label(ctx, "Compás ${song.num}/${song.den} · tempo ${song.bpm} · ${info.bars} compases", 13f, C.MUTED)
            meta.setPadding(0, 0, 0, ctx.dpi(18f))
            col.addView(meta)
            fun big(text: String, sub: String, mode: Int, primary: Boolean) {
                val c = card(ctx)
                if (primary) c.background = roundRect(C.RED, ctx.dp(18f))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.addView(label(ctx, text, 16f, C.TEXT, Fonts.bold))
                val s = label(ctx, sub, 12.5f, if (primary) C.TEXT else C.MUTED)
                s.setPadding(0, ctx.dpi(3f), 0, 0)
                tx.addView(s)
                c.addView(tx, lp(0, WRAP, 1f))
                c.tap { open(info, "bar1", mode) }
                col.addView(c, cardLp(ctx))
            }
            big("Practicar", "La partitura se desplaza sola, con metrónomo y sonido.", 0, true)
            big("Modo espera", "Avanza solo cuando tocas la nota correcta. Usa el micrófono.", 1, false)
            big("Evaluar", "Toca a tiempo y gana estrellas. Usa el micrófono.", 2, false)
        }
    }

    private fun chartView(): View = scrollOf(ctx) { col ->
        val h = label(ctx, "Nota escrita en clave de sol y pistones del bajo en Si bemol. Con 0 no presionas nada.", 13f, C.MUTED)
        h.setPadding(0, 0, 0, ctx.dpi(12f))
        col.addView(h)
        for (m in 84 downTo 54) {
            val f = Fingering.forMidi(m) ?: continue
            val row = LinearLayout(ctx)
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = roundRect(C.CARD, ctx.dp(12f))
            row.setPadding(ctx.dpi(14f), ctx.dpi(8f), ctx.dpi(14f), ctx.dpi(8f))
            row.addView(label(ctx, Names.withOctave(m), 16f, C.TEXT, Fonts.bold), lp(ctx.dpi(84f), WRAP))
            row.addView(label(ctx, f, 16f, C.RED_HI, Fonts.bold), lp(ctx.dpi(60f), WRAP))
            val v = ValveView(ctx); v.set(f)
            row.addView(v, lp(ctx.dpi(96f), ctx.dpi(30f)))
            col.addView(row, lp(MATCH, WRAP).also { it.setMargins(0, 0, 0, ctx.dpi(6f)) })
        }
    }

    private fun symbolsView(): View = scrollOf(ctx) { col ->
        fun item(icon: View, title: String, text: String) {
            val c = card(ctx)
            c.addView(icon, lp(ctx.dpi(84f), ctx.dpi(64f)))
            val tx = LinearLayout(ctx)
            tx.orientation = LinearLayout.VERTICAL
            tx.setPadding(ctx.dpi(8f), 0, 0, 0)
            tx.addView(label(ctx, title, 16f, C.TEXT, Fonts.bold))
            val s = label(ctx, text, 13f, C.MUTED)
            s.setLineSpacing(0f, 1.15f)
            s.setPadding(0, ctx.dpi(4f), 0, 0)
            tx.addView(s)
            c.addView(tx, lp(0, WRAP, 1f))
            col.addView(c, cardLp(ctx))
        }
        val em = "\u2003"
        item(GlyphIcon(ctx, "\uE050", true), "Pentagrama y clave de sol", "Cinco líneas y cuatro espacios, que se cuentan de abajo hacia arriba. La clave de sol rodea la segunda línea, que es Sol. Las partes de barítono y bajo de marcha se leen en clave de sol.")
        item(GlyphIcon(ctx, "2/4", false, false, 24f), "Compás", "Arriba, cuántos tiempos tiene cada compás. Abajo, qué figura vale un tiempo (4 es la negra). En 2/4 caben dos negras. Las barras verticales separan los compases.")
        item(GlyphIcon(ctx, "\uE262", true), "Armadura", "Los sostenidos junto a la clave afectan a todas las notas con ese nombre en toda la pieza. En Sol mayor hay un sostenido: todos los Fa se tocan Fa#.")
        item(GlyphIcon(ctx, "\uE1D2$em\uE1D3$em\uE1D5", true, false, 26f), "Redonda, blanca y negra", "La redonda dura 4 tiempos, la blanca 2 y la negra 1. La negra es la unidad que marca el metrónomo.")
        item(GlyphIcon(ctx, "\uE1D7$em\uE1D9$em\uE1DB", true, false, 26f), "Corchea, semicorchea y fusa", "La corchea dura medio tiempo, la semicorchea un cuarto y la fusa un octavo. Se unen con barras: una barra para corcheas, dos para semicorcheas, tres para fusas.")
        item(GlyphIcon(ctx, "\uE4E5$em\uE4E6$em\uE4E7", true, false, 26f), "Silencios", "Cada figura tiene su silencio con la misma duración: negra, corchea y semicorchea en la imagen. En un silencio no soples, pero sigue contando.")
        item(GlyphIcon(ctx, "\uE4E3", true), "Silencio de compás completo", "El silencio de redonda colgado de la línea sirve para un compás entero vacío, sin importar cuántos tiempos tenga.")
        item(GlyphIcon(ctx, "\uE1D5\uE1E7", true, false, 30f), "Puntillo", "El punto a la derecha de una nota le suma la mitad de su valor. Una negra con puntillo dura un tiempo y medio. Es muy común: corchea con puntillo más semicorchea.")
        item(ShapeIcon(ctx, ShapeIcon.TIE), "Ligadura de unión", "Curva entre dos notas del mismo sonido: se tocan como una sola nota larga. No vuelvas a atacar la segunda.")
        item(ShapeIcon(ctx, ShapeIcon.SLUR), "Ligadura de expresión", "Curva entre notas distintas: tócalas seguidas y suaves, sin cortar el aire entre ellas.")
        item(ShapeIcon(ctx, ShapeIcon.TUPLET), "Tresillo", "Tres notas que se tocan en el tiempo de dos. Se marca con un 3 y una llave. Cuenta: tre-si-llo.")
        item(GlyphIcon(ctx, "\uE262$em\uE260$em\uE261", true, false, 26f), "Sostenido, bemol y becuadro", "El sostenido sube la nota medio tono, el bemol la baja y el becuadro cancela la alteración. Vale hasta la siguiente barra de compás.")
        item(GlyphIcon(ctx, "\uE4A0$em\uE4A2", true, false, 26f), "Acento y staccato", "El acento (>) pide atacar la nota con más fuerza. El punto sobre la nota (staccato) la pide corta y separada.")
        item(GlyphIcon(ctx, "\uE4C0", true), "Calderón", "Alarga la nota o el silencio más de lo escrito. El director decide cuánto.")
        item(ShapeIcon(ctx, ShapeIcon.REPEAT), "Barras de repetición", "Los dos puntos señalan la parte que se repite. Al llegar al signo de cierre, vuelve al signo de inicio y toca de nuevo ese fragmento.")
        item(ShapeIcon(ctx, ShapeIcon.VOLTA), "Casillas 1 y 2", "Los \"puentes\" numerados. La primera vez tocas la casilla 1. Al repetir, te saltas la 1 y tocas la 2.")
        item(GlyphIcon(ctx, "\uE047$em\uE048", true, false, 26f), "Segno y Coda", "El segno marca un punto al que se vuelve con D.S. (del signo). La coda es un final aparte: al llegar a \"Al segno y coda\" vuelves al signo y, en la señal de coda, saltas al final.")
        item(GlyphIcon(ctx, "D.C.", false, false, 22f), "D.C. y Fine", "D.C. significa volver al principio. Fine es el final: \"D.C. al Fine\" es volver al inicio y terminar donde dice Fine.")
        item(GlyphIcon(ctx, "\uE52D$em\uE52F", true, false, 26f), "Matices", "mf es medio fuerte, ff es muy fuerte. El crescendo (una abertura creciente) pide ir aumentando el volumen poco a poco.")
        item(ShapeIcon(ctx, ShapeIcon.HAIRPIN), "Crescendo", "Las líneas que se abren indican que debes subir la intensidad. Si se cierran, es diminuendo: bajar.")
        item(GlyphIcon(ctx, "A", false, true, 20f), "Letras de ensayo", "Las letras en cuadro (A, B, C) son marcas para ubicarse cuando el director dice \"desde la B\".")
        item(GlyphIcon(ctx, "0 1 2 3", false, false, 16f), "Pistones", "Sobre cada nota la app escribe qué pistones presionar. 0 es sin pistones, 12 es el primero y el segundo juntos, y así con las demás combinaciones.")
    }
}

// ====================== EXPLORADOR DE NOTAS ======================

class ExplorerView(private val host: Host) : LinearLayout(host as Context) {
    private val ctx: Context = host as Context
    private var pos = 5       // Do5
    private var alter = 0
    private val staff = Staff(ctx)
    private val nameV = label(ctx, "", 34f, C.TEXT, Fonts.bold)
    private val infoV = label(ctx, "", 13f, C.MUTED)
    private val valve = ValveView(ctx)
    private val fingV = label(ctx, "", 30f, C.RED_HI, Fonts.bold)
    private val SEMI = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(16f), 0, ctx.dpi(16f), ctx.dpi(16f))
        val hint = label(ctx, "Toca una línea o un espacio del pentagrama para colocar la nota.", 13f, C.MUTED)
        hint.setPadding(0, 0, 0, ctx.dpi(10f))
        addView(hint)
        addView(staff, lp(MATCH, 0, 1f))
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = roundRect(C.CARD, ctx.dp(18f))
        row.setPadding(ctx.dpi(16f), ctx.dpi(12f), ctx.dpi(16f), ctx.dpi(12f))
        val left = LinearLayout(ctx)
        left.orientation = VERTICAL
        left.addView(nameV)
        left.addView(infoV)
        row.addView(left, lp(0, WRAP, 1f))
        val right = LinearLayout(ctx)
        right.orientation = VERTICAL
        right.gravity = Gravity.CENTER_HORIZONTAL
        right.addView(fingV, lp(WRAP, WRAP))
        right.addView(valve, lp(ctx.dpi(100f), ctx.dpi(34f)))
        row.addView(right, lp(WRAP, WRAP))
        addView(row, lp(MATCH, WRAP).also { it.topMargin = ctx.dpi(12f) })

        val acc = LinearLayout(ctx)
        acc.gravity = Gravity.CENTER
        val chips = ArrayList<Chip>()
        val labels = arrayOf("Bemol", "Natural", "Sostenido")
        val vals = intArrayOf(-1, 0, 1)
        for (i in 0 until 3) {
            val ch = Chip(ctx, labels[i]); ch.setActive(vals[i] == alter)
            ch.tap { alter = vals[i]; for ((k, c) in chips.withIndex()) c.setActive(k == i); update(true) }
            chips.add(ch)
            acc.addView(ch, lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(4f), 0, ctx.dpi(4f), 0) })
        }
        addView(acc, lp(MATCH, WRAP).also { it.topMargin = ctx.dpi(12f) })
        update(false)
    }

    private fun midiOf(): Int {
        val d = pos + 30
        val oct = Math.floorDiv(d, 7)
        val letter = Math.floorMod(d, 7)
        return 12 * (oct + 1) + SEMI[letter] + alter
    }

    private fun update(sound: Boolean) {
        val m = midiOf()
        val letters = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
        val d = pos + 30
        val nm = letters[Math.floorMod(d, 7)] + (if (alter == 1) "#" else if (alter == -1) "b" else "") + Math.floorDiv(d, 7)
        nameV.text = nm
        val f = Fingering.forMidi(m)
        fingV.text = f ?: "?"
        valve.set(f)
        infoV.text = if (f == null) "Fuera del rango del bajo" else "Pistones: $f"
        staff.pos = pos; staff.alter = alter
        staff.invalidate()
        if (sound && f != null) host.synth.beep(midiToFreq((m - 2).toDouble()))
    }

    private inner class Staff(ctx: Context) : View(ctx) {
        var pos = 5
        var alter = 0
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun sp() = min(height / 11f, width / 14f)

        override fun onDraw(c: Canvas) {
            val sp = sp()
            val cx = width / 2f
            val top = height / 2f - sp * 2f
            val bottomY = top + sp * 4
            p.style = Paint.Style.STROKE
            p.strokeWidth = sp * 0.1f
            p.color = C.MUTED
            for (k in 0 until 5) c.drawLine(sp, top + k * sp, width - sp, top + k * sp, p)
            p.style = Paint.Style.FILL
            p.typeface = Fonts.music
            p.textSize = sp * 4
            p.color = C.TEXT
            p.textAlign = Paint.Align.LEFT
            c.drawText("\uE050", sp * 1.2f, top + 3 * sp, p)
            val y = bottomY - pos * sp / 2f
            p.style = Paint.Style.STROKE
            p.color = C.MUTED
            if (pos <= -2) { var q = -2; while (q >= pos) { c.drawLine(cx - sp * 1.2f, bottomY - q * sp / 2f, cx + sp * 1.2f, bottomY - q * sp / 2f, p); q -= 2 } }
            if (pos >= 10) { var q = 10; while (q <= pos) { c.drawLine(cx - sp * 1.2f, bottomY - q * sp / 2f, cx + sp * 1.2f, bottomY - q * sp / 2f, p); q += 2 } }
            p.style = Paint.Style.FILL
            p.color = C.RED_HI
            c.drawText("\uE0A4", cx - sp * 0.59f, y, p)
            if (alter != 0) c.drawText(if (alter == 1) "\uE262" else "\uE260", cx - sp * 1.9f, y, p)
            p.color = C.TEXT
            val up = pos < 4
            val sx = if (up) cx + sp * 0.59f - sp * 0.06f else cx - sp * 0.59f + sp * 0.06f
            p.style = Paint.Style.STROKE
            p.strokeWidth = sp * 0.12f
            p.color = C.RED_HI
            c.drawLine(sx, y, sx, if (up) y - sp * 3.5f else y + sp * 3.5f, p)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val sp = sp()
            val top = height / 2f - sp * 2f
            val bottomY = top + sp * 4
            val np = ((bottomY - e.y) / (sp / 2f)).roundToInt().coerceIn(-4, 12)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (np != this@ExplorerView.pos) { this@ExplorerView.pos = np; this@ExplorerView.update(false) }
                }
                MotionEvent.ACTION_UP -> { this@ExplorerView.pos = np; this@ExplorerView.update(true); performClick() }
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()
    }
}

// ====================== INICIO ======================

class HomeScreen(private val host: Host, private val open: (SongInfo, String, Int) -> Unit) : FrameLayout(host as Context) {
    private val ctx: Context = host as Context
    private val songs = Library.index(ctx)
    private val content = FrameLayout(ctx)
    private val detail = FrameLayout(ctx)
    private val navItems = ArrayList<LinearLayout>()
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private var tab = host.prefs.getInt("tab", 0).coerceIn(0, 1)
    private var metro: MetronomeTab? = null
    private var tuner: TunerTab? = null

    init {
        setBackgroundColor(C.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(MATCH, MATCH))

        val header = LinearLayout(ctx)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(ctx.dpi(16f), ctx.dpi(10f), ctx.dpi(16f), ctx.dpi(10f))
        header.addView(logoView(ctx, 28f))
        header.addView(View(ctx), lp(0, 1, 1f))
        header.addView(label(ctx, "ART STAR", 15f, C.MUTED, Fonts.bold))
        root.addView(header, lp(MATCH, WRAP))
        root.addView(content, lp(MATCH, 0, 1f))

        val nav = LinearLayout(ctx)
        nav.setBackgroundColor(C.BG)
        val names = arrayOf("Partituras", "Aprender", "Metrónomo", "Afinador")
        val kinds = intArrayOf(IconView.NOTE, IconView.LEARN, IconView.METRO, IconView.TUNER)
        for (i in 0 until 4) {
            val item = LinearLayout(ctx)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            val ic = IconView(ctx, kinds[i])
            item.addView(ic, lp(ctx.dpi(30f), ctx.dpi(30f)))
            val tv = label(ctx, names[i], 11.5f, C.MUTED, Fonts.medium)
            tv.setPadding(0, ctx.dpi(3f), 0, 0)
            item.addView(tv)
            item.tap { select(i) }
            navItems.add(item); navIcons.add(ic); navLabels.add(tv)
            nav.addView(item, lp(0, ctx.dpi(60f), 1f))
        }
        root.addView(nav, lp(MATCH, WRAP))
        addView(detail, LayoutParams(MATCH, MATCH))
        detail.visibility = GONE
        select(tab)
    }

    private fun select(i: Int) {
        if (i > 1) { /* pestañas con micrófono o audio no se recuerdan */ } else host.prefs.edit().putInt("tab", i).apply()
        tab = i
        hideDetail()
        metro?.deactivate()
        tuner?.deactivate()
        content.removeAllViews()
        when (i) {
            0 -> content.addView(LibraryTab(host, songs, open), LayoutParams(MATCH, MATCH))
            1 -> content.addView(LearnTab(host, songs, open) { t, v -> showDetail(t, v) }, LayoutParams(MATCH, MATCH))
            2 -> { val m = MetronomeTab(host); metro = m; content.addView(m, LayoutParams(MATCH, MATCH)); m.activate() }
            3 -> { val t = TunerTab(host); tuner = t; content.addView(t, LayoutParams(MATCH, MATCH)); t.activate() }
        }
        for (k in 0 until 4) {
            val on = k == i
            navIcons[k].color = if (on) C.RED_HI else C.MUTED
            navLabels[k].setTextColor(if (on) C.TEXT else C.MUTED)
        }
    }

    private fun showDetail(title: String, v: View) {
        detail.removeAllViews()
        detail.addView(DetailFrame(ctx, title, v) { hideDetail() }, LayoutParams(MATCH, MATCH))
        detail.visibility = VISIBLE
    }

    private fun hideDetail() {
        detail.visibility = GONE
        detail.removeAllViews()
    }

    fun handleBack(): Boolean {
        if (detail.visibility == VISIBLE) { hideDetail(); return true }
        if (tab != 0) { select(0); return true }
        return false
    }

    fun onLeave() {
        metro?.deactivate()
        tuner?.deactivate()
    }
}
