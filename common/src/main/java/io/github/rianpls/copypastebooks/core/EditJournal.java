package io.github.rianpls.copypastebooks.core;

import java.util.function.UnaryOperator;

/**
 * Editor-style undo journal: consecutive changes coalesce into one undo step (a typing
 * batch), while pastes, restores, bulk operations and edits on another page start a new
 * step. Callers pass the state from before each change; the journal decides
 * whether that state becomes an undo step. Undoing walks the steps back one at a time —
 * so text typed after a paste is undone first, then the paste itself, exactly like any
 * text editor.
 *
 * Undo/redo accept a function that builds the current state from the restored one, so a step
 * tag riding on the snapshot (e.g. "this step was a volume load") can be carried over to
 * the opposite stack and survive round trips.
 */
public final class EditJournal<T> {
    private final UndoStack<T> stack;
    private boolean batchOpen;

    public EditJournal(int limit) {
        this.stack = new UndoStack<>(limit);
    }

    /**
     * Records a change. {@code before} is the state before it; it is pushed as an undo
     * step when this change starts a new batch (or {@code forceNewStep} is set), and
     * coalesced into the current batch otherwise.
     */
    public void noteChange(T before, boolean forceNewStep) {
        if (forceNewStep || !batchOpen) {
            stack.push(before);
            batchOpen = true;
        }
    }

    /** Ends the current typing batch: the next change starts a new undo step. */
    public void boundary() {
        batchOpen = false;
    }

    /** @return the state to restore, or null when there is nothing to undo. */
    public T undo(UnaryOperator<T> currentFromRestored) {
        T restored = stack.undo(currentFromRestored);
        if (restored != null) {
            batchOpen = false;
        }
        return restored;
    }

    /** @return the state to restore, or null when there is nothing to redo. */
    public T redo(UnaryOperator<T> currentFromRestored) {
        T restored = stack.redo(currentFromRestored);
        if (restored != null) {
            batchOpen = false;
        }
        return restored;
    }

    public boolean canUndo() {
        return stack.canUndo();
    }

    public boolean canRedo() {
        return stack.canRedo();
    }
}
