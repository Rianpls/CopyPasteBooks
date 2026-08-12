package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MiscCoreTest {

    @Test
    void nearestLegacyColors() {
        assertEquals('0', LegacyColors.nearestCode(0x000000));
        assertEquals('f', LegacyColors.nearestCode(0xFFFFFF));
        assertEquals('4', LegacyColors.nearestCode(0xFF0000)); // closer to dark_red than red
        assertEquals('c', LegacyColors.nearestCode(0xFF6666));
        assertEquals('6', LegacyColors.nearestCode(0xFFAA00));
    }

    @Test
    void rgbLookup() {
        assertEquals(0xFF5555, LegacyColors.rgbOf('c'));
        assertEquals(0xFF5555, LegacyColors.rgbOf('C'));
        assertEquals(-1, LegacyColors.rgbOf('z'));
    }

    @Test
    void filenameSanitizing() {
        assertEquals("My Book", FileNamer.sanitizeBase("§6My §lBook", "book"));
        assertEquals("a b c", FileNamer.sanitizeBase("a/b\\c", "book"));
        assertEquals("dots", FileNamer.sanitizeBase("dots...", "book"));
        assertEquals("book", FileNamer.sanitizeBase("  §c  ", "book"));
        assertEquals(60, FileNamer.sanitizeBase("x".repeat(200), "book").length());
        assertEquals("_CON", FileNamer.sanitizeBase("CON", "book"));
        assertEquals("_nul.notes", FileNamer.sanitizeBase("nul.notes", "book"));
        assertEquals("_LPT9", FileNamer.sanitizeBase(" LPT9. ", "book"));
        assertEquals("COM10", FileNamer.sanitizeBase("COM10", "book"));
    }

    @Test
    void uniqueNaming() {
        Set<String> taken = Set.of("tale.txt", "tale (2).txt");
        assertEquals("tale (3).txt", FileNamer.uniqueTxt("tale", taken::contains));
        assertEquals("fresh.txt", FileNamer.uniqueTxt("fresh", taken::contains));
    }

    @Test
    void draftDirtyCheckMatchesVanillaTrailingPageTrim() {
        var original = BookDraft.canonicalPages(java.util.List.of("text", "", ""));
        assertEquals(java.util.List.of("text"), original);
        assertFalse(BookDraft.changed(original, java.util.List.of("text", "")));
        assertTrue(BookDraft.changed(original, java.util.List.of("text", " ")));
        assertTrue(BookDraft.changed(original, java.util.List.of("changed")));
        assertFalse(BookDraft.changed(java.util.List.of(), java.util.List.of("")));
    }

    @Test
    void configRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("copypastebooks.json");
        Config out = new Config();
        out.language = Config.LangMode.RU;
        out.destination = Config.Destination.FILE;
        out.formatting = Config.FormattingMode.WITH_CODES;
        out.saveFolder = "/home/user/books";
        out.pageMarkers = true;
        out.pageByteLimit = 256;
        out.multivolumeMethod = Config.MultivolumeMethod.MANUAL;
        // An explicitly saved value must win over the new default (false).
        out.autoNameVolumes = true;
        out.creativeDelayMs = 250;
        out.survivalDelayMs = 2000;
        out.autoHideVolumeSelector = false;
        out.smartPaste = false;
        out.closeBehavior = Config.CloseBehavior.SAVE;
        out.eraseBehavior = Config.EraseBehavior.IMMEDIATE;
        out.save(file);

        Config in = Config.load(file);
        assertEquals(Config.LangMode.RU, in.language);
        assertEquals(Config.Destination.FILE, in.destination);
        assertEquals(Config.FormattingMode.WITH_CODES, in.formatting);
        assertEquals("/home/user/books", in.saveFolder);
        assertTrue(in.pageMarkers);
        assertEquals(256, in.pageByteLimit);
        assertEquals(Config.MultivolumeMethod.MANUAL, in.multivolumeMethod);
        assertTrue(in.autoNameVolumes);
        assertEquals(250, in.creativeDelayMs);
        assertEquals(2000, in.survivalDelayMs);
        assertEquals(false, in.autoHideVolumeSelector);
        assertEquals(false, in.smartPaste);
        assertEquals(Config.CloseBehavior.SAVE, in.closeBehavior);
        assertEquals(Config.EraseBehavior.IMMEDIATE, in.eraseBehavior);
    }

    @Test
    void configSurvivesGarbage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("copypastebooks.json");
        Files.writeString(file, "{not json at all");
        Config in = Config.load(file);
        assertEquals(Config.LangMode.AUTO, in.language);

        Files.writeString(file, "{\"language\":\"klingon\",\"pageByteLimit\":-5,\"survivalDelayMs\":999999}");
        in = Config.load(file);
        assertEquals(Config.LangMode.AUTO, in.language);
        assertEquals(0, in.pageByteLimit);
        assertEquals(Config.MAX_DELAY_MS, in.survivalDelayMs);
    }

    @Test
    void oldConfigGetsSafeEraseDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("copypastebooks.json");
        Files.writeString(file, "{\"destination\":\"file\",\"eraseBehavior\":\"unknown\"}");

        Config in = Config.load(file);
        assertEquals(Config.Destination.FILE, in.destination);
        assertEquals(Config.EraseBehavior.ASK_DELAY, in.eraseBehavior);

        // A genuinely old config has no eraseBehavior key at all and must behave the same.
        Files.writeString(file, "{\"destination\":\"file\"}");
        assertEquals(Config.EraseBehavior.ASK_DELAY, Config.load(file).eraseBehavior);
    }

    @Test
    void configMissingFileGivesDefaults(@TempDir Path dir) {
        Config in = Config.load(dir.resolve("nope.json"));
        assertEquals(Config.LangMode.AUTO, in.language);
        assertEquals(Config.Destination.CLIPBOARD, in.destination);
        assertEquals(Config.FormattingMode.ASK, in.formatting);
        assertEquals("", in.saveFolder);
        assertEquals(0, in.pageByteLimit);
        assertEquals(Config.MultivolumeMethod.AUTO, in.multivolumeMethod);
        assertFalse(in.autoNameVolumes);
        assertTrue(in.autoHideVolumeSelector);
        assertTrue(in.smartPaste);
        assertEquals(Config.CloseBehavior.ASK, in.closeBehavior);
        assertEquals(Config.EraseBehavior.ASK_DELAY, in.eraseBehavior);
        assertEquals(Config.DEFAULT_CREATIVE_DELAY_MS, in.creativeDelayMs);
        assertEquals(100, in.creativeDelayMs);
        assertEquals(Config.DEFAULT_SURVIVAL_DELAY_MS, in.survivalDelayMs);
        assertEquals(1300, in.survivalDelayMs);
    }
}
