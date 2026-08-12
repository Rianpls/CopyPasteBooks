package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TinyfdArgsTest {

    @Test
    void shellBackedDisplayHasNoShellSyntaxOrTinyfdQuotes() {
        assertEquals("Ivan\u2019s \u201Dbook\u201D \u02CBnote\u02CB \uFF04(HOME) \uFF3Cpath line",
                TinyfdArgs.display("Ivan's \"book\" `note` $(HOME) \\path\nline", true));
    }

    @Test
    void nativeWindowsDisplayKeepsHarmlessCharacters() {
        assertEquals("\u2019\u201D\u02CB$\\ ", TinyfdArgs.display("'\"`$\\\n", false));
    }

    @Test
    void pathDisplayAlwaysFitsTheActiveFilesystemEncoding() {
        String original = "Ivan's \u0414\u043d\u0435\u0432\u043d\u0438\u043a $(book).txt";
        String shown = TinyfdArgs.pathDisplay(original, true);
        assertNotNull(TinyfdArgs.pathOrNull(shown));

        if (TinyfdArgs.pathOrNull("\u2019\u0414") == null) {
            assertEquals("Ivan_s _______ _(book).txt", shown);
        } else {
            assertEquals("Ivan\u2019s \u0414\u043d\u0435\u0432\u043d\u0438\u043a \uFF04(book).txt", shown);
        }
    }

    @Test
    void nativeArgumentLengthIsBounded() {
        assertEquals(256, TinyfdArgs.display("x".repeat(10_000), true).length());
        String emoji = TinyfdArgs.display("\uD83D\uDE00".repeat(1_000), true);
        assertEquals(64, emoji.codePointCount(0, emoji.length()));
        assertEquals(256, emoji.getBytes(StandardCharsets.UTF_8).length);
        assertEquals("/" + "x".repeat(511), TinyfdArgs.startPath("/" + "x".repeat(511), true));
        assertEquals("", TinyfdArgs.startPath("/" + "x".repeat(10_000), true));
        assertEquals("", TinyfdArgs.startPath("/" + "\uD83D\uDE00".repeat(128), true));
    }

    @Test
    void shellBackedStartPathIsUsedOnlyVerbatim() {
        assertEquals("/home/rian/Books", TinyfdArgs.startPath("/home/rian/Books", true));
        assertEquals("", TinyfdArgs.startPath("/home/$USER/Books", true));
        assertEquals("", TinyfdArgs.startPath("/home/O'Brien/Books", true));
        assertEquals("", TinyfdArgs.startPath("/home/back\\slash", true));
        assertEquals("", TinyfdArgs.startPath("/home/line\nbreak", true));
    }

    @Test
    void nativeWindowsStartPathKeepsBackslashesAndDollar() {
        assertEquals("C:\\Users\\Rian$\\Books", TinyfdArgs.startPath("C:\\Users\\Rian$\\Books", false));
        assertEquals("", TinyfdArgs.startPath("C:\\Users\\O'Brien", false));
        assertEquals("", TinyfdArgs.startPath("C:\\Users\\`cmd`", false));
    }

    @Test
    void unchangedTemporaryNameIsRestored(@TempDir Path dir) {
        String original = "Ivan's $(book).txt";
        String shown = TinyfdArgs.pathDisplay(original, true);
        Path picked = dir.resolve(shown);
        assertEquals(dir.resolve(original), TinyfdArgs.restoreSuggestedName(picked, shown, original));
    }

    @Test
    void unrepresentableOriginalKeepsTheSafeName(@TempDir Path dir) {
        String original = "\u0414\u043d\u0435\u0432\u043d\u0438\u043a.txt";
        String shown = TinyfdArgs.pathDisplay(original, true);
        Path picked = dir.resolve(shown);
        Path originalPath = TinyfdArgs.pathOrNull(original);
        Path expected = originalPath == null ? picked : dir.resolve(originalPath);
        assertEquals(expected, TinyfdArgs.restoreSuggestedName(picked, shown, original));
    }

    @Test
    void editedOrUnsafeSuggestedNameIsNotRestored(@TempDir Path dir) {
        Path picked = dir.resolve("edited.txt");
        assertEquals(picked, TinyfdArgs.restoreSuggestedName(picked, "shown.txt", "original.txt"));
        assertEquals(picked, TinyfdArgs.restoreSuggestedName(picked, "edited.txt", "../outside.txt"));
        assertEquals(picked, TinyfdArgs.restoreSuggestedName(picked, "edited.txt", ".."));
        assertEquals(picked, TinyfdArgs.restoreSuggestedName(picked, "edited.txt", "C:outside.txt"));
        assertEquals(picked, TinyfdArgs.restoreSuggestedName(picked, "edited.txt", "line\nbreak.txt"));
    }

    @Test
    void restorationNeverSilentlyOverwritesAnotherFile(@TempDir Path dir) throws Exception {
        String originalName = "Ivan's Diary.txt";
        String shown = TinyfdArgs.pathDisplay(originalName, true);
        Path picked = dir.resolve(shown);
        Path original = dir.resolve(originalName);
        Files.writeString(original, "existing");
        assertEquals(picked,
                TinyfdArgs.restoreSuggestedName(picked, shown, originalName));
    }

    @Test
    void existingTemporaryNameIsNotRedirected(@TempDir Path dir) throws Exception {
        String original = "Ivan's Diary.txt";
        String shown = TinyfdArgs.pathDisplay(original, true);
        Path picked = dir.resolve(shown);
        Files.writeString(picked, "existing");
        assertEquals(picked,
                TinyfdArgs.restoreSuggestedName(picked, shown, original));
    }
}
