package io.github.rianpls.copypastebooks.core;

/**
 * Builds per-volume book titles from a base name, fitting the vanilla title length limit.
 * A name that already ends in a digit gets a Roman-numeral suffix (so "Chapter2" becomes
 * "Chapter2 II") to avoid ambiguity; otherwise a " - N" suffix is used. The base is
 * trimmed so the whole title fits.
 */
public final class VolumeNaming {

    private VolumeNaming() {
    }

    public static String forVolume(String base, int oneBasedIndex, int limit) {
        String trimmedBase = base == null ? "" : base.trim();
        if (trimmedBase.isEmpty()) {
            return clamp(String.valueOf(oneBasedIndex), limit);
        }
        boolean endsWithDigit = Character.isDigit(trimmedBase.charAt(trimmedBase.length() - 1));
        String suffix = endsWithDigit ? " " + roman(oneBasedIndex) : " - " + oneBasedIndex;
        int room = limit - suffix.length();
        if (room < 1) {
            // Suffix alone doesn't fit: return the numeric part, clamped.
            return clamp(suffix.trim(), limit);
        }
        String head = trimmedBase.length() > room ? trimmedBase.substring(0, room).trim() : trimmedBase;
        if (head.isEmpty()) {
            return clamp(suffix.trim(), limit);
        }
        return clamp(head + suffix, limit);
    }

    private static String clamp(String s, int limit) {
        return s.length() > limit ? s.substring(0, limit) : s;
    }

    /**
     * The longest base name that always fits within {@code limit} once the per-volume
     * suffix is added, across all volumes and both suffix styles. Used to cap the name
     * input field so the user never sees silent trimming.
     */
    public static int maxBaseLength(int volumeCount, int limit) {
        int worstSuffix = 0;
        for (int i = 1; i <= Math.max(1, volumeCount); i++) {
            int dash = (" - " + i).length();
            int rom = (" " + roman(i)).length();
            worstSuffix = Math.max(worstSuffix, Math.max(dash, rom));
        }
        return Math.max(1, limit - worstSuffix);
    }

    /** Standard Roman numerals for 1..3999; falls back to the decimal string outside that. */
    public static String roman(int value) {
        if (value < 1 || value > 3999) {
            return String.valueOf(value);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int n = value;
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                sb.append(symbols[i]);
                n -= values[i];
            }
        }
        return sb.toString();
    }
}
