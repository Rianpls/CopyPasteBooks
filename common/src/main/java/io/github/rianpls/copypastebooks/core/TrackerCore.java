package io.github.rianpls.copypastebooks.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minecraft-independent identity tracker for colored volume numbers.
 *
 * Object identity is used while possible, but container clicks may replace ItemStack
 * instances. {@link #beginClick} and {@link #endClick} therefore compare the player's
 * slots and cursor before and after each local click, then move the corresponding mark.
 * {@link #tick} handles server resyncs by matching content and expires marks whose books
 * remain outside the player's inventory.
 *
 * Identical books remain ambiguous if the server swaps them without a client-side click;
 * in that case their numbers may swap as well. The caller provides synchronization.
 */
public final class TrackerCore {

    /** How the tracker sees the player's inventory. Tokens are opaque identity objects. */
    public interface View {
        /** Number of player inventory slots (vanilla: 41 = main+hotbar 0-35, armor 36-39, offhand 40). */
        int slotCount();

        /** Identity token of the stack in the slot, or null when empty. */
        Object stackAt(int slot);

        /** Identity token of the stack carried on the cursor, or null. */
        Object cursor();

        /** Pages of the token's unsigned book, or null when it is not one. */
        List<String> pagesOf(Object token);

        /** False for slots a book can never claim by content (armor). */
        boolean claimable(int slot);
    }

    public record Mark(long id, int color, int number) {
    }

    public static final int GRACE_TICKS = 15;
    public static final int LOC_CURSOR = -2;
    public static final int LOC_LOST = -3;

    /**
     * Controls content-based recovery. A resync may recreate a stack anywhere in the
     * inventory. A click that moved a book out may recover only at its previous position,
     * which avoids assigning the number to another identical book.
     */
    private enum LostCause {
        RESYNC, CLICK
    }

    private static final class Tracked {
        final long id;
        final int color;
        final int number;
        List<String> pages;
        Object token;
        int loc;      // slot >= 0, LOC_CURSOR, or LOC_LOST
        int lostFrom; // previous slot or cursor location used for recovery
        long lostSince;
        LostCause lostCause = LostCause.RESYNC;
        boolean rollbackEligible; // the click left lostFrom empty
        int prevLoc;  // location captured by beginClick

        Tracked(long id, int color, int number, List<String> pages) {
            this.id = id;
            this.color = color;
            this.number = number;
            this.pages = pages;
        }
    }

    private final Map<Long, Tracked> byId = new LinkedHashMap<>();
    private long nextId = 1;
    private long now;

    // Snapshot taken by beginClick, consumed by endClick.
    private Object[] preSlots;
    private Object preCursor;
    private int clickedSlot = -1;
    private int swapSlot = -1;

    // ------------------------------------------------------------------ bookkeeping

    /**
     * Registers a freshly filled book sitting in {@code slot}, replacing any older mark
     * there. Call after the local stack was updated so the current token gets captured.
     */
    public long register(View view, int slot, List<String> pages, int color, int number) {
        removeAtSlot(slot);
        Tracked tracked = new Tracked(nextId++, color, number, List.copyOf(pages));
        tracked.token = view.stackAt(slot);
        tracked.lostFrom = slot;
        if (tracked.token != null) {
            tracked.loc = slot;
        } else {
            tracked.loc = LOC_LOST; // not visible yet; the next tick claims it by content
            tracked.lostSince = now;
            tracked.lostCause = LostCause.RESYNC;
        }
        byId.put(tracked.id, tracked);
        return tracked.id;
    }

    /** Keeps the number across a manual edit — only the known content changes. */
    public void updateContent(long id, List<String> pages) {
        Tracked tracked = byId.get(id);
        if (tracked != null) {
            tracked.pages = List.copyOf(pages);
        }
    }

    public void remove(long id) {
        byId.remove(id);
    }

    /** Removes the mark that is — or, while briefly lost, was last seen — at the slot. */
    public void removeAtSlot(int slot) {
        byId.values().removeIf(tracked ->
                tracked.loc == slot || (tracked.loc == LOC_LOST && tracked.lostFrom == slot));
    }

    public void clear() {
        byId.clear();
        preSlots = null;
        preCursor = null;
        clickedSlot = -1;
        swapSlot = -1;
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public long idAtSlot(int slot) {
        for (Tracked tracked : byId.values()) {
            if (tracked.loc == slot) {
                return tracked.id;
            }
        }
        return -1;
    }

    /** {@link #LOC_LOST} while missing, {@link Integer#MIN_VALUE} when the id is gone. */
    public int locationOf(long id) {
        Tracked tracked = byId.get(id);
        return tracked == null ? Integer.MIN_VALUE : tracked.loc;
    }

    // ------------------------------------------------------------------ pure lookups (render path)

    /** Mark of the given slot, only when the token matches — no side effects. */
    public Mark markAt(int slot, Object token) {
        if (token == null) {
            return null;
        }
        for (Tracked tracked : byId.values()) {
            if (tracked.loc == slot && tracked.token == token) {
                return new Mark(tracked.id, tracked.color, tracked.number);
            }
        }
        return null;
    }

    /** Mark of the token wherever it sits in a slot — for renderers without a slot index. */
    public Mark markByToken(Object token) {
        if (token == null) {
            return null;
        }
        for (Tracked tracked : byId.values()) {
            if (tracked.token == token && tracked.loc >= 0) {
                return new Mark(tracked.id, tracked.color, tracked.number);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ click transitions

    /** Call before the client applies a container click locally. */
    public void beginClick(View view, int clickedInventorySlot, int swapInventorySlot) {
        // Resolve server changes first so the following diff contains only this click.
        // Otherwise a resync just before the click could be mistaken for a moved book.
        resolveTokens(view, LostCause.RESYNC);
        recoverByContent(view, false);

        int count = view.slotCount();
        preSlots = new Object[count];
        for (int i = 0; i < count; i++) {
            preSlots[i] = view.stackAt(i);
        }
        preCursor = view.cursor();
        clickedSlot = clickedInventorySlot;
        swapSlot = swapInventorySlot;
        for (Tracked tracked : byId.values()) {
            tracked.prevLoc = tracked.loc;
        }
    }

    /** Call after the click was applied locally; re-homes the involved marks. */
    public void endClick(View view) {
        if (preSlots == null) {
            return;
        }
        Object[] pre = preSlots;
        Object preCur = preCursor;
        int clicked = clickedSlot;
        int swap = swapSlot;
        preSlots = null;
        preCursor = null;
        clickedSlot = -1;
        swapSlot = -1;

        resolveTokens(view, LostCause.CLICK);

        // Positions this click actually touched: only they can be a moved book's new home.
        List<Integer> changedSlots = new ArrayList<>();
        for (int i = 0; i < pre.length; i++) {
            if (view.stackAt(i) != pre[i]) {
                changedSlots.add(i);
            }
        }
        boolean cursorChanged = view.cursor() != preCur;

        // Recovery at the old position is valid only until another click touches it.
        // After that, an identical stack there may be a different book.
        for (Tracked tracked : byId.values()) {
            if (tracked.loc == LOC_LOST && tracked.prevLoc == LOC_LOST && tracked.rollbackEligible) {
                boolean touched = tracked.lostFrom == LOC_CURSOR
                        ? cursorChanged
                        : changedSlots.contains(tracked.lostFrom);
                if (touched) {
                    tracked.rollbackEligible = false;
                }
            }
        }

        List<Tracked> pending = new ArrayList<>();
        for (Tracked tracked : lostMarks()) {
            if (tracked.prevLoc != LOC_LOST) {
                pending.add(tracked); // lost by this click
            } else if (tracked.lostCause == LostCause.RESYNC
                    && (tracked.lostFrom == clicked || tracked.lostFrom == swap
                        || tracked.lostFrom == LOC_CURSOR)) {
                // Follow a resynced book that was clicked before the next recovery tick.
                // Click-lost marks stay excluded because they may refer to another book.
                pending.add(tracked);
            }
        }
        if (pending.isEmpty()) {
            return;
        }

        Set<Integer> takenSlots = new HashSet<>();
        boolean[] cursorTaken = {false};
        for (Tracked tracked : byId.values()) {
            if (tracked.loc >= 0) {
                takenSlots.add(tracked.loc);
            } else if (tracked.loc == LOC_CURSOR) {
                cursorTaken[0] = true;
            }
        }

        // Round 1: transition hints — "the clicked slot's book went to the cursor",
        // "the cursor's book went into the clicked slot", "the swap key's two slots traded".
        List<Tracked> undecided = new ArrayList<>();
        for (Tracked tracked : pending) {
            int effPrev = tracked.prevLoc == LOC_LOST ? tracked.lostFrom : tracked.prevLoc;
            Integer preferred = null;
            if (effPrev >= 0 && effPrev == clicked) {
                if (cursorChanged) {
                    preferred = LOC_CURSOR;
                } else if (swap >= 0 && changedSlots.contains(swap)) {
                    preferred = swap;
                }
            } else if (effPrev == LOC_CURSOR && clicked >= 0 && changedSlots.contains(clicked)) {
                preferred = clicked;
            } else if (effPrev >= 0 && effPrev == swap && clicked >= 0 && changedSlots.contains(clicked)) {
                preferred = clicked;
            }
            if (preferred != null
                    && isCandidate(view, tracked, preferred, changedSlots, cursorChanged, takenSlots, cursorTaken)) {
                claim(view, tracked, preferred, takenSlots, cursorTaken);
            } else {
                undecided.add(tracked);
            }
        }

        // Round 2: exactly one touched position matches the book — that's where it went.
        // The position the book left is never a candidate: after a swap with a foreign
        // container slot, the incoming replacement (possibly an identical book) sits right
        // where ours was, and it must not inherit the number.
        for (Tracked tracked : undecided) {
            int effPrev = tracked.prevLoc == LOC_LOST ? tracked.lostFrom : tracked.prevLoc;
            List<Integer> candidates = new ArrayList<>();
            for (int slot : changedSlots) {
                if (slot != effPrev
                        && isCandidate(view, tracked, slot, changedSlots, cursorChanged, takenSlots, cursorTaken)) {
                    candidates.add(slot);
                }
            }
            if (effPrev != LOC_CURSOR
                    && isCandidate(view, tracked, LOC_CURSOR, changedSlots, cursorChanged, takenSlots, cursorTaken)) {
                candidates.add(LOC_CURSOR);
            }
            if (candidates.size() == 1) {
                claim(view, tracked, candidates.get(0), takenSlots, cursorTaken);
            } else {
                setLost(view, tracked, LostCause.CLICK);
            }
        }
    }

    // ------------------------------------------------------------------ per-tick safety net

    /** Once per client tick: follow tokens, recover resynced books by content, age out the gone. */
    public void tick(View view) {
        now++;
        if (byId.isEmpty()) {
            return;
        }
        resolveTokens(view, LostCause.RESYNC);
        recoverByContent(view, true);
    }

    /** Non-aging resolve used when a caller needs a fresh answer right now (editor init). */
    public long resolveAt(View view, int slot) {
        resolveTokens(view, LostCause.RESYNC);
        recoverByContent(view, false);
        return idAtSlot(slot);
    }

    /**
     * Pass 1-2: follow object identity. Cheap check on the known location first, then a
     * full scan (hotbar and cursor swaps can move the original objects between slots).
     * {@code cause} classifies any loss detected here: called from endClick it is the
     * click that consumed the token, called from the tick it is a server resync.
     */
    private void resolveTokens(View view, LostCause cause) {
        List<Tracked> moved = null;
        for (Tracked tracked : byId.values()) {
            // A null token (registered into a not-yet-synced slot) never matches anything —
            // without the guard it would "match" an empty cursor via null == null.
            if (tracked.token != null) {
                if (tracked.loc >= 0 && view.stackAt(tracked.loc) == tracked.token) {
                    continue;
                }
                if (tracked.loc == LOC_CURSOR && view.cursor() == tracked.token) {
                    continue;
                }
            }
            if (moved == null) {
                moved = new ArrayList<>();
            }
            moved.add(tracked);
        }
        if (moved == null) {
            return;
        }
        int count = view.slotCount();
        for (Tracked tracked : moved) {
            int found = LOC_LOST;
            if (tracked.token != null) {
                if (view.cursor() == tracked.token) {
                    found = LOC_CURSOR;
                } else {
                    for (int i = 0; i < count; i++) {
                        if (view.stackAt(i) == tracked.token) {
                            found = i;
                            break;
                        }
                    }
                }
            }
            if (found == LOC_LOST) {
                setLost(view, tracked, cause);
            } else {
                setLocation(tracked, found);
            }
        }
    }

    /**
     * Pass 3: content recovery of lost marks, gated by the loss cause.
     *
     * First, try the position where the book disappeared. Click-lost marks require that
     * the click left that position empty; otherwise an identical replacement could take
     * the number.
     *
     * For resync losses, search the remaining inventory and recover only a unique content
     * match. Click-lost books are not searched globally because they may have left the
     * player's inventory.
     */
    private void recoverByContent(View view, boolean age) {
        List<Tracked> lost = lostMarks();
        if (lost.isEmpty()) {
            return;
        }
        Set<Integer> takenSlots = new HashSet<>();
        boolean[] cursorTaken = {false};
        for (Tracked tracked : byId.values()) {
            if (tracked.loc >= 0) {
                takenSlots.add(tracked.loc);
            } else if (tracked.loc == LOC_CURSOR) {
                cursorTaken[0] = true;
            }
        }

        // First try the previous location.
        List<Tracked> remaining = new ArrayList<>();
        for (Tracked tracked : lost) {
            boolean allowed = tracked.lostCause == LostCause.RESYNC || tracked.rollbackEligible;
            if (allowed && contentMatches(view, tracked, tracked.lostFrom, takenSlots, cursorTaken)) {
                claim(view, tracked, tracked.lostFrom, takenSlots, cursorTaken);
            } else {
                remaining.add(tracked);
            }
        }

        // Then search resynced books by content, accepting only one match.
        for (Tracked tracked : remaining) {
            int found = LOC_LOST;
            if (tracked.lostCause == LostCause.RESYNC) {
                int matches = 0;
                int count = view.slotCount();
                for (int slot = 0; slot < count && matches < 2; slot++) {
                    if (contentMatches(view, tracked, slot, takenSlots, cursorTaken)) {
                        matches++;
                        found = slot;
                    }
                }
                if (matches < 2 && contentMatches(view, tracked, LOC_CURSOR, takenSlots, cursorTaken)) {
                    matches++;
                    found = LOC_CURSOR;
                }
                if (matches != 1) {
                    found = LOC_LOST; // zero or ambiguous — better no number than a wrong one
                }
            }
            if (found != LOC_LOST) {
                claim(view, tracked, found, takenSlots, cursorTaken);
            } else if (age && now - tracked.lostSince > GRACE_TICKS) {
                byId.remove(tracked.id);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean isCandidate(View view, Tracked tracked, int position, List<Integer> changedSlots,
                                boolean cursorChanged, Set<Integer> takenSlots, boolean[] cursorTaken) {
        if (position == LOC_CURSOR) {
            return cursorChanged && contentMatches(view, tracked, LOC_CURSOR, takenSlots, cursorTaken);
        }
        return changedSlots.contains(position) && contentMatches(view, tracked, position, takenSlots, cursorTaken);
    }

    private boolean contentMatches(View view, Tracked tracked, int position,
                                   Set<Integer> takenSlots, boolean[] cursorTaken) {
        Object token;
        if (position == LOC_CURSOR) {
            if (cursorTaken[0]) {
                return false;
            }
            token = view.cursor();
        } else if (position >= 0 && position < view.slotCount()) {
            if (takenSlots.contains(position) || !view.claimable(position)) {
                return false;
            }
            token = view.stackAt(position);
        } else {
            return false;
        }
        return token != null && tracked.pages.equals(view.pagesOf(token));
    }

    private void claim(View view, Tracked tracked, int position, Set<Integer> takenSlots, boolean[] cursorTaken) {
        tracked.token = position == LOC_CURSOR ? view.cursor() : view.stackAt(position);
        setLocation(tracked, position);
        if (position == LOC_CURSOR) {
            cursorTaken[0] = true;
        } else {
            takenSlots.add(position);
        }
    }

    private void setLocation(Tracked tracked, int location) {
        tracked.loc = location;
        tracked.lostFrom = location;
    }

    private void setLost(View view, Tracked tracked, LostCause cause) {
        if (tracked.loc != LOC_LOST) {
            tracked.lostFrom = tracked.loc;
            tracked.loc = LOC_LOST;
            tracked.lostSince = now;
            tracked.lostCause = cause;
            // A rollback is recognizable only if the click left the old position empty.
            tracked.rollbackEligible = cause == LostCause.CLICK
                    && (tracked.lostFrom == LOC_CURSOR ? view.cursor() == null
                        : view.stackAt(tracked.lostFrom) == null);
        }
    }

    private List<Tracked> lostMarks() {
        List<Tracked> lost = new ArrayList<>();
        for (Tracked tracked : byId.values()) {
            if (tracked.loc == LOC_LOST) {
                lost.add(tracked);
            }
        }
        return lost;
    }
}
