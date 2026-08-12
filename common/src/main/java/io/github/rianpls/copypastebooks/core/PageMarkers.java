package io.github.rianpls.copypastebooks.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Page boundary markers for exported text: a {@code === Page N ===} line before each page.
 * Parsing accepts both English and Russian marker spellings; writing always uses "Page"
 * so files stay machine-readable regardless of the mod language.
 */
public final class PageMarkers {
    private static final Pattern MARKER = Pattern.compile(
            "^\\s*===\\s*(?:page|страница)\\s*(\\d+)?\\s*===\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PageMarkers() {
    }

    public static String marker(int pageNumberOneBased) {
        return "=== Page " + pageNumberOneBased + " ===";
    }

    public static String join(List<String> pages) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(marker(i + 1)).append('\n').append(pages.get(i));
        }
        return out.toString();
    }

    public static boolean hasMarkers(String text) {
        for (String line : text.split("\n", -1)) {
            if (MARKER.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits marked text back into pages. Content before the first marker becomes its own
     * page if non-blank. Marker numbers are ignored; order of appearance wins.
     */
    public static List<String> split(String text) {
        List<String> pages = new ArrayList<>();
        StringBuilder current = null;
        StringBuilder preamble = new StringBuilder();
        boolean sawMarker = false;
        for (String line : text.split("\n", -1)) {
            if (MARKER.matcher(line).matches()) {
                if (sawMarker) {
                    pages.add(stripOneTrailingNewline(current.toString()));
                } else if (!preamble.toString().isBlank()) {
                    pages.add(stripOneTrailingNewline(preamble.toString()));
                }
                sawMarker = true;
                current = new StringBuilder();
            } else if (sawMarker) {
                current.append(line).append('\n');
            } else {
                preamble.append(line).append('\n');
            }
        }
        if (sawMarker) {
            pages.add(stripOneTrailingNewline(current.toString()));
        } else if (!preamble.toString().isBlank()) {
            pages.add(stripOneTrailingNewline(preamble.toString()));
        }
        return pages;
    }

    private static String stripOneTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }
}
