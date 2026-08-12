package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageRangeTest {

    @Test
    void blankSelectsAllPages() {
        PageRange.Result r = PageRange.parse("   ", 4);
        assertTrue(r.ok());
        assertEquals(List.of(0, 1, 2, 3), r.pages());
    }

    @Test
    void nullSelectsAllPages() {
        assertEquals(List.of(0, 1, 2), PageRange.parse(null, 3).pages());
    }

    @Test
    void singlesAndRanges() {
        PageRange.Result r = PageRange.parse("1-3, 5", 10);
        assertTrue(r.ok());
        assertEquals(List.of(0, 1, 2, 4), r.pages());
    }

    @Test
    void overlapsAreDeduplicated() {
        PageRange.Result r = PageRange.parse("2-4, 3-5, 4", 10);
        assertEquals(List.of(1, 2, 3, 4), r.pages());
    }

    @Test
    void swappedBoundsAreNormalized() {
        assertEquals(List.of(4, 5, 6), PageRange.parse("7-5", 10).pages());
    }

    @Test
    void enDashAccepted() {
        assertEquals(List.of(0, 1), PageRange.parse("1–2", 5).pages());
    }

    @Test
    void garbageReportsBadToken() {
        PageRange.Result r = PageRange.parse("1-3, abc", 10);
        assertFalse(r.ok());
        assertEquals("abc", r.badToken());
    }

    @Test
    void zeroIsBad() {
        assertFalse(PageRange.parse("0", 10).ok());
    }

    @Test
    void fullyOutOfBounds() {
        PageRange.Result r = PageRange.parse("50-60", 10);
        assertFalse(r.ok());
        assertNull(r.badToken());
        assertTrue(r.outOfBounds());
    }

    @Test
    void partiallyOutOfBoundsClampsButFlags() {
        PageRange.Result r = PageRange.parse("9-15", 10);
        assertTrue(r.ok());
        assertEquals(List.of(8, 9), r.pages());
        assertTrue(r.outOfBounds());
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void hugeRangeReturnsInstantly() {
        PageRange.Result r = PageRange.parse("1-2147483647", 10);
        assertTrue(r.ok());
        assertEquals(10, r.pages().size());
        assertTrue(r.outOfBounds());
    }

    @Test
    @org.junit.jupiter.api.Timeout(5)
    void hugeFullyOutOfBoundsRangeReturnsInstantly() {
        PageRange.Result r = PageRange.parse("2147483000-2147483647", 5);
        assertFalse(r.ok());
        assertTrue(r.outOfBounds());
    }
}
