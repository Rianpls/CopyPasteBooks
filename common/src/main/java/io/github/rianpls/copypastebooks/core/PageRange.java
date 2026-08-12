package io.github.rianpls.copypastebooks.core;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Parses page selections like {@code "1-13, 15, 17-37"} (1-based, inclusive).
 * A blank input selects every page.
 */
public final class PageRange {

    /** @param pages selected page indices, 0-based, sorted, deduplicated (empty when not {@link #ok()})
     *  @param badToken the first unparseable token, or null */
    public record Result(List<Integer> pages, String badToken, boolean outOfBounds) {
        public boolean ok() {
            return badToken == null && !pages.isEmpty();
        }
    }

    private PageRange() {
    }

    public static Result parse(String text, int pageCount) {
        TreeSet<Integer> picked = new TreeSet<>();
        if (text == null || text.isBlank()) {
            for (int i = 0; i < pageCount; i++) {
                picked.add(i);
            }
            return new Result(List.copyOf(picked), null, false);
        }
        boolean sawInBounds = false;
        boolean sawOutOfBounds = false;
        for (String rawToken : text.split(",")) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            int from;
            int to;
            // "N" or "N-M"; en dash tolerated
            String normalized = token.replace('–', '-');
            int dash = normalized.indexOf('-');
            try {
                if (dash < 0) {
                    from = to = Integer.parseInt(normalized.trim());
                } else {
                    from = Integer.parseInt(normalized.substring(0, dash).trim());
                    to = Integer.parseInt(normalized.substring(dash + 1).trim());
                }
            } catch (NumberFormatException e) {
                return new Result(List.of(), token, false);
            }
            if (from < 1 || to < 1) {
                return new Result(List.of(), token, false);
            }
            if (from > to) {
                int swap = from;
                from = to;
                to = swap;
            }
            // Clamp before iterating so a range like 1-2147483647 cannot overflow the loop.
            if (from > pageCount) {
                sawOutOfBounds = true;
                continue;
            }
            if (to > pageCount) {
                to = pageCount;
                sawOutOfBounds = true;
            }
            for (int page = from; page <= to; page++) {
                picked.add(page - 1);
            }
            sawInBounds = true;
        }
        if (!sawInBounds) {
            return new Result(List.of(), null, sawOutOfBounds);
        }
        List<Integer> pages = new ArrayList<>(picked);
        return new Result(List.copyOf(pages), null, sawOutOfBounds);
    }
}
