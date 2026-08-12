package io.github.rianpls.copypastebooks.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.UnaryOperator;

/**
 * Minimal undo/redo storage for the book editor. Snapshots are opaque; a new
 * {@link #push} invalidates the redo branch, like any editor.
 */
public final class UndoStack<T> {
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();
    private final int limit;

    public UndoStack(int limit) {
        this.limit = limit;
    }

    /** Records the state as it was before an operation. */
    public void push(T beforeState) {
        undo.addLast(beforeState);
        while (undo.size() > limit) {
            undo.pollFirst();
        }
        redo.clear();
    }

    /** @return the state to restore, or null when there is nothing to undo. */
    public T undo(T currentState) {
        return undo(restored -> currentState);
    }

    /** @return the state to restore, or null when there is nothing to redo. */
    public T redo(T currentState) {
        return redo(restored -> currentState);
    }

    /**
     * Undo where the current state is built from the restored one. This lets a step tag on
     * the snapshot ride over to the redo stack and survive round trips.
     */
    public T undo(UnaryOperator<T> currentFromRestored) {
        T restored = undo.pollLast();
        if (restored != null) {
            redo.addLast(currentFromRestored.apply(restored));
        }
        return restored;
    }

    /** Redo counterpart of {@link #undo(UnaryOperator)}. */
    public T redo(UnaryOperator<T> currentFromRestored) {
        T restored = redo.pollLast();
        if (restored != null) {
            undo.addLast(currentFromRestored.apply(restored));
        }
        return restored;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }
}
