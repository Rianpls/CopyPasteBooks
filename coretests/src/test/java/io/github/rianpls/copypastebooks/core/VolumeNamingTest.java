package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VolumeNamingTest {

    @Test
    void plainNameGetsDashNumber() {
        assertEquals("Tale - 1", VolumeNaming.forVolume("Tale", 1, 15));
        assertEquals("Tale - 12", VolumeNaming.forVolume("Tale", 12, 15));
    }

    @Test
    void nameEndingInDigitGetsRoman() {
        assertEquals("Chapter2 III", VolumeNaming.forVolume("Chapter2", 3, 15));
        assertEquals("Book9 IV", VolumeNaming.forVolume("Book9", 4, 15));
    }

    @Test
    void longBaseIsTrimmedToFit() {
        String result = VolumeNaming.forVolume("Verylongtitlename", 2, 15);
        assertTrue(result.length() <= 15, "too long: " + result);
        assertTrue(result.endsWith(" - 2"), result);
        assertEquals("Verylongtit - 2", result);
    }

    @Test
    void blankBaseFallsBackToNumber() {
        assertEquals("5", VolumeNaming.forVolume("", 5, 15));
        assertEquals("7", VolumeNaming.forVolume("   ", 7, 15));
    }

    @Test
    void resultNeverExceedsLimit() {
        for (int i = 1; i <= 120; i++) {
            String r = VolumeNaming.forVolume("SomeBookName", i, 15);
            assertTrue(r.length() <= 15, "vol " + i + " -> '" + r + "'");
        }
    }

    @Test
    void romanNumerals() {
        assertEquals("I", VolumeNaming.roman(1));
        assertEquals("IV", VolumeNaming.roman(4));
        assertEquals("IX", VolumeNaming.roman(9));
        assertEquals("XIV", VolumeNaming.roman(14));
        assertEquals("XL", VolumeNaming.roman(40));
        assertEquals("XCIX", VolumeNaming.roman(99));
        assertEquals("MMXXVI", VolumeNaming.roman(2026));
    }
}
