package io.github.rianpls.copypastebooks.core;

import java.util.List;

/** Normalizes editor pages exactly like vanilla does immediately before saving. */
public final class BookDraft {
    private BookDraft() {
    }

    public static List<String> canonicalPages(List<String> pages) {
        int end = pages.size();
        while (end > 0 && pages.get(end - 1).isEmpty()) {
            end--;
        }
        return List.copyOf(pages.subList(0, end));
    }

    public static boolean changed(List<String> originalCanonicalPages, List<String> currentPages) {
        return !originalCanonicalPages.equals(canonicalPages(currentPages));
    }
}
