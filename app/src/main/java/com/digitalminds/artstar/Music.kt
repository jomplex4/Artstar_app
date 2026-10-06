package com.digitalminds.artstar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una nota o silencio. dia = octava*7 + letra (Do=0), E4 = 30. Duracion en ticks (negra = 48). */
class Note(val dia: Int, val alter: Int, val show: Int, val midi: Int, val dur: Int, val flags: Int) {
    val isRest get() = flags and 1 != 0
    val accent get() = flags and 2 != 0
    val staccato get() = flags and 4 != 0
    val tie get() = flags and 8 != 0
    val triplet get() = flags and 16 != 0
    val fullRest get() = flags and 32 != 0

    /** Posicion en el pentagrama: 0 = linea inferior (Mi4), 2 = Sol4, 4 = Si4 (linea central). */
    val pos get() = dia - 30

    /** Duracion nominal (sin tresillo). */
    val nominal get() = if (triplet) dur * 3 / 2 else dur
}

class Measure(val n: Int, val f: Int, val text: String, val rehearsal: String, val notes: List<Note>) {
    val repeatStart get() = f and 1 != 0
    val repeatEnd get() = f and 2 != 0
    val volta1 get() = f and 4 != 0
    val volta2 get() = f and 8 != 0
    val segno get() = f and 16 != 0
    val coda get() = f and 32 != 0
}

class Part(val id: String, val name: String, val order: List<Int>, val measures: List<Measure>) {
    val byNumber: Map<Int, Measure> = measures.associateBy { it.n }
}

class Section(val name: String, val order: List<Int>)

class Song(
    val id: String, val title: String, val subtitle: String, val meta: String, val cat: String,
    val group: String, val desc: String, val num: Int, val den: Int, val key: Int, val bpm: Int,
    val sections: List<Section>, val parts: List<Part>
) {
    val measureTicks get() = num * (192 / den)
    val beatTicks get() = if (den == 8) 72 else 192 / den
}

class SongInfo(
    val id: String, val title: String, val subtitle: String, val meta: String, val cat: String,
    val group: String, val parts: List<Pair<String, String>>, val bars: Int
)

object Library {
    private fun readAsset(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun index(ctx: Context): List<SongInfo> {
        val arr = JSONArray(readAsset(ctx, "songs/index.json"))
        val out = ArrayList<SongInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ps = o.getJSONArray("parts")
            val parts = ArrayList<Pair<String, String>>()
            for (k in 0 until ps.length()) parts.add(Pair(ps.getJSONObject(k).getString("id"), ps.getJSONObject(k).getString("name")))
            out.add(
                SongInfo(
                    o.getString("id"), o.getString("title"), o.optString("subtitle"), o.optString("meta"),
                    o.optString("cat", "song"), o.optString("group"), parts, o.optInt("bars")
                )
            )
        }
        return out
    }

    fun load(ctx: Context, id: String): Song {
        val o = JSONObject(readAsset(ctx, "songs/$id.json"))
        val secs = ArrayList<Section>()
        val sa = o.getJSONArray("sections")
        for (i in 0 until sa.length()) {
            val s = sa.getJSONObject(i)
            secs.add(Section(s.getString("name"), ints(s.getJSONArray("order"))))
        }
        val parts = ArrayList<Part>()
        val pa = o.getJSONArray("parts")
        for (i in 0 until pa.length()) {
            val p = pa.getJSONObject(i)
            val ms = ArrayList<Measure>()
            val ma = p.getJSONArray("measures")
            for (k in 0 until ma.length()) {
                val m = ma.getJSONObject(k)
                val na = m.getJSONArray("notes")
                val notes = ArrayList<Note>()
                for (j in 0 until na.length()) {
                    val a = na.getJSONArray(j)
                    notes.add(Note(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3), a.getInt(4), a.getInt(5)))
                }
                ms.add(Measure(m.getInt("n"), m.getInt("f"), m.optString("t"), m.optString("rm"), notes))
            }
            parts.add(Part(p.getString("id"), p.getString("name"), ints(p.getJSONArray("order")), ms))
        }
        return Song(
            o.getString("id"), o.getString("title"), o.optString("subtitle"), o.optString("meta"),
            o.optString("cat", "song"), o.optString("group"), o.optString("desc"),
            o.getInt("num"), o.getInt("den"), o.getInt("key"), o.getInt("bpm"), secs, parts
        )
    }

    private fun ints(a: JSONArray): List<Int> {
        val l = ArrayList<Int>(a.length())
        for (i in 0 until a.length()) l.add(a.getInt(i))
        return l
    }
}

/** Digitacion del bajo / barítono de 3 pistones en Si bemol (nota escrita en clave de sol). */
object Fingering {
    private val map = mapOf(
        54 to "123", 55 to "13", 56 to "23", 57 to "12", 58 to "1", 59 to "2",
        60 to "0", 61 to "123", 62 to "13", 63 to "23", 64 to "12", 65 to "1", 66 to "2",
        67 to "0", 68 to "23", 69 to "12", 70 to "1", 71 to "2",
        72 to "0", 73 to "12", 74 to "1", 75 to "2", 76 to "0", 77 to "1", 78 to "2",
        79 to "0", 80 to "23", 81 to "12", 82 to "1", 83 to "2", 84 to "0"
    )

    fun forMidi(m: Int): String? = map[m]

    fun valves(s: String?): BooleanArray {
        val v = BooleanArray(3)
        if (s == null) return v
        for (ch in s) when (ch) {
            '1' -> v[0] = true
            '2' -> v[1] = true
            '3' -> v[2] = true
        }
        return v
    }
}

object Names {
    private val letters = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    private val chroma = arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")

    fun ofNote(n: Note): String {
        val base = letters[((n.dia % 7) + 7) % 7]
        return when (n.alter) {
            1 -> "$base#"
            -1 -> base + "b"
            else -> base
        }
    }

    fun pitchClass(midi: Int): String = chroma[((midi % 12) + 12) % 12]

    fun withOctave(midi: Int): String = pitchClass(midi) + (Math.floorDiv(midi, 12) - 1)
}

/** Nota con su tiempo absoluto dentro del flujo de ejecucion. Los silencios no generan evento. */
class Ev(val note: Note, val slot: Int, val noteIdx: Int, val t0: Int, val t1: Int) {
    var cont = false          // continuacion de una ligadura: no se vuelve a atacar
    var tieTargetT = -1       // tick de inicio de la nota a la que se liga
    var hit = false           // acierto en modo evaluar
    var matched = false       // acierto en modo espera
}

class Slot(val m: Measure, val index: Int, val t0: Int, val offsets: IntArray, val evIdx: IntArray) {
    var repeatEnd = false     // se muestra :| al final porque la ejecucion salta atras
    var repeatStart = false
    var voltaStart = 0        // 1 o 2 si aqui comienza una casilla
    var voltaLen = 0          // numero de compases consecutivos de la casilla
}

class Stream(val slots: List<Slot>, val events: List<Ev>, val total: Int, val mlen: Int, val beat: Int) {
    /** Indices de los eventos que hay que atacar (excluye continuaciones de ligadura). */
    val attacks: List<Int> = events.indices.filter { !events[it].cont }

    fun slotAt(tick: Int): Int = if (slots.isEmpty()) 0 else (tick / mlen).coerceIn(0, slots.size - 1)

    fun resetResults() {
        for (e in events) { e.hit = false; e.matched = false }
    }
}

object StreamBuilder {
    fun build(song: Song, part: Part, order: List<Int>): Stream {
        val mlen = song.measureTicks
        val slots = ArrayList<Slot>()
        val events = ArrayList<Ev>()
        for ((i, num) in order.withIndex()) {
            val m = part.byNumber[num] ?: continue
            val t0 = slots.size * mlen
            val offs = IntArray(m.notes.size)
            val evIdx = IntArray(m.notes.size) { -1 }
            var acc = 0
            for ((k, nt) in m.notes.withIndex()) {
                offs[k] = acc
                if (!nt.isRest) {
                    evIdx[k] = events.size
                    events.add(Ev(nt, slots.size, k, t0 + acc, t0 + acc + nt.dur))
                }
                acc += nt.dur
            }
            val s = Slot(m, slots.size, t0, offs, evIdx)
            s.repeatStart = m.repeatStart
            slots.add(s)
            if (i < 0) break
        }
        // saltos hacia atras: se muestra la barra de repeticion final
        for (i in 0 until slots.size - 1) {
            val a = slots[i]; val b = slots[i + 1]
            if (a.m.repeatEnd && b.m.n <= a.m.n) a.repeatEnd = true
        }
        // casillas: se rotula la primera de cada grupo consecutivo
        var i = 0
        while (i < slots.size) {
            val v = if (slots[i].m.volta1) 1 else if (slots[i].m.volta2) 2 else 0
            if (v != 0) {
                var j = i
                while (j + 1 < slots.size && ((v == 1 && slots[j + 1].m.volta1) || (v == 2 && slots[j + 1].m.volta2))) j++
                slots[i].voltaStart = v
                slots[i].voltaLen = j - i + 1
                i = j + 1
            } else i++
        }
        // ligaduras
        for (k in 0 until events.size - 1) {
            val a = events[k]; val b = events[k + 1]
            if (a.note.tie && b.t0 == a.t1 && b.note.midi == a.note.midi) {
                b.cont = true
                a.tieTargetT = b.t0
            }
        }
        return Stream(slots, events, slots.size * mlen, mlen, song.beatTicks)
    }
}
