package hu.onda;

import java.util.ArrayList;
import java.util.List;

/** IR jelminták (mikroszekundum, be/ki váltakozva). A Lens Mind kódkészletéből: LG TV és két gyakori LED-szalag család. */
final class IrCodes {
    private IrCodes() {}

    static final int CARRIER = 38000;

    /** dev: "LG", "LED_A", "LED_B". null = ehhez az eszközhöz nincs ilyen gomb. */
    static int[] pattern(String dev, String key) {
        int c;
        switch (dev) {
            case "LG":    c = lg(key);   return c < 0 ? null : nec(0x04, c);
            case "LED_A": c = ledA(key); return c < 0 ? null : nec(0x00, c);
            case "LED_B": c = ledB(key); return c < 0 ? null : necExt(0xEF00, c);
            default: return null;
        }
    }

    static boolean isLedKey(String k) {
        switch (k) {
            case "LED_ON": case "LED_OFF": case "BRIGHT_UP": case "BRIGHT_DOWN":
            case "RED": case "GREEN": case "BLUE": case "WHITE": return true;
            default: return false;
        }
    }

    private static int lg(String k) {
        switch (k) {
            case "POWER": return 0x08; case "VOL_UP": return 0x02; case "VOL_DOWN": return 0x03; case "MUTE": return 0x09;
            case "UP": return 0x40; case "DOWN": return 0x41; case "LEFT": return 0x07; case "RIGHT": return 0x06;
            case "OK": return 0x44; case "BACK": return 0x28; case "HOME": return 0x7C;
            case "CH_UP": return 0x00; case "CH_DOWN": return 0x01;
            case "D0": return 0x10; case "D1": return 0x11; case "D2": return 0x12; case "D3": return 0x13; case "D4": return 0x14;
            case "D5": return 0x15; case "D6": return 0x16; case "D7": return 0x17; case "D8": return 0x18; case "D9": return 0x19;
            case "SOURCE": return 0x0B; case "MENU": return 0x43;
            case "PLAY": return 0xB0; case "PAUSE": return 0xBA; case "NEXT": return 0x8E; case "PREV": return 0x8F;
            default: return -1;
        }
    }

    /** Gyakori 24 gombos LED-szalag távirányító, NEC cím 0x00. */
    private static int ledA(String k) {
        switch (k) {
            case "LED_ON": return 0x40; case "LED_OFF": return 0x41;
            case "BRIGHT_UP": return 0x5C; case "BRIGHT_DOWN": return 0x5D;
            case "RED": return 0x58; case "GREEN": return 0x59; case "BLUE": return 0x45; case "WHITE": return 0x44;
            default: return -1;
        }
    }

    /** Másik gyakori 24 gombos család, NEC kiterjesztett cím 0xEF00. */
    private static int ledB(String k) {
        switch (k) {
            case "LED_ON": return 3; case "LED_OFF": return 2;
            case "BRIGHT_UP": return 0; case "BRIGHT_DOWN": return 1;
            case "RED": return 4; case "GREEN": return 5; case "BLUE": return 6; case "WHITE": return 7;
            default: return -1;
        }
    }

    private static int[] bits(int v, int n) {
        int[] b = new int[n];
        for (int i = 0; i < n; i++) b[i] = (v >> i) & 1;
        return b;
    }

    private static int[] necLike(int h1, int h2, int[]... parts) {
        List<Integer> l = new ArrayList<>();
        l.add(h1);
        l.add(h2);
        for (int[] part : parts) {
            for (int bit : part) {
                l.add(560);
                l.add(bit == 1 ? 1690 : 560);
            }
        }
        l.add(560); // záró jel
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    private static int[] nec(int addr, int cmd) {
        return necLike(9000, 4500, bits(addr, 8), bits(~addr & 0xFF, 8), bits(cmd, 8), bits(~cmd & 0xFF, 8));
    }

    /** 16 bites cím (nincs inverz bájt). */
    private static int[] necExt(int addr16, int cmd) {
        return necLike(9000, 4500, bits(addr16 & 0xFF, 8), bits((addr16 >> 8) & 0xFF, 8), bits(cmd, 8), bits(~cmd & 0xFF, 8));
    }
}
