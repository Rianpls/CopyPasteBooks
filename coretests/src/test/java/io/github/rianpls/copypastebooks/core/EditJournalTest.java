package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EditJournalTest {

    /** The reported scenario: type, paste, type more — undo peels one step at a time. */
    @Test
    void typingAfterPasteUndoesSeparately() {
        EditJournal<String> j = new EditJournal<>(64);
        // Typing "ab" — one batch, baseline pushed on the first keystroke.
        j.noteChange("", false);
        j.noteChange("a", false);
        // Paste "PP" — its own step.
        j.noteChange("ab", true);
        j.boundary();
        // Typing "xy" after the paste — a new batch.
        j.noteChange("abPP", false);
        j.noteChange("abPPx", false);
        // Ctrl+Z #1 removes only the typed tail...
        assertEquals("abPP", j.undo(r -> "abPPxy"));
        // ...#2 removes the paste...
        assertEquals("ab", j.undo(r -> "abPP"));
        // ...#3 removes the typing.
        assertEquals("", j.undo(r -> "ab"));
        assertFalse(j.canUndo());
        // Redo walks forward through the same steps.
        assertEquals("ab", j.redo(r -> ""));
        assertEquals("abPP", j.redo(r -> "ab"));
        assertEquals("abPPxy", j.redo(r -> "abPP"));
        assertFalse(j.canRedo());
    }

    @Test
    void consecutiveTypingCoalescesIntoOneStep() {
        EditJournal<String> j = new EditJournal<>(64);
        j.noteChange("", false);
        j.noteChange("a", false);
        j.noteChange("ab", false);
        j.noteChange("abc", false);
        assertEquals("", j.undo(r -> "abcd"));
        assertFalse(j.canUndo());
    }

    @Test
    void typingAfterUndoStartsANewStep() {
        EditJournal<String> j = new EditJournal<>(64);
        j.noteChange("", false);
        assertEquals("", j.undo(r -> "abc"));
        // New typing after the undo — its own step (and it kills the redo branch).
        j.noteChange("", false);
        assertFalse(j.canRedo());
        assertEquals("", j.undo(r -> "xyz"));
    }

    @Test
    void pageSwitchForcesANewStep() {
        EditJournal<String> j = new EditJournal<>(64);
        j.noteChange("p1:", false);   // typing on page 1
        j.noteChange("p1:a|p2:", true); // first edit on page 2 forces a new step
        assertEquals("p1:a|p2:", j.undo(r -> "p1:a|p2:b"));
        assertEquals("p1:", j.undo(r -> "p1:a|p2:"));
    }

    @Test
    void bulkOperationIsItsOwnStep() {
        EditJournal<String> j = new EditJournal<>(64);
        j.noteChange("text", false);   // typing batch open
        j.noteChange("text2", true);   // erase/import: forced step
        j.boundary();
        j.noteChange("", false);       // typing after the erase
        assertEquals("", j.undo(r -> "z"));
        assertEquals("text2", j.undo(r -> ""));
        assertEquals("text", j.undo(r -> "text2"));
    }

    @Test
    void limitDropsOldestSteps() {
        EditJournal<String> j = new EditJournal<>(2);
        j.noteChange("v1", true);
        j.noteChange("v2", true);
        j.noteChange("v3", true);
        assertEquals("v3", j.undo(r -> "v4"));
        assertEquals("v2", j.undo(r -> "v3"));
        assertFalse(j.canUndo());
        assertNull(j.undo(r -> "v2"));
    }

    /** A step tag riding on the snapshot must survive undo/redo round trips. */
    @Test
    void stepTagRidesAcrossUndoRedo() {
        record Snap(String text, int tag) {
        }
        EditJournal<Snap> j = new EditJournal<>(8);
        j.noteChange(new Snap("before", 7), true); // e.g. "volume 7 was loaded here"
        j.boundary();
        // Undo: current state is built FROM the restored snapshot — the tag carries over.
        Snap restored = j.undo(r -> new Snap("after", r.tag()));
        assertEquals(new Snap("before", 7), restored);
        // Redo returns the after-state built above, tag intact...
        Snap redone = j.redo(r -> new Snap("before", r.tag()));
        assertEquals(new Snap("after", 7), redone);
        // ...and the tag still survives another full round trip.
        assertEquals(new Snap("before", 7), j.undo(r -> new Snap("after", r.tag())));
        assertEquals(new Snap("after", 7), j.redo(r -> new Snap("before", r.tag())));
    }

    @Test
    void emptyJournalDoesNothing() {
        EditJournal<String> j = new EditJournal<>(8);
        assertNull(j.undo(r -> "x"));
        assertNull(j.redo(r -> "x"));
        assertFalse(j.canUndo());
        assertFalse(j.canRedo());
        assertTrue(true);
    }
}
