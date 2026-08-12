package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageMarkersTest {

    @Test
    void joinThenSplitRoundTrips() {
        List<String> pages = List.of("first page\nsecond line", "", "third page");
        String joined = PageMarkers.join(pages);
        assertTrue(PageMarkers.hasMarkers(joined));
        assertEquals(pages, PageMarkers.split(joined));
    }

    @Test
    void plainTextHasNoMarkers() {
        assertFalse(PageMarkers.hasMarkers("just some text\nwith === not a marker ==="));
    }

    @Test
    void preambleBecomesFirstPage() {
        String text = "intro text\n=== Page 1 ===\nbody";
        assertEquals(List.of("intro text", "body"), PageMarkers.split(text));
    }

    @Test
    void russianMarkerAccepted() {
        String text = "=== Страница 1 ===\nодин\n=== страница 2 ===\nдва";
        assertEquals(List.of("один", "два"), PageMarkers.split(text));
    }

    @Test
    void markerWithoutNumberAccepted() {
        String text = "=== Page ===\na\n=== Page ===\nb";
        assertEquals(List.of("a", "b"), PageMarkers.split(text));
    }

    @Test
    void markerNumbersAreInformationalOnly() {
        String text = "=== Page 7 ===\na\n=== Page 3 ===\nb";
        assertEquals(List.of("a", "b"), PageMarkers.split(text));
    }

    @Test
    void emptyPagesSurvive() {
        String joined = PageMarkers.join(List.of("", "", "x"));
        assertEquals(List.of("", "", "x"), PageMarkers.split(joined));
    }
}
