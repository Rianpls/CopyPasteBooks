package io.github.rianpls.copypastebooks.core;

import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/** Builds a safe, unique {@code .txt} filename from a book title. */
public final class FileNamer {
    private static final int MAX_BASE_LENGTH = 60;
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL", "CLOCK$",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNamer() {
    }

    public static String sanitizeBase(String title, String fallbackBase) {
        String base = title == null ? "" : LegacyText.strip(title);
        StringBuilder cleaned = new StringBuilder();
        for (char c : base.toCharArray()) {
            if (c < 0x20 || "<>:\"/\\|?*".indexOf(c) >= 0) {
                cleaned.append(' ');
            } else {
                cleaned.append(c);
            }
        }
        String result = cleaned.toString().trim();
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1).trim();
        }
        if (result.length() > MAX_BASE_LENGTH) {
            result = result.substring(0, MAX_BASE_LENGTH).trim();
        }
        result = result.isEmpty() ? fallbackBase : result;
        // Windows rejects these device names even with an extension ("CON.txt").
        // Prefixing keeps the user's recognizable title and is harmless elsewhere.
        String stem = result;
        int dot = stem.indexOf('.');
        if (dot >= 0) {
            stem = stem.substring(0, dot);
        }
        stem = stem.stripTrailing().toUpperCase(Locale.ROOT);
        if (WINDOWS_RESERVED.contains(stem)) {
            result = "_" + result;
        }
        return result;
    }

    /** @param exists filename (with extension) → already taken? */
    public static String uniqueTxt(String base, Predicate<String> exists) {
        String candidate = base + ".txt";
        int counter = 2;
        while (exists.test(candidate)) {
            candidate = base + " (" + counter + ").txt";
            counter++;
        }
        return candidate;
    }
}
