package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LegacyTextTest {

    private static LegacyText.StyleState state(char color, String flags) {
        LegacyText.StyleState s = new LegacyText.StyleState();
        s.color = color;
        s.bold = flags.contains("l");
        s.italic = flags.contains("o");
        s.underline = flags.contains("n");
        s.strikethrough = flags.contains("m");
        s.obfuscated = flags.contains("k");
        return s;
    }

    @Test
    void detectionOnlyCountsEffectiveCodes() {
        assertTrue(LegacyText.hasEffectiveCodes("hello §cworld"));
        assertTrue(LegacyText.hasEffectiveCodes("§Lbold"));
        assertFalse(LegacyText.hasEffectiveCodes("price is 5§ per kg"));
        assertFalse(LegacyText.hasEffectiveCodes("weird §z code"));
        assertFalse(LegacyText.hasEffectiveCodes("trailing §"));
    }

    @Test
    void stripRemovesOnlyEffectiveCodes() {
        assertEquals("red and plain §z stays §", LegacyText.strip("§cred and §rplain §z stays §"));
    }

    @Test
    void plainToBold() {
        assertEquals("§l", LegacyText.transition(state((char) 0, ""), state((char) 0, "l")));
    }

    @Test
    void boldToPlainNeedsReset() {
        assertEquals("§r", LegacyText.transition(state((char) 0, "l"), state((char) 0, "")));
    }

    @Test
    void plainToColor() {
        assertEquals("§c", LegacyText.transition(state((char) 0, ""), state('c', "")));
    }

    @Test
    void addingStyleKeepsColorWithoutReemit() {
        assertEquals("§l", LegacyText.transition(state('c', ""), state('c', "l")));
    }

    @Test
    void removingStyleReemitsColor() {
        assertEquals("§c", LegacyText.transition(state('c', "l"), state('c', "")));
    }

    @Test
    void colorChangeReemitsStyles() {
        assertEquals("§9§o", LegacyText.transition(state('c', "l"), state('9', "o")));
    }

    @Test
    void losingColorUsesFullReset() {
        assertEquals("§r§l", LegacyText.transition(state('c', ""), state((char) 0, "l")));
    }

    @Test
    void identicalStatesEmitNothing() {
        assertEquals("", LegacyText.transition(state('c', "lo"), state('c', "lo")));
    }
}
