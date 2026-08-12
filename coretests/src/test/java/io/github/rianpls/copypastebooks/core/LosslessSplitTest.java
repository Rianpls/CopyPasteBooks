package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LosslessSplitTest {

    /** Char-count limit, like a simple page. */
    private static LosslessSplit.FitTester maxChars(int n) {
        return text -> text.length() <= n;
    }

    /** UTF-8 byte limit — exercises multi-byte characters. */
    private static LosslessSplit.FitTester maxBytes(int n) {
        return text -> text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= n;
    }

    @Test
    void everythingFitsOnOnePage() {
        assertEquals(List.of("abc"), LosslessSplit.split("abc", maxChars(10), 2048, 1000).pages());
        assertEquals(List.of(""), LosslessSplit.split("", maxChars(10), 2048, 1000).pages());
    }

    @Test
    void breaksPreferNewlines() {
        // "aaa\nbbb\nccc" with a 9-char page: cut after the last \n inside the prefix.
        List<String> pages = LosslessSplit.split("aaa\nbbb\nccc", maxChars(9), 2048, 1000).pages();
        assertEquals(List.of("aaa\nbbb\n", "ccc"), pages);
        assertEquals("aaa\nbbb\nccc", String.join("", pages));
    }

    @Test
    void breaksFallBackToSpaces() {
        List<String> pages = LosslessSplit.split("aaaa bbbb cccc", maxChars(10), 2048, 1000).pages();
        assertEquals("aaaa bbbb cccc", String.join("", pages));
        // Cut lands after a space, not mid-word.
        assertTrue(pages.get(0).endsWith(" "), pages.toString());
        assertFalse(pages.get(0).length() > 10);
    }

    /** A nearby paragraph break must not leave most of the page empty. */
    @Test
    void paragraphBoundaryDoesNotStarvePage() {
        String text = "tail.\n" + "word ".repeat(30);
        List<String> pages = LosslessSplit.split(text, maxChars(50), 2048, 100).pages();
        assertEquals(text, String.join("", pages));
        assertTrue(pages.get(0).length() > 40, "page starved at the early newline: " + pages.get(0));
    }

    /** ...but a rollback further than half the page is refused — hard cut instead. */
    @Test
    void rollbackNeverEatsMoreThanHalfThePage() {
        String text = "ab " + "x".repeat(80);
        List<String> pages = LosslessSplit.split(text, maxChars(40), 2048, 100).pages();
        assertEquals(text, String.join("", pages));
        assertEquals(40, pages.get(0).length()); // hard cut, not "ab "
    }

    /** Every stored page must pass the fit test, including its trailing newline. */
    @Test
    void fullLineBreakMovesLastWordToNextPage() {
        LosslessSplit.Result r = LosslessSplit.split("aaa\nbbb\nccc", maxChars(7), 2048, 100);
        assertEquals(List.of("aaa\n", "bbb\nccc"), r.pages());
        assertEquals("aaa\nbbb\nccc", String.join("", r.pages()));
        for (String page : r.pages()) {
            assertTrue(page.length() <= 7, "page exceeds the fit test: " + page);
        }
        assertFalse(r.pages().get(1).startsWith("\n"), "next page starts with a blank line");
    }

    /** Same with a rendered-height-style fit (a trailing \n counts as a phantom line). */
    @Test
    void strictLineLimitNeverExceeded() {
        LosslessSplit.FitTester threeRenderedLines = s -> s.split("\n", -1).length <= 3;
        LosslessSplit.Result r = LosslessSplit.split("L1\nL2\nL3\nL4\nL5\nL6", threeRenderedLines, 2048, 100);
        assertEquals(List.of("L1\nL2\n", "L3\nL4\n", "L5\nL6"), r.pages());
        for (String page : r.pages()) {
            assertTrue(threeRenderedLines.fits(page), "page exceeds the line limit: " + page);
        }
    }

    @Test
    void hardCutInsideEndlessWord() {
        List<String> pages = LosslessSplit.split("x".repeat(25), maxChars(10), 2048, 1000).pages();
        assertEquals(List.of("x".repeat(10), "x".repeat(10), "x".repeat(5)), pages);
    }

    @Test
    void neverSplitsSurrogatePairs() {
        String emoji = "😀"; // 😀, one code point, two chars
        List<String> pages = LosslessSplit.split(emoji.repeat(8), maxChars(5), 2048, 1000).pages();
        assertEquals(emoji.repeat(8), String.join("", pages));
        for (String page : pages) {
            assertFalse(Character.isLowSurrogate(page.charAt(0)), "page starts mid-pair");
            assertFalse(Character.isHighSurrogate(page.charAt(page.length() - 1)), "page ends mid-pair");
        }
    }

    @Test
    void byteLimitWithMultibyteText() {
        String text = "ёжик — тест".repeat(10); // 2- and 3-byte UTF-8 chars
        List<String> pages = LosslessSplit.split(text, maxBytes(40), 2048, 1000).pages();
        assertEquals(text, String.join("", pages));
        for (String page : pages) {
            assertTrue(page.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 40);
        }
    }

    /** A character that alone exceeds the fit test aborts the split and is reported. */
    @Test
    void unfittableCharacterIsReportedNotForced() {
        LosslessSplit.Result r = LosslessSplit.split("abc", text -> false, 2048, 1000);
        assertEquals("a", r.unfittable());
        assertTrue(r.pages().isEmpty());
        // A multi-byte character is reported as one code point.
        LosslessSplit.Result emoji = LosslessSplit.split("x😀y", maxBytes(3), 2048, 1000);
        assertEquals("😀", emoji.unfittable());
        assertEquals(List.of("x"), emoji.pages()); // consumed up to the culprit
        // A 4-byte cap fits one emoji per page — carried to the next page, never dropped.
        LosslessSplit.Result ok = LosslessSplit.split("😀😀", maxBytes(4), 2048, 1000);
        assertEquals(List.of("😀", "😀"), ok.pages());
        assertEquals(null, ok.unfittable());
    }

    /** A long word that fits on its own page moves there instead of being split. */
    @Test
    void longWordMovesWholeToNextPage() {
        String word = "W".repeat(35);
        List<String> pages = LosslessSplit.split("aaaaa " + word, maxChars(40), 2048, 100).pages();
        assertEquals(List.of("aaaaa ", word), pages);
    }

    /** The page budget stops the work early and reports the overflow. */
    @Test
    void pageBudgetStopsEarly() {
        LosslessSplit.Result r = LosslessSplit.split("x".repeat(100), maxChars(10), 2048, 3);
        assertTrue(r.overflow());
        assertEquals(3, r.pages().size());
        assertEquals("x".repeat(30), String.join("", r.pages())); // consumed prefix only
        LosslessSplit.Result exact = LosslessSplit.split("x".repeat(30), maxChars(10), 2048, 3);
        assertFalse(exact.overflow());
        assertEquals(3, exact.pages().size());
    }

    /** A pathologically huge input costs only the budget's worth of work. */
    @Test
    void hugeInputBoundedByBudget() {
        String huge = "word ".repeat(1_000_000); // 5M chars
        long start = System.nanoTime();
        LosslessSplit.Result r = LosslessSplit.split(huge, maxChars(1000), 1024, 101);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(r.overflow());
        assertEquals(101, r.pages().size());
        assertTrue(elapsedMs < 2000, "took " + elapsedMs + " ms"); // generous CI margin
    }

    /** The core contract, fuzzed: join(split(text)) == text, and every page fits. */
    @Test
    void fuzzJoinAlwaysEqualsInput() {
        String alphabet = "ab cd\nёж— 😀xyz \n\n  ";
        for (long seed = 1; seed <= 5; seed++) {
            Random random = new Random(seed);
            for (int round = 0; round < 200; round++) {
                StringBuilder sb = new StringBuilder();
                int length = random.nextInt(400);
                for (int i = 0; i < length; i++) {
                    int at = random.nextInt(alphabet.length());
                    char c = alphabet.charAt(at);
                    // Keep surrogate pairs intact in the source text too.
                    if (Character.isLowSurrogate(c)) {
                        continue;
                    }
                    if (Character.isHighSurrogate(c)) {
                        sb.append(c).append(alphabet.charAt(at + 1));
                    } else {
                        sb.append(c);
                    }
                }
                String text = sb.toString();
                int limit = 3 + random.nextInt(40);
                LosslessSplit.FitTester fit = random.nextBoolean() ? maxChars(limit) : maxBytes(limit + 3);
                List<String> pages = LosslessSplit.split(text, fit, 2048, 1000).pages();
                assertEquals(text, String.join("", pages), "seed=" + seed + " round=" + round);
                for (String page : pages) {
                    assertTrue(fit.fits(page), "page exceeds fit, seed=" + seed + ": [" + page + "]");
                }
                assertFalse(pages.isEmpty());
                for (int p = 0; p < pages.size(); p++) {
                    if (pages.get(p).isEmpty()) {
                        assertTrue(pages.size() == 1, "empty page in a multi-page result");
                    }
                }
            }
        }
    }
}
