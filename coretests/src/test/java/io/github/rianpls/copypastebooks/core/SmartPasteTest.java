package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmartPasteTest {

    /** Verbatim splitter: pages of at most n chars, honoring the page budget. */
    private static SmartPaste.Splitter chunks(int n) {
        return (text, maxPages) -> {
            List<String> pages = new ArrayList<>();
            for (int i = 0; i < text.length(); i += n) {
                if (pages.size() >= maxPages) {
                    return new LosslessSplit.Result(pages, true, null);
                }
                pages.add(text.substring(i, Math.min(text.length(), i + n)));
            }
            if (pages.isEmpty()) {
                pages.add("");
            }
            return new LosslessSplit.Result(pages, false, null);
        };
    }

    @Test
    void pasteIntoEmptyBook() {
        SmartPaste.Result r = SmartPaste.paste(List.of(), 0, 0, 0, "hello", chunks(3), 100);
        assertEquals(List.of("hel", "lo"), r.pages());
        assertEquals(1, r.focusPage());
        assertEquals(2, r.caret());
        assertFalse(r.truncated());
        assertFalse(r.rejected());
    }

    @Test
    void pasteIsVerbatimNoAddedNewlines() {
        // The exact review case: "ab|cd" + "X" on a page with room must stay "abXcd".
        SmartPaste.Result r = SmartPaste.paste(List.of("abcd"), 0, 2, 2, "X", chunks(100), 100);
        assertEquals(List.of("abXcd"), r.pages());
        assertEquals(0, r.focusPage());
        assertEquals(3, r.caret());
    }

    @Test
    void pasteAtCaretMiddleOfPageOverflows() {
        SmartPaste.Result r = SmartPaste.paste(List.of("abcd"), 0, 2, 2, "XY", chunks(3), 100);
        assertEquals(List.of("abX", "Ycd"), r.pages());
        assertEquals("abXYcd", String.join("", r.pages()));
        assertEquals(1, r.focusPage());
        assertEquals(1, r.caret()); // right after the pasted "Y"
    }

    @Test
    void selectionIsReplaced() {
        SmartPaste.Result r = SmartPaste.paste(List.of("abcd"), 0, 1, 3, "Z", chunks(10), 100);
        assertEquals(List.of("aZd"), r.pages());
        assertEquals(0, r.focusPage());
        assertEquals(2, r.caret());
    }

    @Test
    void reversedSelectionBoundsWork() {
        SmartPaste.Result r = SmartPaste.paste(List.of("abcd"), 0, 3, 1, "Z", chunks(10), 100);
        assertEquals(List.of("aZd"), r.pages());
    }

    @Test
    void overflowShiftsFollowingPagesUnchanged() {
        List<String> pages = List.of("one", "TWO", "THREE");
        SmartPaste.Result r = SmartPaste.paste(pages, 0, 3, 3, "XXXX", chunks(3), 100);
        assertEquals(List.of("one", "XXX", "X", "TWO", "THREE"), r.pages());
        assertEquals(2, r.focusPage());
        assertEquals(1, r.caret());
    }

    @Test
    void pasteOnMiddlePageLeavesNeighborsAlone() {
        List<String> pages = List.of("first", "mid", "last");
        SmartPaste.Result r = SmartPaste.paste(pages, 1, 3, 3, "dle", chunks(10), 100);
        assertEquals(List.of("first", "middle", "last"), r.pages());
        assertEquals(1, r.focusPage());
        assertEquals(6, r.caret());
    }

    @Test
    void caretOnPageBoundaryStaysOnTheEarlierPage() {
        // Paste ends exactly at a page break: the caret sits at the END of that page.
        SmartPaste.Result r = SmartPaste.paste(List.of("ab"), 0, 2, 2, "X", chunks(3), 100);
        assertEquals(List.of("abX"), r.pages());
        assertEquals(0, r.focusPage());
        assertEquals(3, r.caret());
    }

    // ------------------------------------------------------------- page cap behavior

    /** Old pages after the current one would be cut — the paste must be refused whole. */
    @Test
    void rejectedWhenShiftedPagesWouldBeCut() {
        List<String> pages = List.of("aa", "bb");
        SmartPaste.Result r = SmartPaste.paste(pages, 0, 2, 2, "XXXXXX", chunks(2), 3);
        assertTrue(r.rejected());
        assertFalse(r.truncated());
        assertEquals(pages, r.pages()); // untouched
    }

    /** The current page's own tail (after the caret) would be cut — refused too. */
    @Test
    void rejectedWhenOwnTailWouldBeCut() {
        SmartPaste.Result r = SmartPaste.paste(List.of("abcd"), 0, 2, 2, "XXXXXXXX", chunks(2), 2);
        assertTrue(r.rejected());
        assertEquals(List.of("abcd"), r.pages());
    }

    /** Pasting at the very end of the book: only the paste itself is trimmed — allowed. */
    @Test
    void trimmedWhenOnlyPasteTailIsCut() {
        SmartPaste.Result r = SmartPaste.paste(List.of("aa"), 0, 2, 2, "XXXXXX", chunks(2), 2);
        assertFalse(r.rejected());
        assertTrue(r.truncated());
        assertEquals(List.of("aa", "XX"), r.pages());
        assertEquals(1, r.focusPage());
        assertEquals(2, r.caret()); // end of the last kept page
    }

    @Test
    void undoStackRoundTrip() {
        UndoStack<String> stack = new UndoStack<>(3);
        assertFalse(stack.canUndo());
        assertNull(stack.undo("now"));
        stack.push("v1");
        stack.push("v2");
        assertEquals("v2", stack.undo("v3"));
        assertEquals("v1", stack.undo("v2"));
        assertFalse(stack.canUndo());
        assertEquals("v2", stack.redo("v1"));
        assertEquals("v3", stack.redo("v2"));
        assertFalse(stack.canRedo());
        stack.push("v3");
        assertEquals("v3", stack.undo("v4"));
        stack.push("a");
        assertNull(stack.redo("x"));
        UndoStack<Integer> small = new UndoStack<>(2);
        small.push(1);
        small.push(2);
        small.push(3);
        assertEquals(3, small.undo(4));
        assertEquals(2, small.undo(3));
        assertFalse(small.canUndo());
    }
}
