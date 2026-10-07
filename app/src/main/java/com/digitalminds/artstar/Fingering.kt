package com.digitalminds.artstar

/** Digitacion por instrumento. Bajo/barítono y trompeta usan la nota escrita; trombón y tuba, el sonido real. */
object Fingering {
    private val valveMap = mapOf(
        54 to "123", 55 to "13", 56 to "23", 57 to "12", 58 to "1", 59 to "2",
        60 to "0", 61 to "123", 62 to "13", 63 to "23", 64 to "12", 65 to "1", 66 to "2",
        67 to "0", 68 to "23", 69 to "12", 70 to "1", 71 to "2",
        72 to "0", 73 to "12", 74 to "1", 75 to "2", 76 to "0", 77 to "1", 78 to "2",
        79 to "0", 80 to "23", 81 to "12", 82 to "1", 83 to "2", 84 to "0", 85 to "12", 86 to "1"
    )

    /** Saxofon: octava, mano izquierda (1 2 3), mano derecha (4 5 6) y llaves extra. */
    class Sax(val oct: Boolean, val lh: String, val rh: String, val extra: String) {
        val top: String get() = (if (oct) "8 " else "") + (if (lh.isEmpty() && rh.isEmpty() && extra.isEmpty()) "0" else lh)
        val bottom: String get() = listOf(rh, extra).filter { it.isNotEmpty() }.joinToString(" ")
        val label: String get() = (top + (if (bottom.isNotEmpty()) " | $bottom" else "")).trim()
    }

    private val saxLow = mapOf(
        58 to Sax(false, "123", "456", "Sib"), 59 to Sax(false, "123", "456", "Si"), 60 to Sax(false, "123", "456", "Do"),
        61 to Sax(false, "123", "456", "Do#"), 62 to Sax(false, "123", "456", ""), 63 to Sax(false, "123", "456", "Mib"),
        64 to Sax(false, "123", "45", ""), 65 to Sax(false, "123", "4", ""), 66 to Sax(false, "123", "5", ""),
        67 to Sax(false, "123", "", ""), 68 to Sax(false, "123", "", "Sol#"), 69 to Sax(false, "12", "", ""),
        70 to Sax(false, "1", "4", ""), 71 to Sax(false, "1", "", ""), 72 to Sax(false, "2", "", ""), 73 to Sax(false, "", "", "")
    )

    fun valves(midi: Int): String? = valveMap[midi]

    fun sax(midi: Int): Sax? {
        saxLow[midi]?.let { return it }
        if (midi in 74..85) {
            val b = saxLow[midi - 12] ?: return null
            return Sax(true, b.lh, b.rh, if (midi == 74 || midi == 75) b.extra.replace("Sib", "") else b.extra)
        }
        return when (midi) {
            86 -> Sax(true, "", "", "P1")
            87 -> Sax(true, "", "", "P1 P2")
            88 -> Sax(true, "", "", "P1 P2 P3")
            89 -> Sax(true, "", "", "P1 P2 P3 Fa")
            else -> null
        }
    }

    private val tbnOpen = intArrayOf(46, 53, 58, 62, 65, 70, 72, 74)

    /** Posicion de vara (1 a 7) del trombon en Si bemol, sobre el sonido real. Elige el parcial mas agudo posible. */
    fun slide(midi: Int): Int? {
        for (o in tbnOpen.reversed()) {
            val l = o - midi
            if (l in 0..6) return l + 1
        }
        return null
    }

    private val tubaOpen = intArrayOf(34, 41, 46, 50, 53, 58, 62, 65)
    private val lower = arrayOf("0", "2", "1", "12", "23", "13", "123")

    /** Pistones de la tuba en Si bemol (sonido real). El semitono mas grave se logra con los pistones sumados. */
    fun tubaValves(midi: Int): String? {
        for (o in tubaOpen.reversed()) {
            val l = o - midi
            if (l in 0..6) return lower[l]
        }
        return null
    }

    fun label(inst: InstDef, midi: Int): String? = when (inst.fam) {
        0 -> valves(midi)
        1 -> sax(midi)?.label
        3 -> slide(midi)?.toString()
        4 -> tubaValves(midi)
        else -> null
    }

    fun valveArray(s: String?): BooleanArray {
        val v = BooleanArray(3)
        if (s == null) return v
        for (ch in s) when (ch) { '1' -> v[0] = true; '2' -> v[1] = true; '3' -> v[2] = true }
        return v
    }
}
