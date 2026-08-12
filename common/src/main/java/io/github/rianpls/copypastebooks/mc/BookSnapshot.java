package io.github.rianpls.copypastebooks.mc;

import java.util.List;

/**
 * Editor state for the undo journal: the pages, the page the player was on, and both
 * ends of the selection on that page — so undo/redo restores the cursor and selection
 * that existed before the edit. Equal {@code caret}/{@code anchor} means no selection.
 * {@code importedVolume} is the volume identity of the text in this snapshot, used by
 * the sign screen even after the player edits the text. {@code loadedVolume} tags the
 * STEP this snapshot begins (0 = plain edit); {@code volumeWasInserted} records whether
 * that volume was already counted before the step, so undo never removes an older mark.
 * {@code dropTrackedOnSave} is draft state too: a fresh import replaces an old colored
 * volume only after the player actually saves it, not while it can still be undone or
 * discarded.
 */
public record BookSnapshot(List<String> pages, int page, int caret, int anchor, int importedVolume,
                           int loadedVolume, boolean volumeWasInserted,
                           boolean dropTrackedOnSave) {
}
