package com.digitalminds.artstar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Una nota o silencio. dia = octava*7 + letra (Do=0), Mi4 = 30. Duracion en ticks (negra = 48). */
class Note(val dia: Int, val alter: Int, val show: Int, val midi: Int, val dur: Int, val flags: Int) {
    val isRest get() = flags and 1 != 0
    val accent get() = flags and 2 != 0
    val staccato get() = flags and 4 != 0
    val tie get() = flags and 8 != 0
    val triplet get() = flags and 16 != 0
    val fullRest get() = flags and 32 != 0
    fun posIn(base: Int) = dia - base
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

/** Instrumentos. transpose = semitonos de la nota escrita a la nota que suena. fam: 0 pistones, 1 saxo, 2 otro. */
class InstDef(val id: String, val name: String, val short: String, val transpose: Int, val sample: String, val fam: Int, val practice: Boolean, val low: Int, val high: Int, val bass: Boolean = false)

object Insts {
    val bar = InstDef("bar", "Bajo / Barítono", "Bajo", -14, "trombone", 0, true, 54, 81)
    val tpt = InstDef("tpt", "Trompeta", "Trompeta", -2, "trumpet", 0, true, 54, 86)
    val alto = InstDef("alto", "Saxo alto", "Saxo", -9, "alto_sax", 1, true, 58, 89)
    val tbn = InstDef("tbn", "Trombón", "Trombón", 0, "trombone", 3, true, 40, 70, true)
    val tuba = InstDef("tuba", "Tuba", "Tuba", 0, "tuba", 4, true, 34, 65, true)
    val clar = InstDef("clar", "Clarinete", "Clarinete", -2, "clarinet", 2, false, 52, 91)
    val glock = InstDef("glock", "Lira", "Lira", 0, "glockenspiel", 2, false, 60, 108)
    val all = listOf(bar, tpt, alto, tbn, tuba, clar, glock)
    val practice = listOf(bar, tpt, alto, tbn, tuba)
    fun get(id: String): InstDef = all.firstOrNull { it.id == id } ?: bar
}

class Part(val id: String, val name: String, val key: Int, val inst: InstDef, val order: List<Int>, val measures: List<Measure>) {
    /** Dia de la linea inferior del pentagrama: Mi4 en clave de sol, Sol2 en clave de fa. */
    val base: Int get() = if (inst.bass) 18 else 30
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

class PartInfo(val id: String, val name: String, val key: Int, val inst: String)

class SongInfo(
    val id: String, val title: String, val subtitle: String, val meta: String, val cat: String,
    val group: String, val parts: List<PartInfo>, val bars: Int
) {
    fun partsOf(inst: String) = parts.filter { it.inst == inst }
}

object Library {
    private fun readAsset(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun index(ctx: Context): List<SongInfo> {
        val arr = JSONArray(readAsset(ctx, "songs/index.json"))
        val out = ArrayList<SongInfo>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ps = o.getJSONArray("parts")
            val parts = ArrayList<PartInfo>()
            for (k in 0 until ps.length()) {
                val p = ps.getJSONObject(k)
                parts.add(PartInfo(p.getString("id"), p.getString("name"), p.optInt("key"), p.optString("inst", "bar")))
            }
            out.add(SongInfo(o.getString("id"), o.getString("title"), o.optString("subtitle"), o.optString("meta"),
                o.optString("cat", "song"), o.optString("group"), parts, o.optInt("bars")))
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
            parts.add(Part(p.getString("id"), p.getString("name"), p.optInt("key", o.getInt("key")), Insts.get(p.optString("inst", "bar")),
                ints(p.getJSONArray("order")), ms))
        }
        return Song(o.getString("id"), o.getString("title"), o.optString("subtitle"), o.optString("meta"),
            o.optString("cat", "song"), o.optString("group"), o.optString("desc"),
            o.getInt("num"), o.getInt("den"), o.getInt("key"), o.getInt("bpm"), secs, parts)
    }

    private fun ints(a: JSONArray): List<Int> {
        val l = ArrayList<Int>(a.length())
        for (i in 0 until a.length()) l.add(a.getInt(i))
        return l
    }
}

object Names {
    private val letters = arrayOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si")
    private val chroma = arrayOf("Do", "Do#", "Re", "Mib", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "Sib", "Si")

    fun ofNote(n: Note): String {
        val base = letters[((n.dia % 7) + 7) % 7]
        return when (n.alter) { 1 -> "$base#"; -1 -> base + "b"; 2 -> "$base##"; -2 -> base + "bb"; else -> base }
    }

    fun pitchClass(midi: Int): String = chroma[((midi % 12) + 12) % 12]
    fun withOctave(midi: Int): String = pitchClass(midi) + (Math.floorDiv(midi, 12) - 1)
    fun keyName(k: Int): String = when {
        k == 0 -> "sin alteraciones"
        k > 0 -> "$k sostenido" + (if (k > 1) "s" else "")
        else -> "${-k} bemol" + (if (k < -1) "es" else "")
    }
}

/** Nota con su tiempo absoluto dentro del flujo de ejecucion. Los silencios no generan evento. */
class Ev(val note: Note, val slot: Int, val noteIdx: Int, val t0: Int, val t1: Int) {
    var cont = false
    var tieTargetT = -1
    var hit = false
    var matched = false
}

class Slot(val m: Measure, val index: Int, val t0: Int, val offsets: IntArray, val evIdx: IntArray) {
    var repeatEnd = false
    var repeatStart = false
    var voltaStart = 0
    var voltaLen = 0
}

class Stream(val slots: List<Slot>, val events: List<Ev>, val total: Int, val mlen: Int, val beat: Int) {
    val attacks: List<Int> = events.indices.filter { !events[it].cont }
}

object StreamBuilder {
    fun build(song: Song, part: Part, order: List<Int>): Stream {
        val mlen = song.measureTicks
        val slots = ArrayList<Slot>()
        val events = ArrayList<Ev>()
        for (num in order) {
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
        }
        for (i in 0 until slots.size - 1) {
            val a = slots[i]; val b = slots[i + 1]
            if (a.m.repeatEnd && b.m.n <= a.m.n) a.repeatEnd = true
        }
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
        for (k in 0 until events.size - 1) {
            val a = events[k]; val b = events[k + 1]
            if (a.note.tie && b.t0 == a.t1 && b.note.midi == a.note.midi) { b.cont = true; a.tieTargetT = b.t0 }
        }
        return Stream(slots, events, slots.size * mlen, mlen, song.beatTicks)
    }
}
