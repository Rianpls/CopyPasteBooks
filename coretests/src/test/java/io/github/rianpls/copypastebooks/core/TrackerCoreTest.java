package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * TrackerCore against a simulator of vanilla's container-click semantics, mirrored from
 * the decompiled 26.2 client: picking a stack up (Slot.tryRemove) and putting it down
 * (Slot.safeInsert) COPY the stack via ItemStack.split; number-key/offhand swaps and
 * cursor-vs-different-item swaps MOVE the original objects; clicking an identical book
 * onto an identical book is a no-op (same components, max stack size 1). Server resyncs
 * replace objects in place. Every simulated click wraps beginClick/endClick exactly like
 * the MultiPlayerGameMode hook does.
 */
class TrackerCoreTest {

    private static final int SLOTS = 41;
    private static final int OFFHAND = 40;
    private static final int ABSENT = -99;

    /** A simulated ItemStack: identity = the object, lineage = trueId (copies inherit it). */
    private static final class SimStack {
        final List<String> pages; // null = some non-book item
        final int trueId;

        SimStack(List<String> pages, int trueId) {
            this.pages = pages;
            this.trueId = trueId;
        }

        SimStack copy() {
            return new SimStack(pages, trueId);
        }
    }

    private static final class Sim implements TrackerCore.View {
        final TrackerCore core = new TrackerCore();
        final SimStack[] slots = new SimStack[SLOTS];
        SimStack cursor;

        @Override
        public int slotCount() {
            return SLOTS;
        }

        @Override
        public Object stackAt(int slot) {
            return slots[slot];
        }

        @Override
        public Object cursor() {
            return cursor;
        }

        @Override
        public List<String> pagesOf(Object token) {
            return ((SimStack) token).pages;
        }

        @Override
        public boolean claimable(int slot) {
            return slot < 36 || slot == OFFHAND;
        }

        long place(int slot, List<String> pages, int trueId, int color, int number) {
            slots[slot] = new SimStack(pages, trueId);
            return core.register(this, slot, pages, color, number);
        }

        void tick() {
            core.tick(this);
        }

        // ---- player clicks (each wrapped in begin/end like the real hook) ----

        /** PICKUP: copy to cursor / copy into slot / object swap; identical-on-identical = no-op. */
        void clickPickup(int slot) {
            core.beginClick(this, slot, -1);
            SimStack inSlot = slots[slot];
            if (cursor == null && inSlot != null) {
                cursor = inSlot.copy();
                slots[slot] = null;
            } else if (cursor != null && inSlot == null) {
                slots[slot] = cursor.copy();
                cursor = null;
            } else if (cursor != null && inSlot != null && !samePages(cursor, inSlot)) {
                slots[slot] = cursor;
                cursor = inSlot;
            }
            core.endClick(this);
        }

        /** QUICK_MOVE inside the own inventory screen: hotbar <-> main, copy into first free slot. */
        void clickQuickMove(int slot) {
            core.beginClick(this, slot, -1);
            if (slots[slot] != null) {
                boolean fromHotbar = slot < 9 || slot == OFFHAND;
                int from = fromHotbar ? 9 : 0;
                int to = fromHotbar ? 36 : 9;
                for (int i = from; i < to; i++) {
                    if (slots[i] == null) {
                        slots[i] = slots[slot].copy();
                        slots[slot] = null;
                        break;
                    }
                }
            }
            core.endClick(this);
        }

        /** QUICK_MOVE while a chest is open: the book leaves the player's inventory. */
        void clickQuickMoveToChest(int slot) {
            core.beginClick(this, slot, -1);
            slots[slot] = null;
            core.endClick(this);
        }

        /** PICKUP on a foreign (chest) slot with a carried book: it leaves the player domain. */
        void clickPutCursorToChest() {
            core.beginClick(this, -1, -1);
            cursor = null;
            core.endClick(this);
        }

        /** SWAP (number key / F): vanilla moves the original objects. */
        void clickSwapKey(int slot, int button) {
            core.beginClick(this, slot, button);
            SimStack tmp = slots[button];
            slots[button] = slots[slot];
            slots[slot] = tmp;
            core.endClick(this);
        }

        /**
         * SWAP (number key) while hovering a FOREIGN chest slot: our book goes into the
         * chest and the chest's stack lands in the hotbar slot (vanilla moves objects;
         * the clicked slot maps to no player index).
         */
        void clickSwapKeyWithForeign(int button, SimStack incoming) {
            core.beginClick(this, -1, button);
            slots[button] = incoming;
            core.endClick(this);
        }

        /** QUICK_MOVE of a FOREIGN chest slot: its stack lands in the player inventory. */
        void clickWithdrawFromChest(int destSlot, SimStack stack) {
            core.beginClick(this, -1, -1);
            slots[destSlot] = stack;
            core.endClick(this);
        }

        /** THROW (Q): the copy is dropped into the world. */
        void clickThrow(int slot) {
            core.beginClick(this, slot, -1);
            slots[slot] = null;
            core.endClick(this);
        }

        /** PICKUP outside the window: the carried book is dropped into the world. */
        void clickDropCursor() {
            core.beginClick(this, -1, -1);
            cursor = null;
            core.endClick(this);
        }

        // ---- server events (no click hook — they arrive in packet handlers) ----

        /** Full inventory resync: every object replaced in place (same content). */
        void resyncAll() {
            for (int i = 0; i < SLOTS; i++) {
                if (slots[i] != null) {
                    slots[i] = slots[i].copy();
                }
            }
            if (cursor != null) {
                cursor = cursor.copy();
            }
        }

        void resyncSlot(int slot) {
            if (slots[slot] != null) {
                slots[slot] = slots[slot].copy();
            }
        }

        /** Server-side rollback restoring a book the prediction removed. */
        void serverRestore(int slot, List<String> pages, int trueId) {
            slots[slot] = new SimStack(pages, trueId);
        }

        int findTrueId(int trueId) {
            if (cursor != null && cursor.trueId == trueId) {
                return TrackerCore.LOC_CURSOR;
            }
            for (int i = 0; i < SLOTS; i++) {
                if (slots[i] != null && slots[i].trueId == trueId) {
                    return i;
                }
            }
            return ABSENT;
        }

        private static boolean samePages(SimStack a, SimStack b) {
            return a.pages != null && a.pages.equals(b.pages);
        }
    }

    private static List<String> pages(String... p) {
        return List.of(p);
    }

    /**
     * Lineage invariants. Always: a LIVE mark's position must hold the stack carrying ITS
     * trueId — a number on a stranger is the cardinal sin. ONE exception, by design: a
     * mark revived at the EXACT position it lost its book (a server "rollback") may have
     * bound to an indistinguishable identical copy standing there — content-equal by
     * construction, so the number stays semantically true; the expectation re-binds to
     * that physical book. Additionally, for books that never left the player domain the
     * mark must be exactly where the book is.
     */
    private static void assertTracks(Sim sim, Map<Long, Integer> lineage, java.util.Set<Integer> external,
                                     Map<Long, Integer> lastLiveLoc) {
        for (Map.Entry<Long, Integer> entry : lineage.entrySet()) {
            long id = entry.getKey();
            int trueId = entry.getValue();
            int loc = sim.core.locationOf(id);
            if (loc >= 0 || loc == TrackerCore.LOC_CURSOR) {
                SimStack at = loc == TrackerCore.LOC_CURSOR ? sim.cursor : sim.slots[loc];
                assertTrue(at != null, "mark " + id + " located at an empty position " + loc);
                if (at.trueId != trueId) {
                    Integer prev = lastLiveLoc.get(id);
                    assertTrue(prev != null && prev == loc,
                            "mark " + id + " sits on a stranger at " + loc + " (was at " + prev + ")");
                    entry.setValue(at.trueId); // rollback re-bind: adopt the returned copy
                    trueId = at.trueId;
                }
                lastLiveLoc.put(id, loc);
            }
            if (!external.contains(trueId)) {
                int pos = sim.findTrueId(trueId);
                if (pos == ABSENT) {
                    assertTrue(loc == TrackerCore.LOC_LOST || loc == Integer.MIN_VALUE,
                            "mark " + id + " must be lost/dead, its book left; loc=" + loc);
                } else {
                    assertEquals(pos, loc, "mark " + id + " must follow its book");
                }
            }
        }
    }

    // ------------------------------------------------------------------ directed scenarios

    @Test
    void identicalBooksKeepDistinctNumbers() {
        Sim sim = new Sim();
        List<String> text = pages("same", "text");
        long id1 = sim.place(0, text, 101, 0xFF00FF00, 1);
        long id2 = sim.place(1, text, 102, 0xFF00FF00, 2);
        assertNotEquals(id1, id2);
        assertEquals(1, sim.core.markAt(0, sim.slots[0]).number());
        assertEquals(2, sim.core.markAt(1, sim.slots[1]).number());
    }

    @Test
    void dragToAnotherSlotFollows() {
        Sim sim = new Sim();
        long id = sim.place(3, pages("a"), 1, 0, 1);
        sim.clickPickup(3);
        sim.clickPickup(17);
        assertEquals(17, sim.core.locationOf(id));
        assertEquals(1, sim.core.markAt(17, sim.slots[17]).number());
    }

    /** THE regression: vanilla puts a COPY on the cursor, and the mark must survive there. */
    @Test
    void heldOnCursorIndefinitelyKeepsMark() {
        Sim sim = new Sim();
        long id = sim.place(3, pages("a"), 1, 0, 1);
        sim.clickPickup(3);
        for (int i = 0; i < 100; i++) {
            sim.tick();
            assertEquals(TrackerCore.LOC_CURSOR, sim.core.locationOf(id), "tick " + i);
        }
        sim.clickPickup(8);
        assertEquals(8, sim.core.locationOf(id));
    }

    @Test
    void identicalBooksSurviveDragsAndHolds() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id1 = sim.place(0, text, 101, 0, 1);
        long id2 = sim.place(1, text, 102, 0, 2);
        sim.clickPickup(0);          // twin #1 on cursor
        sim.tick();
        sim.tick();
        sim.clickQuickMove(1);       // twin #2 shift-clicked to main inventory
        sim.clickPickup(30);         // twin #1 placed elsewhere
        sim.tick();
        assertEquals(1, sim.core.markAt(30, sim.slots[30]).number());
        assertEquals(9, sim.core.locationOf(id2));
        assertEquals(30, sim.core.locationOf(id1));
    }

    @Test
    void swapKeyMovesMarksWithBooks() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id1 = sim.place(10, text, 101, 0, 1);
        long id2 = sim.place(4, text, 102, 0, 2);
        sim.clickSwapKey(10, 4); // number key: swaps the two identical books
        assertEquals(4, sim.core.locationOf(id1));
        assertEquals(10, sim.core.locationOf(id2));
    }

    @Test
    void offhandSwapKeepsMark() {
        Sim sim = new Sim();
        long id = sim.place(2, pages("a"), 1, 0, 1);
        sim.clickSwapKey(2, OFFHAND); // F key
        assertEquals(OFFHAND, sim.core.locationOf(id));
        sim.clickSwapKey(2, OFFHAND);
        assertEquals(2, sim.core.locationOf(id));
    }

    @Test
    void editKeepsNumberThroughResync() {
        Sim sim = new Sim();
        long id = sim.place(2, pages("old"), 1, 0, 1);
        // The editor mutates the same object and reports the new content...
        sim.slots[2] = new SimStack(pages("new"), 1); // component swap -> in MC same object; new object is even harsher
        sim.core.updateContent(id, pages("new"));
        sim.tick();
        assertEquals(2, sim.core.locationOf(id));
        // ...then the server confirms with a fresh object.
        sim.resyncSlot(2);
        sim.tick();
        assertEquals(2, sim.core.locationOf(id));
        // And the edited book still follows moves.
        sim.clickPickup(2);
        sim.tick();
        sim.clickPickup(25);
        assertEquals(25, sim.core.locationOf(id));
    }

    @Test
    void signingOneIdenticalKillsOnlyItsMark() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id1 = sim.place(0, text, 101, 0, 1);
        long id2 = sim.place(1, text, 102, 0, 2);
        sim.core.removeAtSlot(0); // the sign-screen hook
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id1));
        assertEquals(1, sim.core.locationOf(id2));
        assertEquals(2, sim.core.markAt(1, sim.slots[1]).number());
    }

    @Test
    void chestDepositKillsOnlyThatMark() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id1 = sim.place(0, text, 101, 0, 1);
        long id2 = sim.place(1, text, 102, 0, 2);
        sim.clickQuickMoveToChest(0);
        assertEquals(TrackerCore.LOC_LOST, sim.core.locationOf(id1)); // digit already invisible
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id1));
        assertEquals(1, sim.core.locationOf(id2)); // the twin didn't inherit the dead mark
    }

    @Test
    void cursorDepositAndDropDie() {
        Sim sim = new Sim();
        long id1 = sim.place(0, pages("a"), 1, 0, 1);
        long id2 = sim.place(1, pages("b"), 2, 0, 2);
        sim.clickPickup(0);
        sim.clickPutCursorToChest();
        sim.clickPickup(1);
        sim.clickDropCursor();
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id1));
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id2));
        assertTrue(sim.core.isEmpty());
    }

    /** Server rejects the predicted deposit and puts the book back: the number returns. */
    @Test
    void rollbackWithinGraceRestoresMark() {
        Sim sim = new Sim();
        long id = sim.place(5, pages("a"), 1, 0, 1);
        sim.clickQuickMoveToChest(5);
        sim.tick();
        sim.tick();
        sim.serverRestore(5, pages("a"), 1);
        sim.tick();
        assertEquals(5, sim.core.locationOf(id));
        assertEquals(1, sim.core.markAt(5, sim.slots[5]).number());
    }

    /** Resync replaces every object; identical books must re-claim their OWN slots. */
    @Test
    void fullResyncKeepsIdenticalBooksApart() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id1 = sim.place(0, text, 101, 0, 1);
        long id2 = sim.place(7, text, 102, 0, 2);
        long id3 = sim.place(20, text, 103, 0, 3);
        sim.resyncAll();
        sim.tick();
        assertEquals(0, sim.core.locationOf(id1));
        assertEquals(7, sim.core.locationOf(id2));
        assertEquals(20, sim.core.locationOf(id3));
    }

    /** Resync while a book rides the cursor (server also refreshes the carried stack). */
    @Test
    void resyncWhileCarriedRecoversOnCursor() {
        Sim sim = new Sim();
        long id = sim.place(3, pages("a"), 1, 0, 1);
        sim.clickPickup(3);
        sim.resyncAll();
        sim.tick();
        assertEquals(TrackerCore.LOC_CURSOR, sim.core.locationOf(id));
        sim.clickPickup(6);
        assertEquals(6, sim.core.locationOf(id));
    }

    /**
     * A resync re-creates the tracked book's object, and the player clicks something
     * UNRELATED before the next mod tick. The pre-click settle must classify the token
     * death as a resync (heal in place) — not blame it on the click, which would forbid
     * recovery (slot not empty → no rollback) and kill a mark whose book never moved.
     */
    @Test
    void resyncFollowedByUnrelatedClickKeepsMark() {
        Sim sim = new Sim();
        long id = sim.place(0, pages("mine"), 301, 0, 1);
        sim.slots[5] = new SimStack(pages("other"), 500);
        sim.resyncSlot(0);   // server sync between ticks
        sim.clickPickup(5);  // unrelated click before the next tick
        assertEquals(0, sim.core.locationOf(id));
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
        }
        assertEquals(0, sim.core.locationOf(id));
        assertEquals(1, sim.core.markAt(0, sim.slots[0]).number());
        // Same story with a full-inventory resync while dragging that unrelated book.
        sim.resyncAll();
        sim.clickPickup(9); // place the carried unrelated book
        assertEquals(0, sim.core.locationOf(id));
    }

    @Test
    void registerOnEmptySlotAgesOutWithoutPhantom() {
        Sim sim = new Sim();
        long id = sim.core.register(sim, 4, pages("a"), 0, 1); // nothing actually in the slot
        assertEquals(-1, sim.core.idAtSlot(4));
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    @Test
    void registerReplacesOldMarkInSlot() {
        Sim sim = new Sim();
        long id1 = sim.place(2, pages("a"), 1, 0, 1);
        long id2 = sim.place(2, pages("b"), 2, 0, 1);
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id1));
        assertEquals(2, sim.core.locationOf(id2));
    }

    @Test
    void clearForgetsEverything() {
        Sim sim = new Sim();
        sim.place(0, pages("a"), 1, 0, 1);
        sim.clickPickup(0); // leave a click snapshot dangling too
        sim.core.clear();
        assertTrue(sim.core.isEmpty());
        assertNull(sim.core.markByToken(sim.cursor));
        sim.core.endClick(sim); // must be a no-op, not a crash
    }

    @Test
    void lookupsArePureAndTokenChecked() {
        Sim sim = new Sim();
        long id = sim.place(0, pages("a"), 1, 0, 7);
        assertEquals(7, sim.core.markAt(0, sim.slots[0]).number());
        assertNull(sim.core.markAt(0, new SimStack(pages("a"), 99))); // foreign object: no match
        assertNull(sim.core.markAt(1, sim.slots[0]));                 // wrong slot: no match
        assertEquals(7, sim.core.markByToken(sim.slots[0]).number());
        sim.clickQuickMoveToChest(0);
        assertNull(sim.core.markByToken(sim.slots[0]));               // lost mark renders nowhere
        assertEquals(TrackerCore.LOC_LOST, sim.core.locationOf(id));
    }

    @Test
    void resolveAtRecoversMidResync() {
        Sim sim = new Sim();
        long id = sim.place(3, pages("a"), 1, 0, 1);
        sim.resyncAll(); // editor opens before the next tick ran
        assertEquals(id, sim.core.resolveAt(sim, 3));
    }

    // -------------------------------------------- lost-cause rules (marks vs identical strangers)

    /** A deposited book's mark must die — not jump onto an untracked identical book. */
    @Test
    void untrackedTwinNeverInheritsDepositedMark() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id = sim.place(0, text, 301, 0, 1);
        sim.slots[5] = new SimStack(text, 399); // plain identical book, never registered
        sim.clickQuickMoveToChest(0);
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
            assertNull(sim.core.markByToken(sim.slots[5]), "tick " + i + ": stranger got the number");
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    /** Taking a book back from a chest must not restore an expired number. */
    @Test
    void retrieveFromChestIntoOtherSlotStaysDead() {
        Sim sim = new Sim();
        List<String> text = pages("mine");
        long id = sim.place(0, text, 301, 0, 1);
        sim.clickQuickMoveToChest(0);
        sim.tick();
        sim.clickWithdrawFromChest(7, new SimStack(text, 301)); // same physical book, new copy
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
            assertNull(sim.core.markByToken(sim.slots[7]), "withdrawn book must stay unnumbered");
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    /**
     * The player (not a rollback) fills the freshly-emptied deposit slot with an identical
     * book: a click touched the position, so the revival eligibility is cancelled.
     */
    @Test
    void playerFillingDepositSlotDoesNotRevive() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id = sim.place(3, text, 301, 0, 1);
        sim.clickQuickMoveToChest(3); // numbered book deposited; slot 3 now empty
        sim.tick();
        sim.cursor = new SimStack(text, 399); // player carries a plain identical book...
        sim.clickPickup(3);                   // ...and places it right into slot 3
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
            assertNull(sim.core.markByToken(sim.slots[3]), "tick " + i + ": player-placed twin numbered");
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    /** Number-key swap over a chest slot: an identical incoming book must not steal the number. */
    @Test
    void foreignSwapReplacementNotClaimed() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id = sim.place(3, text, 301, 0, 1);
        SimStack incoming = new SimStack(text, 399);
        sim.clickSwapKeyWithForeign(3, incoming); // ours -> chest, identical stranger -> slot 3
        assertEquals(TrackerCore.LOC_LOST, sim.core.locationOf(id));
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
            assertNull(sim.core.markByToken(sim.slots[3]), "tick " + i + ": replacement got the number");
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    @Test
    void removeAtSlotAlsoRemovesTemporarilyLostMark() {
        Sim sim = new Sim();
        long id = sim.place(2, pages("a"), 1, 0, 1);
        sim.clickQuickMoveToChest(2); // mark is now LOST, last seen at slot 2
        assertEquals(TrackerCore.LOC_LOST, sim.core.locationOf(id));
        sim.core.removeAtSlot(2);
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
        // And a same-slot server restore must not resurrect the removed mark.
        sim.serverRestore(2, pages("a"), 1);
        sim.tick();
        assertNull(sim.core.markByToken(sim.slots[2]));
    }

    // ------------------------------------- creative screen (clicks may bypass the hook entirely)

    /** With the menu hook (clicked() is hooked even in creative): twin present, cursor tracked. */
    @Test
    void hookedCreativePickupWithTwinTracksCursor() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id = sim.place(1, text, 301, 0, 1);
        sim.slots[0] = new SimStack(text, 399); // untracked identical
        sim.clickPickup(1);
        assertEquals(TrackerCore.LOC_CURSOR, sim.core.locationOf(id));
        assertNull(sim.core.markByToken(sim.slots[0]));
        sim.clickPickup(9);
        assertEquals(9, sim.core.locationOf(id));
    }

    /** Belt-and-braces: a pickup with NO hook at all (modded path) still recovers via the cursor. */
    @Test
    void hooklessPickupWithoutTwinRecoversOnCursor() {
        Sim sim = new Sim();
        long id = sim.place(1, pages("solo"), 301, 0, 1);
        sim.cursor = sim.slots[1].copy(); // vanilla copies on pickup; no begin/end fired
        sim.slots[1] = null;
        sim.tick();
        assertEquals(TrackerCore.LOC_CURSOR, sim.core.locationOf(id));
    }

    /** Hookless pickup with an identical stranger: ambiguous — the number dies, never migrates. */
    @Test
    void hooklessPickupWithTwinDiesInsteadOfJumping() {
        Sim sim = new Sim();
        List<String> text = pages("twin");
        long id = sim.place(1, text, 301, 0, 1);
        sim.slots[0] = new SimStack(text, 399);
        sim.cursor = sim.slots[1].copy();
        sim.slots[1] = null;
        for (int i = 0; i < TrackerCore.GRACE_TICKS + 2; i++) {
            sim.tick();
            assertNull(sim.core.markByToken(sim.slots[0]), "tick " + i + ": stranger got the number");
        }
        assertEquals(Integer.MIN_VALUE, sim.core.locationOf(id));
    }

    // ------------------------------------------------------------------ fuzz

    /** Unique books: full op mix incl. deposits/throws/rollbacks — strict lineage always. */
    @Test
    void fuzzUniqueBooks() {
        for (long seed = 1; seed <= 4; seed++) {
            fuzz(seed, 600, false);
        }
    }

    /**
     * Identical books, UNTRACKED identical strangers in the inventory, plus deposits and
     * throws — the exact soup that let a mark migrate to a stranger before the lost-cause
     * rules. Marks must stay on their physical books or die, never migrate.
     */
    @Test
    void fuzzIdenticalBooks() {
        for (long seed = 1; seed <= 4; seed++) {
            fuzz(seed, 600, true);
        }
    }

    private void fuzz(long seed, int ops, boolean identical) {
        Random random = new Random(seed);
        Sim sim = new Sim();
        Map<Long, Integer> lineage = new LinkedHashMap<>();
        java.util.Set<Integer> external = new java.util.HashSet<>();
        List<List<String>> texts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            texts.add(identical ? pages("twin", "text") : pages("book" + i, "p2-" + i));
        }
        Map<Long, Integer> lastLoc = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            int slot = i < 3 ? i : 6 + i; // hotbar + main mix
            long id = sim.place(slot, texts.get(i), 100 + i, 0, i + 1);
            lineage.put(id, 100 + i);
            lastLoc.put(id, slot);
        }
        if (identical) {
            // Untracked identical strangers — they must never acquire a number.
            sim.slots[20] = new SimStack(texts.get(0), 900);
            sim.slots[21] = new SimStack(texts.get(0), 901);
        }

        String last = "";
        for (int op = 0; op < ops; op++) {
            int roll = random.nextInt(100);
            int slot = random.nextInt(SLOTS);
            if (slot >= 36 && slot != OFFHAND) {
                slot = random.nextInt(9); // don't click armor
            }
            if (roll < 42) {
                last = "pickup " + slot;
                sim.clickPickup(slot);
            } else if (roll < 56) {
                last = "quickMove " + slot;
                sim.clickQuickMove(slot);
            } else if (roll < 68) {
                int button = random.nextInt(10);
                int target = button == 9 ? OFFHAND : button;
                last = "swapKey " + slot + "<->" + target;
                sim.clickSwapKey(slot, target);
            } else if (roll < 78) {
                last = "resyncAll";
                sim.resyncAll();
            } else if (roll < 86) {
                last = "resyncSlot " + slot;
                sim.resyncSlot(slot);
            } else if (roll < 90) {
                last = "throw " + slot;
                if (sim.slots[slot] != null) {
                    external.add(sim.slots[slot].trueId);
                }
                sim.clickThrow(slot);
            } else if (roll < 96) {
                if (sim.cursor != null) {
                    last = "cursorToChest";
                    external.add(sim.cursor.trueId);
                    sim.clickPutCursorToChest();
                } else {
                    last = "depositChest " + slot;
                    if (sim.slots[slot] != null) {
                        external.add(sim.slots[slot].trueId);
                    }
                    sim.clickQuickMoveToChest(slot);
                }
            } else {
                // Rollback/withdraw: an absent book comes back in a random free slot.
                boolean done = false;
                for (Map.Entry<Long, Integer> entry : lineage.entrySet()) {
                    int trueId = entry.getValue();
                    if (sim.findTrueId(trueId) == ABSENT) {
                        int free = -1;
                        for (int i = 0; i < 36; i++) {
                            if (sim.slots[i] == null) {
                                free = i;
                                break;
                            }
                        }
                        if (free >= 0) {
                            last = "return trueId " + trueId + " -> " + free;
                            sim.serverRestore(free, texts.get(trueId - 100), trueId);
                            done = true;
                        }
                        break;
                    }
                }
                if (!done) {
                    last = "pickup(fallback) " + slot;
                    sim.clickPickup(slot);
                }
            }
            sim.tick();
            try {
                assertTracks(sim, lineage, external, lastLoc);
                if (identical) {
                    for (int i = 0; i < SLOTS; i++) {
                        SimStack stack = sim.slots[i];
                        if (stack != null && stack.trueId >= 900) {
                            assertNull(sim.core.markByToken(stack), "stranger " + stack.trueId + " numbered");
                        }
                    }
                }
            } catch (AssertionError e) {
                throw new AssertionError("seed=" + seed + " op#" + op + " (" + last + "): " + e.getMessage(), e);
            }
            // Once a mark is dead, stop expecting anything of it.
            lineage.entrySet().removeIf(entry ->
                    sim.core.locationOf(entry.getKey()) == Integer.MIN_VALUE);
        }
        // No two live marks may ever share a position.
        List<Integer> seen = new ArrayList<>();
        for (Long id : lineage.keySet()) {
            int loc = sim.core.locationOf(id);
            if (loc >= 0 || loc == TrackerCore.LOC_CURSOR) {
                assertTrue(!seen.contains(loc), "two marks share position " + loc + " seed=" + seed);
                seen.add(loc);
            }
        }
    }
}
