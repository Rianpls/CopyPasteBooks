package io.github.rianpls.copypastebooks.mc;

import java.util.List;

/** Implemented by BookEditScreenMixin: lets mod code drive the vanilla book editor. */
public interface BookEditBridge {

    /** Replaces the editor's draft pages, jumps to page 1 and refreshes the widgets. */
    void copypastebooks$setPages(List<String> pages);

    /** Loads an imported volume and journals both its text and selector bookkeeping. */
    void copypastebooks$setPagesFromVolume(List<String> pages, int volume);

    List<String> copypastebooks$getPages();

    /** Re-evaluates the volume selector widgets (visibility + label). */
    void copypastebooks$refreshVolumeWidgets();

    /** Marks the next page replacement as a fresh import; old identity dies on commit. */
    void copypastebooks$onFreshImport();

    /** 1-based imported volume currently being edited, or 0 for an unrelated book. */
    int copypastebooks$currentImportedVolume();
}
