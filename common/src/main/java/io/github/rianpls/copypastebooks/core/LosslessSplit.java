package io.github.rianpls.copypastebooks.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into pages without changing the consumed characters. Joining the returned
 * pages reconstructs the consumed input exactly, and every page passes the supplied fit
 * test. Breaks prefer the nearest newline or space; a word is split only when it cannot
 * fit on a page by itself.
 *
 * The fit test must be monotonic for growing prefixes. Work stops at {@code maxPages},
 * so very large clipboard input is bounded by the book's remaining page budget.
 */
public final class LosslessSplit {

    /** Returns whether the supplied text fits on one page. */
    @FunctionalInterface
    public interface FitTester {
        boolean fits(String pageText);
    }

    /**
     * {@code overflow} = the page budget ran out with input left unconsumed.
     * {@code unfittable} = a single character that exceeds the fit test all by itself
     * (e.g. a 4-byte emoji against a 3-byte page cap); splitting stopped there — the
     * caller must refuse the operation instead of silently dropping or force-fitting it.
     */
    public record Result(List<String> pages, boolean overflow, String unfittable) {
    }

    private LosslessSplit() {
    }

    /**
     * @param text          input; only the first {@code maxPages} pages' worth is consumed
     * @param fit           page-fits test, monotonic in the prefix
     * @param maxPageLength hard upper bound of a page's char count (binary-search ceiling)
     * @param maxPages      page budget; splitting stops once it is reached
     */
    public static Result split(String text, FitTester fit, int maxPageLength, int maxPages) {
        List<String> pages = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            if (pages.size() >= maxPages) {
                return new Result(pages, true, null);
            }
            int prefix = fitPrefixLength(text, start, fit, maxPageLength);
            if (prefix <= 0) {
                // Even one code point fails the fit test (tiny byte cap vs a multi-byte
                // character): stop and report it instead of emitting an over-limit page.
                int codePoint = text.codePointAt(start);
                return new Result(pages, false, new String(Character.toChars(codePoint)));
            }
            int cut = start + prefix;
            if (cut < text.length()) {
                // Prefer the separator nearest the page end. Rolling back by more than
                // half a page is allowed only when it keeps an otherwise fitting word
                // intact; this avoids both half-empty pages and unnecessary word splits.
                int half = start + (cut - start) / 2;
                int breakAt = lastSeparator(text, cut - 1, start + 1);
                if (breakAt > start) {
                    if (breakAt + 1 > half) {
                        cut = breakAt + 1;
                    } else {
                        // Otherwise use a hard cut instead of leaving most of the page empty.
                        int wordEnd = wordEnd(text, cut, breakAt + 1 + maxPageLength + 1);
                        if (wordEnd - breakAt - 1 <= maxPageLength
                                && fit.fits(text.substring(breakAt + 1, wordEnd))) {
                            cut = breakAt + 1;
                        }
                    }
                }
            }
            pages.add(text.substring(start, cut));
            start = cut;
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        return new Result(pages, false, null);
    }

    /** Last '\n' or ' ' in {@code [downTo..from]}, or -1 — bounded by the current page. */
    private static int lastSeparator(String text, int from, int downTo) {
        for (int i = from; i >= downTo; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == ' ') {
                return i;
            }
        }
        return -1;
    }

    /** End of the word starting before {@code from}: next separator or {@code cap}. */
    private static int wordEnd(String text, int from, int cap) {
        int end = Math.min(text.length(), cap);
        for (int i = from; i < end; i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == ' ') {
                return i;
            }
        }
        return end;
    }

    /** Longest fitting prefix length from {@code start}, code-point aligned, at least one. */
    private static int fitPrefixLength(String text, int start, FitTester fit, int maxPageLength) {
        int remaining = text.length() - start;
        int ceiling = Math.min(remaining, Math.max(1, maxPageLength));
        if (ceiling == remaining && fit.fits(text.substring(start))) {
            return remaining;
        }
        // Binary search for the longest fitting prefix; assumes fits() is monotonic.
        int low = 1;
        int high = ceiling == remaining ? ceiling - 1 : ceiling;
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (fit.fits(text.substring(start, start + mid))) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return alignDown(text, start, best); // 0 = even one code point doesn't fit
    }

    /** Moves a prefix length down onto a code-point boundary (never splits a surrogate pair). */
    private static int alignDown(String text, int start, int length) {
        int index = start + length;
        if (length > 0 && index < text.length()
                && Character.isLowSurrogate(text.charAt(index))
                && Character.isHighSurrogate(text.charAt(index - 1))) {
            return length - 1;
        }
        return length;
    }
}
