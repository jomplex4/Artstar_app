package com.digitalminds.artstar

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
    companion object { const val TIE = 0; const val SLUR = 1; const val TUPLET = 2; const val VOLTA = 3; const val REPEAT = 4; const val HAIRPIN = 5; const val HAIRPIN_CLOSE = 6 }
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
            HAIRPIN_CLOSE -> {
                p.style = Paint.Style.STROKE
                c.drawLine(cx - u * 26, cy - u * 9, cx + u * 26, cy, p)
                c.drawLine(cx - u * 26, cy + u * 9, cx + u * 26, cy, p)
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

fun scrollOf(ctx: Context, build: (LinearLayout) -> Unit): FastScrollView {
    val fs = FastScrollView(ctx)
    val col = LinearLayout(ctx)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(ctx.dpi(16f), ctx.dpi(6f), ctx.dpi(30f), ctx.dpi(24f))
    build(col)
    fs.setContent(col)
    return fs
}

/** Selector segmentado estilo COMET: fondo grafito y pastilla roja activa. */
fun segmented(ctx: Context, names: List<String>, selected: Int, textSp: Float, padV: Float, onPick: (Int) -> Unit): LinearLayout {
    val pill = LinearLayout(ctx)
    pill.background = roundRect(C.CARD2, ctx.dp(24f))
    pill.setPadding(ctx.dpi(3f), ctx.dpi(3f), ctx.dpi(3f), ctx.dpi(3f))
    for ((i, n) in names.withIndex()) {
        val t = label(ctx, n, textSp, if (i == selected) C.TEXT else C.MUTED, if (i == selected) Fonts.bold else Fonts.medium)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setPadding(ctx.dpi(4f), ctx.dpi(padV), ctx.dpi(4f), ctx.dpi(padV))
        if (i == selected) t.background = roundRect(C.RED, ctx.dp(20f))
        t.tap { onPick(i) }
        pill.addView(t, lp(0, WRAP, 1f))
    }
    return pill
}

/** Fila de pastillas de seleccion (una activa). */
fun pickRow(ctx: Context, names: List<String>, selected: Int, onPick: (Int) -> Unit): View {
    val sc = HorizontalScrollView(ctx)
    sc.isHorizontalScrollBarEnabled = false
    val r = LinearLayout(ctx)
    val chips = ArrayList<Chip>()
    for ((i, n) in names.withIndex()) {
        val c = Chip(ctx, n); c.setActive(i == selected)
        c.tap { for ((k, x) in chips.withIndex()) x.setActive(k == i); onPick(i) }
        chips.add(c)
        r.addView(c, lp(WRAP, WRAP).also { it.setMargins(0, 0, ctx.dpi(6f), 0) })
    }
    sc.addView(r)
    return sc
}

fun soundOf(inst: InstDef, writtenMidi: Int): Int = writtenMidi + inst.transpose

// ====================== BIBLIOTECA ======================

class LibraryTab(private val host: Host, private val songs: List<SongInfo>, private val open: (SongInfo, String, Int) -> Unit) : LinearLayout(host as Context) {
    private val ctx: Context = host as Context
    private var instIdx = host.prefs.getInt("lib_inst", 0).coerceIn(0, 4)
    private var voice = host.prefs.getInt("lib_voice", 0).coerceIn(0, 1)

    init {
        orientation = VERTICAL
        build()
    }

    private fun partFor(s: SongInfo): PartInfo? {
        val list = s.partsOf(Insts.practice[instIdx].id)
        if (list.isEmpty()) return null
        return list.getOrNull(voice) ?: list[0]
    }

    private fun build() {
        removeAllViews()
        val list = songs.filter { it.cat == "song" }
        val head = LinearLayout(ctx)
        head.orientation = VERTICAL
        head.setPadding(ctx.dpi(16f), ctx.dpi(2f), ctx.dpi(16f), ctx.dpi(6f))
        head.addView(segmented(ctx, listOf("Bajo", "Trompeta", "Saxo", "Trombón", "Tuba"), instIdx, 12.5f, 9f) {
            instIdx = it; host.prefs.edit().putInt("lib_inst", it).apply(); build()
        })
        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(label(ctx, "${list.size} marchas", 16f, C.TEXT, Fonts.bold), lp(0, WRAP, 1f))
        row.addView(segmented(ctx, listOf("1ª voz", "2ª voz"), voice, 12.5f, 6f) {
            voice = it; host.prefs.edit().putInt("lib_voice", it).apply(); build()
        }, lp(ctx.dpi(150f), WRAP))
        head.addView(row, lp(MATCH, WRAP).also { it.topMargin = ctx.dpi(10f) })
        addView(head, lp(MATCH, WRAP))
        addView(scrollOf(ctx) { col ->
            for (s in list) {
                val p = partFor(s)
                val c = card(ctx)
                val ic = FrameLayout(ctx)
                ic.background = roundRect(C.CARD2, ctx.dp(14f))
                ic.addView(GlyphIcon(ctx, "\uE1D7", true, false, 30f), FrameLayout.LayoutParams(MATCH, MATCH))
                c.addView(ic, lp(ctx.dpi(52f), ctx.dpi(52f)))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.setPadding(ctx.dpi(14f), 0, ctx.dpi(8f), 0)
                tx.addView(label(ctx, s.title, 17f, C.TEXT, Fonts.bold))
                val sub = if (p != null) s.subtitle + " · " + s.meta + " · " + p.name else "Sin parte de " + Insts.practice[instIdx].short.lowercase()
                tx.addView(label(ctx, sub, 13f, C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
                c.addView(tx, lp(0, WRAP, 1f))
                val pl = FrameLayout(ctx)
                pl.background = roundRect(if (p != null) C.RED else C.CARD2, ctx.dp(20f))
                pl.addView(IconView(ctx, IconView.PLAY).also { it.color = if (p != null) C.TEXT else C.DIM }, FrameLayout.LayoutParams(ctx.dpi(26f), ctx.dpi(26f), Gravity.CENTER))
                c.addView(pl, lp(ctx.dpi(40f), ctx.dpi(40f)))
                if (p != null) c.tap { open(s, p.id, 0) } else c.alpha = 0.55f
                col.addView(c, cardLp(ctx))
            }
            col.addView(label(ctx, "Dentro de cada marcha puedes practicar por secciones y activar la banda: bombo, tarola, napoleón, platillo, pandereta, lira y los demás instrumentos de viento.", 12f, C.DIM).also { it.setPadding(ctx.dpi(2f), ctx.dpi(8f), ctx.dpi(2f), 0) })
        }, lp(MATCH, 0, 1f))
    }
}

// ====================== APRENDER ======================

class LearnTab(private val host: Host, private val songs: List<SongInfo>, private val open: (SongInfo, String, Int) -> Unit, private val push: (String, View) -> Unit) : LinearLayout(host as Context) {
    private val ctx: Context = host as Context

    init {
        orientation = VERTICAL
        val head = LinearLayout(ctx)
        head.orientation = VERTICAL
        head.setPadding(ctx.dpi(18f), ctx.dpi(4f), ctx.dpi(18f), ctx.dpi(8f))
        head.addView(label(ctx, "Aprende a leer partitura", 22f, C.TEXT, Fonts.bold))
        head.addView(label(ctx, "Herramientas primero, luego los ejercicios por grupos.", 13f, C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
        addView(head, lp(MATCH, WRAP))
        addView(scrollOf(ctx) { col ->
            fun entry(glyph: String, music: Boolean, title: String, text: String, action: () -> Unit) {
                val c = card(ctx)
                val ic = FrameLayout(ctx)
                ic.background = roundRect(C.CARD2, ctx.dp(14f))
                ic.addView(GlyphIcon(ctx, glyph, music, false, if (music) 30f else 20f), FrameLayout.LayoutParams(MATCH, MATCH))
                c.addView(ic, lp(ctx.dpi(52f), ctx.dpi(52f)))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.setPadding(ctx.dpi(14f), 0, ctx.dpi(6f), 0)
                tx.addView(label(ctx, title, 16f, C.TEXT, Fonts.bold))
                tx.addView(label(ctx, text, 12.5f, C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
                c.addView(tx, lp(0, WRAP, 1f))
                c.addView(IconView(ctx, IconView.CHEVRON_RIGHT).also { it.color = C.MUTED }, lp(ctx.dpi(22f), ctx.dpi(22f)))
                c.tap(action)
                col.addView(c, cardLp(ctx))
            }
            entry("\uE050", true, "Explorador de notas", "Sube y baja nota por nota con las flechas, en tu instrumento, y escúchala.") { push("Explorador de notas", ExplorerView(host)) }
            entry("\uE1D5", true, "Símbolos de la partitura", "Cada figura, silencio y signo, uno por uno.") { push("Símbolos", symbolsView()) }
            entry("0 1 2", false, "Tabla de digitación", "Pistones, vara y llaves de cada instrumento, con sonido y las marchas donde se usa cada nota.") { push("Tabla de digitación", ChartView(host)) }

            val lessons = songs.filter { it.cat == "lesson" }
            val groups = LinkedHashMap<String, ArrayList<SongInfo>>()
            for (l in lessons) groups.getOrPut(l.group) { ArrayList() }.add(l)
            col.addView(label(ctx, "Ejercicios", 15f, C.MUTED, Fonts.medium).also { it.setPadding(ctx.dpi(2f), ctx.dpi(10f), 0, ctx.dpi(8f)) })
            for ((g, items) in groups) {
                val glyph = when (g) {
                    "Figuras largas" -> "\uE1D3"
                    "Figuras rápidas" -> "\uE1D9"
                    "Escalas" -> "\uE050"
                    else -> "\uE1D7"
                }
                entry(glyph, true, g, "${items.size} ejercicios: " + items.take(3).joinToString(", ") { it.title } + if (items.size > 3) "..." else "") { push(g, groupView(g, items)) }
            }
        }, lp(MATCH, 0, 1f))
    }

    /** Ventana propia de un grupo de ejercicios. */
    private fun groupView(group: String, items: List<SongInfo>): View = scrollOf(ctx) { col ->
        col.addView(label(ctx, "Toca un ejercicio para abrirlo y elegir cómo practicarlo.", 13f, C.MUTED).also { it.setPadding(ctx.dpi(2f), 0, 0, ctx.dpi(10f)) })
        for (l in items) {
            val c = card(ctx)
            val tx = LinearLayout(ctx)
            tx.orientation = LinearLayout.VERTICAL
            tx.addView(label(ctx, l.title, 16f, C.TEXT, Fonts.bold))
            tx.addView(label(ctx, l.meta + " · " + l.bars + " compases", 12.5f, C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
            c.addView(tx, lp(0, WRAP, 1f))
            val go = FrameLayout(ctx)
            go.background = roundRect(C.CARD2, ctx.dp(18f))
            go.addView(IconView(ctx, IconView.PLAY).also { it.color = C.RED_HI }, FrameLayout.LayoutParams(ctx.dpi(22f), ctx.dpi(22f), Gravity.CENTER))
            c.addView(go, lp(ctx.dpi(36f), ctx.dpi(36f)))
            c.tap { push(l.title, lessonView(l)) }
            col.addView(c, cardLp(ctx))
        }
    }

    private fun lessonView(info: SongInfo): View {
        val song = Library.load(ctx, info.id)
        var instIdx = host.prefs.getInt("lib_inst", 0).coerceIn(0, 4)
        return scrollOf(ctx) { col ->
            col.addView(label(ctx, info.group, 13f, C.MUTED, Fonts.medium))
            col.addView(label(ctx, song.desc, 16f, C.TEXT).also { it.setLineSpacing(0f, 1.2f); it.setPadding(0, ctx.dpi(8f), 0, ctx.dpi(14f)) })
            col.addView(label(ctx, "Compás ${song.num}/${song.den} · tempo ${song.bpm} · ${info.bars} compases", 13f, C.MUTED).also { it.setPadding(0, 0, 0, ctx.dpi(14f)) })
            col.addView(label(ctx, "Instrumento", 13f, C.MUTED, Fonts.medium).also { it.setPadding(0, 0, 0, ctx.dpi(6f)) })
            col.addView(pickRow(ctx, Insts.practice.map { it.name }, instIdx) { instIdx = it; host.prefs.edit().putInt("lib_inst", it).apply() },
                lp(MATCH, WRAP).also { it.bottomMargin = ctx.dpi(16f) })
            fun big(text: String, sub: String, mode: Int, primary: Boolean) {
                val c = card(ctx)
                if (primary) c.background = roundRect(C.RED, ctx.dp(18f))
                val tx = LinearLayout(ctx)
                tx.orientation = LinearLayout.VERTICAL
                tx.addView(label(ctx, text, 16f, C.TEXT, Fonts.bold))
                tx.addView(label(ctx, sub, 12.5f, if (primary) C.TEXT else C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
                c.addView(tx, lp(0, WRAP, 1f))
                c.tap {
                    val id = song.parts.firstOrNull { it.inst.id == Insts.practice[instIdx].id }?.id ?: song.parts[0].id
                    open(info, id, mode)
                }
                col.addView(c, cardLp(ctx))
            }
            big("Practicar", "La partitura se desplaza sola, con metrónomo y sonido real.", 0, true)
            big("Modo espera", "Avanza solo cuando tocas la nota correcta. Usa el micrófono.", 1, false)
            big("Evaluar", "Toca a tiempo y gana estrellas. Usa el micrófono.", 2, false)
        }
    }

    private fun symbolsView(): View = scrollOf(ctx) { col ->
        fun item(icon: View, title: String, text: String) {
            val c = card(ctx)
            c.addView(icon, lp(ctx.dpi(96f), ctx.dpi(90f)))
            val tx = LinearLayout(ctx)
            tx.orientation = LinearLayout.VERTICAL
            tx.setPadding(ctx.dpi(10f), 0, 0, 0)
            tx.addView(label(ctx, title, 17f, C.TEXT, Fonts.bold))
            tx.addView(label(ctx, text, 13f, C.MUTED).also { it.setLineSpacing(0f, 1.15f); it.setPadding(0, ctx.dpi(4f), 0, 0) })
            c.addView(tx, lp(0, WRAP, 1f))
            col.addView(c, cardLp(ctx))
        }
        fun g(code: String, size: Float = 46f) = GlyphIcon(ctx, code, true, false, size)
        item(g("\uE050", 50f), "Clave de sol", "Rodea la segunda línea del pentagrama, que es Sol. Las partes de bajo, barítono, trompeta y saxofón se leen en clave de sol.")
        item(GlyphIcon(ctx, "2/4", false, false, 30f), "Compás", "Arriba, cuántos tiempos tiene cada compás. Abajo, qué figura vale un tiempo (4 es la negra). Las barras verticales separan los compases.")
        item(g("\uE262"), "Sostenido", "Sube la nota medio tono. Si aparece al inicio, en la armadura, vale para toda la pieza.")
        item(g("\uE260"), "Bemol", "Baja la nota medio tono. En la armadura vale para todas las notas con ese nombre.")
        item(g("\uE261"), "Becuadro", "Cancela el sostenido o el bemol. La nota vuelve a ser natural hasta la siguiente barra.")
        item(g("\uE1D2"), "Redonda", "Dura 4 tiempos. Es la nota más larga de uso común.")
        item(g("\uE1D3"), "Blanca", "Dura 2 tiempos, la mitad de una redonda.")
        item(g("\uE1D5"), "Negra", "Dura 1 tiempo. Es la unidad que marca el metrónomo.")
        item(g("\uE1D7"), "Corchea", "Dura medio tiempo. Dos corcheas se unen con una barra.")
        item(g("\uE1D9"), "Semicorchea", "Dura un cuarto de tiempo. Cuatro semicorcheas llenan una negra. Lleva dos barras.")
        item(g("\uE1DB"), "Fusa", "Dura un octavo de tiempo. Lleva tres barras.")
        item(g("\uE1DD"), "Semifusa", "Dura un dieciseisavo de tiempo. Lleva cuatro barras. Aparece en pasajes muy rápidos.")
        item(g("\uE4E3"), "Silencio de redonda", "Silencio de 4 tiempos. Colgado de la cuarta línea, también indica un compás entero en silencio.")
        item(g("\uE4E4"), "Silencio de blanca", "Silencio de 2 tiempos. Se apoya sobre la tercera línea.")
        item(g("\uE4E5"), "Silencio de negra", "Silencio de 1 tiempo. Durante un silencio no soples, pero sigue contando.")
        item(g("\uE4E6"), "Silencio de corchea", "Silencio de medio tiempo.")
        item(g("\uE4E7"), "Silencio de semicorchea", "Silencio de un cuarto de tiempo.")
        item(g("\uE1D5\uE1E7"), "Puntillo", "Suma la mitad del valor. Una negra con puntillo dura un tiempo y medio.")
        item(ShapeIcon(ctx, ShapeIcon.TIE), "Ligadura de unión", "Une dos notas del mismo sonido: se tocan como una sola nota larga.")
        item(ShapeIcon(ctx, ShapeIcon.SLUR), "Ligadura de expresión", "Une notas distintas: tócalas seguidas y suaves, sin cortar el aire.")
        item(ShapeIcon(ctx, ShapeIcon.TUPLET), "Tresillo", "Tres notas que se tocan en el tiempo de dos. Cuenta: tre-si-llo.")
        item(g("\uE4A0"), "Acento", "Ataca esa nota con más fuerza que las demás.")
        item(g("\uE4A2"), "Staccato", "El punto pide una nota corta y separada.")
        item(g("\uE4C0"), "Calderón", "Alarga la nota o el silencio más de lo escrito. Lo decide el director.")
        item(ShapeIcon(ctx, ShapeIcon.REPEAT), "Barras de repetición", "Al llegar al signo de cierre, vuelve al signo de inicio y toca otra vez ese fragmento.")
        item(ShapeIcon(ctx, ShapeIcon.VOLTA), "Casillas 1 y 2", "Los puentes numerados. La primera vez tocas la casilla 1; al repetir, saltas a la 2.")
        item(g("\uE047"), "Segno", "Punto al que se vuelve cuando la partitura dice D.S. (del signo).")
        item(g("\uE048"), "Coda", "Un final aparte. En la señal de coda saltas a la sección marcada con el mismo signo.")
        item(GlyphIcon(ctx, "D.C.", false, false, 26f), "D.C. y Fine", "D.C. es volver al principio. Fine marca el final: \"D.C. al Fine\" es volver al inicio y terminar en Fine.")
        item(g("\uE520"), "Piano", "Suave.")
        item(g("\uE52D"), "Mezzoforte", "Medio fuerte: el volumen normal de la banda.")
        item(g("\uE522"), "Forte", "Fuerte.")
        item(g("\uE52F"), "Fortissimo", "Muy fuerte.")
        item(ShapeIcon(ctx, ShapeIcon.HAIRPIN), "Crescendo", "Las líneas que se abren piden subir el volumen poco a poco.")
        item(ShapeIcon(ctx, ShapeIcon.HAIRPIN_CLOSE), "Diminuendo", "Las líneas que se cierran piden bajar el volumen poco a poco.")
        item(GlyphIcon(ctx, "A", false, true, 22f), "Letras de ensayo", "Las letras en cuadro sirven para ubicarse cuando el director dice \"desde la B\".")
    }
}

// ====================== TABLA DE DIGITACION ======================

class ChartView(private val host: Host) : LinearLayout(host as Context) {
    private val ctx: Context = host as Context
    private val holder = FrameLayout(ctx)
    private var instIdx = host.prefs.getInt("lib_inst", 0).coerceIn(0, 4)
    private val usage: org.json.JSONObject? = try {
        org.json.JSONObject(ctx.assets.open("songs/usage.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    } catch (e: Exception) { null }

    init {
        orientation = VERTICAL
        addView(pickRow(ctx, Insts.practice.map { it.name }, instIdx) { instIdx = it; host.prefs.edit().putInt("lib_inst", it).apply(); fill() },
            lp(MATCH, WRAP).also { it.setMargins(ctx.dpi(16f), 0, ctx.dpi(16f), ctx.dpi(8f)) })
        addView(holder, lp(MATCH, 0, 1f))
        fill()
    }

    private fun landscape() = ctx.resources.displayMetrics.widthPixels > ctx.resources.displayMetrics.heightPixels

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post { fill() }
    }

    override fun onDetachedFromWindow() {
        host.setOrientation(false)
        super.onDetachedFromWindow()
    }

    private fun fill() {
        val inst = Insts.practice[instIdx]
        // el saxofón tiene muchos puntos: se muestra en horizontal para verlos bien
        host.setOrientation(inst.fam == 1)
        holder.removeAllViews()
        val land = landscape()
        holder.addView(scrollOf(ctx) { list ->
            val info = when (inst.fam) {
                1 -> "Saxo alto en Mi bemol, nota escrita. El 8 es la llave de octava, 1 2 3 la mano izquierda y 4 5 6 la derecha. Toca una fila para escucharla."
                3 -> "Trombón en clave de fa. El número es la posición de la vara, de 1 (recogida) a 7 (extendida). Toca una fila para escucharla."
                4 -> "Tuba en Si bemol en clave de fa. Con 0 no presionas ningún pistón. Toca una fila para escucharla."
                else -> "Nota escrita en clave de sol para instrumento en Si bemol. Con 0 no presionas nada. Toca una fila para escucharla."
            }
            list.addView(label(ctx, info, 13f, C.MUTED).also { it.setPadding(0, 0, 0, ctx.dpi(10f)) })
            val used = usage?.optJSONObject(inst.id)
            for (m in inst.high downTo inst.low) {
                val f = Fingering.label(inst, m) ?: continue
                val row = LinearLayout(ctx)
                row.gravity = Gravity.CENTER_VERTICAL
                row.background = roundRect(C.CARD, ctx.dp(12f))
                row.setPadding(ctx.dpi(14f), ctx.dpi(8f), ctx.dpi(14f), ctx.dpi(8f))
                val left = LinearLayout(ctx)
                left.orientation = VERTICAL
                val top = LinearLayout(ctx)
                top.gravity = Gravity.CENTER_VERTICAL
                top.addView(label(ctx, Names.withOctave(m), 16f, C.TEXT, Fonts.bold), lp(ctx.dpi(76f), WRAP))
                top.addView(label(ctx, if (inst.fam == 3) "Posición $f" else f, if (inst.fam == 1) 12f else 15f, C.RED_HI, Fonts.bold))
                left.addView(top)
                val songsOf = used?.optJSONArray(m.toString())
                val names = ArrayList<String>()
                if (songsOf != null) for (k in 0 until songsOf.length()) names.add(songsOf.getString(k))
                left.addView(label(ctx, if (names.isEmpty()) "No aparece en las marchas" else "Marchas: " + names.joinToString(", "), 11.5f, if (names.isEmpty()) C.DIM else C.MUTED).also { it.setPadding(0, ctx.dpi(3f), 0, 0) })
                row.addView(left, lp(0, WRAP, 1f))
                val fv = FingerView(ctx); fv.set(inst, m)
                val w = when {
                    inst.fam == 1 && land -> ctx.dpi(330f)
                    inst.fam == 1 -> ctx.dpi(170f)
                    inst.fam == 3 -> ctx.dpi(150f)
                    else -> ctx.dpi(96f)
                }
                val h = if (inst.fam == 1) (if (land) ctx.dpi(64f) else ctx.dpi(40f)) else ctx.dpi(34f)
                row.addView(fv, lp(w, h))
                row.tap { host.synth.play(inst.sample, soundOf(inst, m)) }
                list.addView(row, lp(MATCH, WRAP).also { it.setMargins(0, 0, 0, ctx.dpi(6f)) })
            }
        }, FrameLayout.LayoutParams(MATCH, MATCH))
    }
}

// ====================== EXPLORADOR DE NOTAS ======================

class ExplorerView(private val host: Host) : LinearLayout(host as Context) {
    private val ctx: Context = host as Context
    private var pos = 5
    private var alter = 0
    private var instIdx = host.prefs.getInt("lib_inst", 0).coerceIn(0, 4)
    private val staff = Staff(ctx)
    private val nameV = label(ctx, "", 32f, C.TEXT, Fonts.bold)
    private val infoV = label(ctx, "", 13f, C.MUTED)
    private val finger = FingerView(ctx)
    private val accChips = ArrayList<Chip>()
    private val SEMI = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val minPos = -9
    private val maxPos = 17

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(16f), 0, ctx.dpi(16f), ctx.dpi(14f))
        addView(pickRow(ctx, Insts.practice.map { it.name }, instIdx) { instIdx = it; host.prefs.edit().putInt("lib_inst", it).apply(); update(true) })
        val mid = LinearLayout(ctx)
        mid.gravity = Gravity.CENTER_VERTICAL
        mid.addView(staff, lp(0, MATCH, 1f))
        val arrows = LinearLayout(ctx)
        arrows.orientation = VERTICAL
        arrows.gravity = Gravity.CENTER
        fun arrow(kind: Int, d: Int) {
            val b = FrameLayout(ctx)
            b.background = roundRect(C.CARD2, ctx.dp(18f))
            b.addView(IconView(ctx, kind).also { it.color = C.RED_HI }, FrameLayout.LayoutParams(ctx.dpi(34f), ctx.dpi(34f), Gravity.CENTER))
            b.tap { move(d) }
            arrows.addView(b, lp(ctx.dpi(64f), ctx.dpi(64f)).also { it.setMargins(0, ctx.dpi(6f), 0, ctx.dpi(6f)) })
        }
        arrow(IconView.UP, 1)
        arrow(IconView.DOWN, -1)
        mid.addView(arrows, lp(WRAP, MATCH))
        addView(mid, lp(MATCH, 0, 1f).also { it.topMargin = ctx.dpi(8f) })

        val row = LinearLayout(ctx)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = roundRect(C.CARD, ctx.dp(18f))
        row.setPadding(ctx.dpi(16f), ctx.dpi(12f), ctx.dpi(16f), ctx.dpi(12f))
        val left = LinearLayout(ctx)
        left.orientation = VERTICAL
        left.addView(nameV)
        left.addView(infoV)
        row.addView(left, lp(0, WRAP, 1f))
        row.addView(finger, lp(ctx.dpi(150f), ctx.dpi(44f)))
        row.tap { update(true) }
        addView(row, lp(MATCH, WRAP).also { it.topMargin = ctx.dpi(10f) })

        val acc = LinearLayout(ctx)
        acc.gravity = Gravity.CENTER
        val labels = arrayOf("Bemol", "Natural", "Sostenido")
        val vals = intArrayOf(-1, 0, 1)
        for (i in 0 until 3) {
            val ch = Chip(ctx, labels[i]); ch.setActive(vals[i] == alter)
            ch.tap { alter = vals[i]; refreshAcc(); update(true) }
            accChips.add(ch)
            acc.addView(ch, lp(WRAP, WRAP).also { it.setMargins(ctx.dpi(4f), 0, ctx.dpi(4f), 0) })
        }
        addView(acc, lp(MATCH, WRAP).also { it.topMargin = ctx.dpi(10f) })
        update(false)
    }

    private fun refreshAcc() { val vals = intArrayOf(-1, 0, 1); for ((i, c) in accChips.withIndex()) c.setActive(vals[i] == alter) }

    private fun move(d: Int) {
        pos = (pos + d).coerceIn(minPos, maxPos)
        update(true)
    }

    private fun baseDia() = if (Insts.practice[instIdx].bass) 18 else 30

    private fun midiOf(): Int {
        val d = pos + baseDia()
        return 12 * (Math.floorDiv(d, 7) + 1) + SEMI[Math.floorMod(d, 7)] + alter
    }

    private fun update(sound: Boolean) {
        val inst = Insts.practice[instIdx]
        val m = midiOf()
        val letters = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
        val d = pos + baseDia()
        nameV.text = letters[Math.floorMod(d, 7)] + (if (alter == 1) "#" else if (alter == -1) "b" else "") + Math.floorDiv(d, 7)
        val f = Fingering.label(inst, m)
        finger.set(inst, m)
        val sounds = Names.withOctave(soundOf(inst, m))
        infoV.text = if (f == null) "Registro extremo del ${inst.short.lowercase()}, sin digitación fija · suena $sounds" else (when (inst.fam) { 1 -> "Llaves: $f"; 3 -> "Posición de vara: $f"; else -> "Pistones: $f" }) + " · suena $sounds"
        staff.invalidate()
        if (sound) host.synth.play(inst.sample, soundOf(inst, m))
    }

    private inner class Staff(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun sp() = min(height / 17f, width / 10f)

        override fun onDraw(c: Canvas) {
            val sp = sp()
            val cx = width * 0.58f
            val top = height / 2f - sp * 2f
            val bottomY = top + sp * 4
            p.style = Paint.Style.STROKE
            p.strokeWidth = sp * 0.1f
            p.color = C.MUTED
            for (k in 0 until 5) c.drawLine(sp * 0.3f, top + k * sp, width - sp * 0.3f, top + k * sp, p)
            p.style = Paint.Style.FILL
            p.typeface = Fonts.music
            p.textSize = sp * 4
            p.color = C.TEXT
            p.textAlign = Paint.Align.LEFT
            if (Insts.practice[instIdx].bass) c.drawText("\uE062", sp * 0.5f, top + sp, p) else c.drawText("\uE050", sp * 0.5f, top + 3 * sp, p)
            val y = bottomY - this@ExplorerView.pos * sp / 2f
            p.style = Paint.Style.STROKE
            p.color = C.MUTED
            val pp = this@ExplorerView.pos
            if (pp <= -2) { var q = -2; while (q >= pp) { c.drawLine(cx - sp * 1.2f, bottomY - q * sp / 2f, cx + sp * 1.2f, bottomY - q * sp / 2f, p); q -= 2 } }
            if (pp >= 10) { var q = 10; while (q <= pp) { c.drawLine(cx - sp * 1.2f, bottomY - q * sp / 2f, cx + sp * 1.2f, bottomY - q * sp / 2f, p); q += 2 } }
            p.style = Paint.Style.FILL
            p.color = C.RED_HI
            c.drawText("\uE0A4", cx - sp * 0.59f, y, p)
            val al = this@ExplorerView.alter
            if (al != 0) c.drawText(if (al == 1) "\uE262" else "\uE260", cx - sp * 1.9f, y, p)
            val up = pp < 4
            val sx = if (up) cx + sp * 0.53f else cx - sp * 0.53f
            p.style = Paint.Style.STROKE
            p.strokeWidth = sp * 0.12f
            c.drawLine(sx, y, sx, if (up) y - sp * 3.5f else y + sp * 3.5f, p)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val sp = sp()
            val bottomY = height / 2f + sp * 2f
            val np = ((bottomY - e.y) / (sp / 2f)).roundToInt().coerceIn(minPos, maxPos)
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                this@ExplorerView.pos = np; update(true); performClick()
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
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private var tab = host.prefs.getInt("tab", 0).coerceIn(0, 1)
    private var metro: MetronomeTab? = null
    private var tuner: TunerTab? = null
    private val stack = ArrayList<Pair<String, View>>()

    init {
        setBackgroundColor(C.BG)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        addView(root, LayoutParams(MATCH, MATCH))

        val header = LinearLayout(ctx)
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(ctx.dpi(18f), ctx.dpi(12f), ctx.dpi(18f), ctx.dpi(8f))
        header.addView(logoView(ctx, 22f))
        header.addView(View(ctx), lp(0, 1, 1f))
        val about = FrameLayout(ctx)
        about.background = roundRect(C.MUTED, ctx.dp(14f))
        about.addView(label(ctx, "i", 15f, C.BG, Fonts.bold), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        about.tap { showAbout() }
        header.addView(about, lp(ctx.dpi(28f), ctx.dpi(28f)))
        root.addView(header, lp(MATCH, WRAP))
        root.addView(content, lp(MATCH, 0, 1f))

        val nav = LinearLayout(ctx)
        nav.setPadding(0, ctx.dpi(4f), 0, ctx.dpi(6f))
        val names = arrayOf("Partituras", "Aprender", "Metrónomo", "Afinador")
        val kinds = intArrayOf(IconView.NOTE, IconView.LEARN, IconView.METRO, IconView.TUNER)
        for (i in 0 until 4) {
            val item = LinearLayout(ctx)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            val ic = IconView(ctx, kinds[i])
            item.addView(ic, lp(ctx.dpi(30f), ctx.dpi(30f)))
            val tv = label(ctx, names[i], 11.5f, C.MUTED, Fonts.medium)
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setPadding(0, ctx.dpi(3f), 0, 0)
            item.addView(tv, lp(MATCH, WRAP))
            item.tap { select(i) }
            navIcons.add(ic); navLabels.add(tv)
            nav.addView(item, lp(0, ctx.dpi(58f), 1f))
        }
        root.addView(nav, lp(MATCH, WRAP))
        addView(detail, LayoutParams(MATCH, MATCH))
        detail.visibility = GONE
        select(tab)
    }

    private fun showAbout() {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sv = ScrollView(ctx)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ctx.dpi(22f), ctx.dpi(22f), ctx.dpi(22f), ctx.dpi(16f))
        box.background = roundRect(C.CARD, ctx.dp(24f))
        sv.addView(box)

        val top = LinearLayout(ctx)
        top.gravity = Gravity.CENTER_VERTICAL
        val ic = ImageView(ctx)
        ic.setImageBitmap(ctx.assets.open("splash_icon.png").use { BitmapFactory.decodeStream(it) })
        top.addView(ic, lp(ctx.dpi(56f), ctx.dpi(56f)))
        val tt = LinearLayout(ctx)
        tt.orientation = LinearLayout.VERTICAL
        tt.setPadding(ctx.dpi(14f), 0, 0, 0)
        tt.addView(label(ctx, "ART STAR", 22f, C.TEXT, Fonts.bold))
        tt.addView(label(ctx, "Versión " + BuildConfigLite.VERSION, 13f, C.MUTED).also { it.setPadding(0, ctx.dpi(2f), 0, 0) })
        top.addView(tt, lp(0, WRAP, 1f))
        box.addView(top)

        fun title(t: String) { box.addView(label(ctx, t, 12f, C.RED_HI, Fonts.bold).also { it.letterSpacing = 0.12f; it.setPadding(0, ctx.dpi(18f), 0, ctx.dpi(6f)) }) }
        fun body(t: String) { box.addView(label(ctx, t, 14f, C.TEXT).also { it.setLineSpacing(0f, 1.2f) }) }

        title("QUÉ ES")
        body("Una app para aprender y practicar las marchas de la banda escolar y militar. La partitura se desplaza y escribe sobre cada nota los pistones, la vara o las llaves que debes usar.")
        title("PUNTOS CLAVE")
        for (k in listOf(
            "Marchas con voces para bajo, trompeta, saxo, trombón y tuba.",
            "Banda de acompañamiento con instrumentos reales y percusión de marcha.",
            "Modo espera y modo evaluar con el micrófono.",
            "Metrónomo con subdivisiones y afinador con medidor de nivel.",
            "Lecciones, símbolos, tablas de digitación y 17 canciones.",
            "Funciona sin internet y sin anuncios."
        )) {
            val r = LinearLayout(ctx)
            r.gravity = Gravity.TOP
            r.addView(View(ctx).also { it.background = roundRect(C.RED, ctx.dp(3f)) }, lp(ctx.dpi(6f), ctx.dpi(6f)).also { it.setMargins(0, ctx.dpi(8f), ctx.dpi(10f), 0) })
            r.addView(label(ctx, k, 14f, C.TEXT).also { it.setLineSpacing(0f, 1.15f) }, lp(0, WRAP, 1f))
            box.addView(r, lp(MATCH, WRAP).also { it.bottomMargin = ctx.dpi(4f) })
        }
        title("CREADOR")
        body("Joel Mamani Pauccara")
        title("CRÉDITOS")
        body("Sonidos de instrumentos: FluidR3 GM (Frank Wen), licencia CC BY 3.0. Tipografías: Space Grotesk y Bravura, licencia SIL OFL.")

        val sep = View(ctx)
        sep.setBackgroundColor(C.CARD2)
        box.addView(sep, lp(MATCH, ctx.dpi(1f)).also { it.topMargin = ctx.dpi(18f) })
        val pb = label(ctx, "POWERED BY DIGITALMINDS", 12f, C.MUTED, Fonts.medium)
        pb.letterSpacing = 0.25f
        pb.gravity = Gravity.CENTER
        pb.setPadding(0, ctx.dpi(14f), 0, ctx.dpi(8f))
        box.addView(pb, lp(MATCH, WRAP))
        val ok = FrameLayout(ctx)
        ok.background = roundRect(C.RED, ctx.dp(22f))
        ok.addView(label(ctx, "Cerrar", 15f, C.TEXT, Fonts.bold), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        ok.tap { d.dismiss() }
        box.addView(ok, lp(MATCH, ctx.dpi(46f)).also { it.topMargin = ctx.dpi(6f) })

        d.setContentView(sv)
        d.window?.setBackgroundDrawable(ColorDrawable(0))
        val dm = ctx.resources.displayMetrics
        d.window?.setLayout((dm.widthPixels * 0.9f).toInt().coerceAtMost(ctx.dpi(460f)), (dm.heightPixels * 0.88f).toInt())
        d.show()
    }

    private fun select(i: Int) {
        if (i <= 1) host.prefs.edit().putInt("tab", i).apply()
        tab = i
        hideDetail()
        metro?.deactivate(); tuner?.deactivate()
        content.removeAllViews()
        when (i) {
            0 -> content.addView(LibraryTab(host, songs, open), LayoutParams(MATCH, MATCH))
            1 -> content.addView(LearnTab(host, songs, open) { t, v -> showDetail(t, v) }, LayoutParams(MATCH, MATCH))
            2 -> { val m = MetronomeTab(host); metro = m; content.addView(m, LayoutParams(MATCH, MATCH)); m.activate() }
            3 -> { val t = TunerTab(host); tuner = t; content.addView(t, LayoutParams(MATCH, MATCH)); t.activate() }
        }
        for (k in 0 until 4) {
            navIcons[k].color = if (k == i) C.RED_HI else C.MUTED
            navLabels[k].setTextColor(if (k == i) C.TEXT else C.MUTED)
        }
    }

    private fun showDetail(title: String, v: View) {
        stack.add(Pair(title, v))
        renderDetail()
    }

    private fun renderDetail() {
        detail.removeAllViews()
        val top = stack.lastOrNull()
        if (top == null) { detail.visibility = GONE; return }
        detail.addView(DetailFrame(ctx, top.first, top.second) { back() }, LayoutParams(MATCH, MATCH))
        detail.visibility = VISIBLE
    }

    private fun back() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        renderDetail()
    }

    private fun hideDetail() { stack.clear(); detail.removeAllViews(); detail.visibility = GONE }

    fun handleBack(): Boolean {
        if (stack.isNotEmpty()) { back(); return true }
        if (tab != 0) { select(0); return true }
        return false
    }

    fun onLeave() { metro?.deactivate(); tuner?.deactivate() }
}

object BuildConfigLite { const val VERSION = "1.5" }
