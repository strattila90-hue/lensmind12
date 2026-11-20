package hu.lensmind

enum class Brand(val label: String, val carrier: Int) {
    LG("LG", 38000),
    SAMSUNG("Samsung", 38000),
    SONY("Sony", 40000)
}

enum class Key { POWER, VOL_UP, VOL_DOWN, MUTE }

/** IR jelminták (mikroszekundum, be/ki váltakozva) a gyakori TV-protokollokhoz. */
object IrCodes {

    fun pattern(b: Brand, k: Key): IntArray = when (b) {
        Brand.LG -> nec(0x04, when (k) {
            Key.POWER -> 0x08; Key.VOL_UP -> 0x02; Key.VOL_DOWN -> 0x03; Key.MUTE -> 0x09
        })
        Brand.SAMSUNG -> samsung(0x07, when (k) {
            Key.POWER -> 0x02; Key.VOL_UP -> 0x07; Key.VOL_DOWN -> 0x0B; Key.MUTE -> 0x0F
        })
        Brand.SONY -> sirc12(0x01, when (k) {
            Key.POWER -> 21; Key.VOL_UP -> 18; Key.VOL_DOWN -> 19; Key.MUTE -> 20
        })
    }

    private fun bits(v: Int, n: Int): List<Int> = (0 until n).map { (v shr it) and 1 }

    private fun necLike(h1: Int, h2: Int, data: List<Int>): IntArray {
        val l = mutableListOf(h1, h2)
        for (bit in data) {
            l.add(560)
            l.add(if (bit == 1) 1690 else 560)
        }
        l.add(560) // záró jel
        return l.toIntArray()
    }

    private fun nec(addr: Int, cmd: Int) = necLike(
        9000, 4500,
        bits(addr, 8) + bits(addr.inv() and 0xFF, 8) + bits(cmd, 8) + bits(cmd.inv() and 0xFF, 8)
    )

    private fun samsung(addr: Int, cmd: Int) = necLike(
        4500, 4500,
        bits(addr, 8) + bits(addr, 8) + bits(cmd, 8) + bits(cmd.inv() and 0xFF, 8)
    )

    /** Sony SIRC 12 bites, háromszor ismételve (a TV-k ezt várják). */
    private fun sirc12(addr: Int, cmd: Int): IntArray {
        val frame = mutableListOf(2400, 600)
        for (bit in bits(cmd, 7) + bits(addr, 5)) {
            frame.add(if (bit == 1) 1200 else 600) // jel
            frame.add(600)                          // szünet
        }
        frame.removeAt(frame.size - 1) // jellel záruljon
        val dur = frame.sum()
        val out = mutableListOf<Int>()
        repeat(3) { i ->
            out.addAll(frame)
            if (i < 2) out.add(45000 - dur)
        }
        return out.toIntArray()
    }
}
