package io.github.rianpls.copypastebooks.mc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Text pasted into books lives here when it doesn't fit into one book (100 pages max).
 * The book edit screen shows a volume selector fed by this state; /importbook continues
 * with the next volume when repeated with a fresh book and quill.
 */
public final class VolumeState {
    public static final int MAX_PAGES_PER_BOOK = 100;

    private static List<List<String>> volumes = List.of();
    private static String title = "";
    /** 1-based volume shown/selected in the editor UI. */
    private static int selected = 1;
    /** One-shot flag so the sign screen prefills the title only while it's fresh. */
    private static boolean titleFresh = false;
    /** 1-based indices of volumes that were actually inserted into a book. */
    private static final Set<Integer> insertedVolumes = new HashSet<>();
    /** Slot-bound identity for command imports; content guards against stale slot reuse. */
    private static final Map<Integer, BoundVolume> boundVolumes = new HashMap<>();
    /** True once every volume was inserted and the auto-hide option kicked in. */
    private static boolean selectorHidden;

    private record BoundVolume(int volume, List<String> pages) {
    }

    private VolumeState() {
    }

    public static List<List<String>> splitIntoVolumes(List<String> pages) {
        List<List<String>> result = new ArrayList<>();
        for (int from = 0; from < pages.size(); from += MAX_PAGES_PER_BOOK) {
            result.add(List.copyOf(pages.subList(from, Math.min(pages.size(), from + MAX_PAGES_PER_BOOK))));
        }
        if (result.isEmpty()) {
            result.add(List.of());
        }
        return result;
    }

    public static void setPending(List<List<String>> newVolumes, String newTitle) {
        volumes = List.copyOf(newVolumes);
        title = newTitle == null ? "" : newTitle;
        selected = 1;
        titleFresh = !title.isBlank();
        insertedVolumes.clear();
        boundVolumes.clear();
        selectorHidden = false;
    }

    public static void clear() {
        volumes = List.of();
        title = "";
        selected = 1;
        titleFresh = false;
        insertedVolumes.clear();
        boundVolumes.clear();
        selectorHidden = false;
    }

    public static boolean hasMultiple() {
        return volumes.size() > 1 && !selectorHidden;
    }

    public static int count() {
        return volumes.size();
    }

    public static List<String> volume(int oneBased) {
        return volumes.get(oneBased - 1);
    }

    public static int selected() {
        return selected;
    }

    public static void select(int oneBased) {
        selected = Math.max(1, Math.min(volumes.isEmpty() ? 1 : volumes.size(), oneBased));
    }

    public static void markInserted(int volumeOneBased) {
        if (volumeOneBased < 1 || volumeOneBased > volumes.size()) {
            return;
        }
        insertedVolumes.add(volumeOneBased);
        // Once every volume has been inserted into a book, the selector hides itself
        // (configurable). The data stays so signing those books still prefills titles.
        if (volumes.size() > 1 && insertedVolumes.size() >= volumes.size()
                && CPB.config().autoHideVolumeSelector) {
            selectorHidden = true;
        }
    }

    /**
     * Undo of a volume load in the editor: that volume no longer counts as inserted and
     * a hidden selector comes back. No-op when no multivolume text is pending.
     */
    public static void markUninserted(int volumeOneBased) {
        if (volumes.isEmpty() || !insertedVolumes.remove(volumeOneBased)) {
            return;
        }
        selectorHidden = false;
    }

    public static boolean hasNextToInsert() {
        return volumes.size() > 1 && insertedVolumes.size() < volumes.size();
    }

    /** First volume not yet inserted, or -1 when the pending set is complete. */
    public static int nextToInsert() {
        for (int i = 1; i <= volumes.size(); i++) {
            if (!insertedVolumes.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isInserted(int volumeOneBased) {
        return insertedVolumes.contains(volumeOneBased);
    }

    /**
     * Title suggestion for the sign screen (max 15 chars, vanilla limit). Multi-volume
     * texts get a " N" suffix. Single-volume titles stop being suggested once a book
     * was actually signed ({@link #onBookSigned(int)}).
     */
    public static String suggestedSignTitle(int volumeOneBased) {
        if (title.isBlank() || volumeOneBased < 1 || volumeOneBased > volumes.size()) {
            return "";
        }
        if (volumes.size() > 1) {
            String suffix = " " + volumeOneBased;
            int room = 15 - suffix.length();
            String base = title.length() > room ? title.substring(0, room).trim() : title;
            return base + suffix;
        }
        if (!titleFresh) {
            return "";
        }
        return title.length() > 15 ? title.substring(0, 15).trim() : title;
    }

    /**
     * Remembers which imported volume currently occupies a hand slot. Unlike matching by
     * content alone this distinguishes identical volumes and cannot name an unrelated
     * book merely because its pages happen to equal an old import.
     */
    public static void rememberAtSlot(int slot, int volumeOneBased, List<String> pages) {
        if (volumeOneBased < 1 || volumeOneBased > volumes.size()) {
            boundVolumes.remove(slot);
            return;
        }
        boundVolumes.put(slot, new BoundVolume(volumeOneBased, List.copyOf(pages)));
    }

    /** Returns the bound volume only while the slot still contains the remembered text. */
    public static int boundVolumeAtSlot(int slot, List<String> pages) {
        BoundVolume bound = boundVolumes.get(slot);
        if (bound == null || !bound.pages().equals(pages)) {
            if (bound != null) {
                boundVolumes.remove(slot);
            }
            return -1;
        }
        return bound.volume();
    }

    public static void forgetAtSlot(int slot) {
        boundVolumes.remove(slot);
    }

    /** Called from an explicit editor context, so edits and duplicate text are safe. */
    public static void onBookSigned(int volumeOneBased) {
        if (volumeOneBased == 1 && volumes.size() == 1) {
            titleFresh = false;
        }
    }

    public static String title() {
        return title;
    }
}
