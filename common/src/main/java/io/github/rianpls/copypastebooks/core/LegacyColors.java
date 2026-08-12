package io.github.rianpls.copypastebooks.core;

/** The 16 legacy chat colors; maps arbitrary RGB to the nearest legacy code. */
public final class LegacyColors {
    private static final char[] CODES = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };
    private static final int[] RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private LegacyColors() {
    }

    /** @param rgb 0xRRGGBB (alpha ignored) */
    public static char nearestCode(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < RGB.length; i++) {
            int cr = (RGB[i] >> 16) & 0xFF;
            int cg = (RGB[i] >> 8) & 0xFF;
            int cb = RGB[i] & 0xFF;
            long dist = (long) (r - cr) * (r - cr) + (long) (g - cg) * (g - cg) + (long) (b - cb) * (b - cb);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return CODES[best];
    }

    /** @return 0xRRGGBB of a legacy code char, or -1 if not a color code */
    public static int rgbOf(char code) {
        char c = Character.toLowerCase(code);
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i] == c) {
                return RGB[i];
            }
        }
        return -1;
    }
}
