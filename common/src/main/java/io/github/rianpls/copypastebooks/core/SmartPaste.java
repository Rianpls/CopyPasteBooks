package io.github.rianpls.copypastebooks.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Inserts clipboard text at the caret and lets it continue onto following pages. Pages
 * before the caret stay unchanged; later pages move forward without being re-split.
 *
 * If the page limit would remove existing text, the paste is rejected. If only the end
 * of a paste at the end of the book is over the limit, that excess is trimmed and
 * reported in the result.
 */
public final class SmartPaste {

    /**
     * @param pages     resulting pages (never empty; the original pages when rejected)
     * @param focusPage page index the caret should land on
     * @param caret     character offset inside that page (end of the pasted text)
     * @param truncated true when the paste's own tail was cut at the book cap
     * @param rejected  true when nothing was changed — applying it would erase existing text
     */
    public record Result(List<String> pages, int focusPage, int caret, boolean truncated, boolean rejected) {
    }

    /** Budgeted lossless splitter — it may stop early once the page budget is reached. */
    @FunctionalInterface
    public interface Splitter {
        LosslessSplit.Result split(String text, int maxPages);
    }

    private SmartPaste() {
    }

    /**
     * @param pages       current book pages (may be empty)
     * @param currentPage page the caret is on
     * @param selStart    selection start within that page (== selEnd when no selection);
     *                    the selected range is replaced, like any editor does
     * @param selEnd      selection end (either order)
     * @param clipboard   text to paste (already normalized)
     * @param split       splitter whose returned pages preserve the consumed input
     * @param maxPages    book page cap (vanilla: 100)
     */
    public static Result paste(List<String> pages, int currentPage, int selStart, int selEnd,
                               String clipboard, Splitter split, int maxPages) {
        int page = pages.isEmpty() ? 0 : Math.clamp(currentPage, 0, pages.size() - 1);
        String current = pages.isEmpty() ? "" : pages.get(page);
        int from = Math.clamp(Math.min(selStart, selEnd), 0, current.length());
        int to = Math.clamp(Math.max(selStart, selEnd), 0, current.length());

        String before = current.substring(0, from);
        String after = current.substring(to);
        String combined = before + clipboard + after;

        // The budget bounds the work on a pathological clipboard: one page over the legal
        // maximum is enough — the cap check below sees the overflow either way.
        int budget = Math.max(1, maxPages - page + 1);
        LosslessSplit.Result splitResult = combined.isEmpty()
                ? new LosslessSplit.Result(List.of(""), false, null)
                : split.split(combined, budget);
        if (splitResult.unfittable() != null) {
            // Vanilla limits accept every single code point, but keep the fallback safe
            // for callers with a stricter fit test.
            return new Result(pages.isEmpty() ? List.of("") : List.copyOf(pages), page, from, false, true);
        }
        List<String> replacement = List.copyOf(splitResult.pages());

        List<String> out = new ArrayList<>();
        for (int i = 0; i < page && i < pages.size(); i++) {
            out.add(pages.get(i));
        }
        int replacementStart = out.size();
        out.addAll(replacement);
        for (int i = page + 1; i < pages.size(); i++) {
            out.add(pages.get(i));
        }

        boolean truncated = false;
        if (out.size() > maxPages) {
            int oldTailPages = pages.isEmpty() ? 0 : pages.size() - page - 1;
            if (oldTailPages > 0 || !after.isEmpty()) {
                // The cut would eat existing text (shifted pages are at the very end, and
                // the current page's tail sits at the end of the replacement) — refuse.
                return new Result(pages.isEmpty() ? List.of("") : List.copyOf(pages),
                        page, from, false, true);
            }
            out.subList(maxPages, out.size()).clear();
            truncated = true;
        }

        // End of the pasted text, located by pure arithmetic (the splitter is lossless).
        int end = before.length() + clipboard.length();
        int focusPage = replacementStart;
        int caret = 0;
        int consumed = 0;
        for (int i = 0; i < replacement.size(); i++) {
            int index = replacementStart + i;
            if (index >= out.size()) {
                break; // trimmed away; land at the end of the last kept page
            }
            int length = replacement.get(i).length();
            focusPage = index;
            caret = Math.min(end - consumed, length);
            if (end <= consumed + length) {
                break;
            }
            consumed += length;
        }
        return new Result(out, focusPage, caret, truncated, false);
    }
}
