package hu.lensmind

enum class Cat { TV, LED }

enum class Brand(val label: String, val carrier: Int, val cat: Cat) {
    LG("LG", 38000, Cat.TV),
    SAMSUNG("Samsung", 38000, Cat.TV),
    SONY("Sony", 40000, Cat.TV),
    LED_A("LED „A” típus", 38000, Cat.LED),
    LED_B("LED „B” típus", 38000, Cat.LED)
}

enum class Key {
    // közös / tévé
    POWER, VOL_UP, VOL_DOWN, MUTE, UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, CH_UP, CH_DOWN,
    // LED lámpa / szalag
    LED_ON, LED_OFF, BRIGHT_UP, BRIGHT_DOWN, RED, GREEN, BLUE, WHITE
}

/** IR jelminták (mikroszekundum, be/ki váltakozva). null = ehhez az eszközhöz nincs ilyen gomb. */
object IrCodes {

    fun pattern(b: Brand, k: Key): IntArray? = when (b) {
        Brand.LG -> lg(k)?.let { nec(0x04, it) }
        Brand.SAMSUNG -> samsungCmd(k)?.let { samsung(0x07, it) }
        Brand.SONY -> sonyCmd(k)?.let { sirc12(0x01, it) }
        Brand.LED_A -> ledA(k)?.let { nec(0x00, it) }
        Brand.LED_B -> ledB(k)?.let { necExt(0xEF00, it) }
    }

    private fun lg(k: Key): Int? = when (k) {
        Key.POWER -> 0x08; Key.VOL_UP -> 0x02; Key.VOL_DOWN -> 0x03; Key.MUTE -> 0x09
        Key.UP -> 0x40; Key.DOWN -> 0x41; Key.LEFT -> 0x07; Key.RIGHT -> 0x06; Key.OK -> 0x44
        Key.BACK -> 0x28; Key.HOME -> 0x7C; Key.CH_UP -> 0x00; Key.CH_DOWN -> 0x01
        else -> null
    }

    private fun samsungCmd(k: Key): Int? = when (k) {
        Key.POWER -> 0x02; Key.VOL_UP -> 0x07; Key.VOL_DOWN -> 0x0B; Key.MUTE -> 0x0F
        Key.UP -> 0x60; Key.DOWN -> 0x61; Key.LEFT -> 0x65; Key.RIGHT -> 0x62; Key.OK -> 0x68
        Key.BACK -> 0x58; Key.HOME -> 0x79; Key.CH_UP -> 0x12; Key.CH_DOWN -> 0x10
        else -> null
    }

    private fun sonyCmd(k: Key): Int? = when (k) {
        Key.POWER -> 21; Key.VOL_UP -> 18; Key.VOL_DOWN -> 19; Key.MUTE -> 20
        Key.UP -> 116; Key.DOWN -> 117; Key.LEFT -> 52; Key.RIGHT -> 51; Key.OK -> 101
        Key.BACK -> 99; Key.HOME -> 96; Key.CH_UP -> 0; Key.CH_DOWN -> 1
        else -> null
    }

    /** Gyakori 24 gombos LED-szalag távirányító, NEC cím 0x00. */
    private fun ledA(k: Key): Int? = when (k) {
        Key.LED_ON, Key.POWER -> 0x40; Key.LED_OFF -> 0x41
        Key.BRIGHT_UP -> 0x5C; Key.BRIGHT_DOWN -> 0x5D
        Key.RED -> 0x58; Key.GREEN -> 0x59; Key.BLUE -> 0x45; Key.WHITE -> 0x44
        else -> null
    }

    /** Másik gyakori 24 gombos család, NEC kiterjesztett cím 0xEF00. */
    private fun ledB(k: Key): Int? = when (k) {
        Key.LED_ON, Key.POWER -> 3; Key.LED_OFF -> 2
        Key.BRIGHT_UP -> 0; Key.BRIGHT_DOWN -> 1
        Key.RED -> 4; Key.GREEN -> 5; Key.BLUE -> 6; Key.WHITE -> 7
        else -> null
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

    /** 16 bites cím (nincs inverz bájt). */
    private fun necExt(addr16: Int, cmd: Int) = necLike(
        9000, 4500,
        bits(addr16 and 0xFF, 8) + bits((addr16 shr 8) and 0xFF, 8) + bits(cmd, 8) + bits(cmd.inv() and 0xFF, 8)
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
