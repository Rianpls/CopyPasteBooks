package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.core.BookDraft;
import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.core.EditJournal;
import io.github.rianpls.copypastebooks.core.SmartPaste;
import io.github.rianpls.copypastebooks.gui.EraseConfirmScreen;
import io.github.rianpls.copypastebooks.gui.SettingsScreen;
import io.github.rianpls.copypastebooks.gui.UnsavedBookScreen;
import io.github.rianpls.copypastebooks.mc.BookEditBridge;
import io.github.rianpls.copypastebooks.mc.BookSnapshot;
import io.github.rianpls.copypastebooks.mc.BookSource;
import io.github.rianpls.copypastebooks.mc.CPB;
import io.github.rianpls.copypastebooks.mc.ClipboardIO;
import io.github.rianpls.copypastebooks.mc.CopyOutFlow;
import io.github.rianpls.copypastebooks.mc.Feedback;
import io.github.rianpls.copypastebooks.mc.InsertFlow;
import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.PageFit;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import io.github.rianpls.copypastebooks.mc.TextFieldEditBridge;
import io.github.rianpls.copypastebooks.mc.VolumeState;
import io.github.rianpls.copypastebooks.mc.VolumeTracker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements BookEditBridge {

    @Shadow @Final private List<String> pages;
    @Shadow @Final private InteractionHand hand;
    @Shadow private int currentPage;
    @Shadow private MultiLineEditBox page;

    @Shadow protected abstract void updatePageContent();
    @Shadow protected abstract void updateLocalCopy();
    @Shadow protected abstract void updateButtonVisibility();
    @Shadow protected abstract void saveChanges();

    @Unique private Button cpb$volumeApply;
    @Unique private Button cpb$volumeUp;
    @Unique private Button cpb$volumeDown;
    @Unique private Button cpb$volumeReset;
    /** Tracked identity of the book being edited; captured once, survives re-init. */
    @Unique private long cpb$trackedId = Long.MIN_VALUE;
    /** Undo journal (typing batches + pastes + bulk ops). Lazy: mixin field initializers never run. */
    @Unique private EditJournal<BookSnapshot> cpb$editJournal;
    /** Where/when the last journaled edit happened — a jump starts a new undo step. */
    @Unique private int cpb$lastEditPage = -1;
    @Unique private int cpb$lastEditCaret = -1;
    @Unique private long cpb$lastEditTimeMs;
    /** True while we apply a restore/paste ourselves, so the listener doesn't journal it. */
    @Unique private boolean cpb$applying;
    /** Imported-volume identity follows the draft through edits and undo/redo. */
    @Unique private int cpb$currentImportedVolume;
    @Unique private boolean cpb$volumeContextResolved;
    /** Canonical pages as they were when this editor was constructed (dirty check). */
    @Unique private List<String> cpb$originalPages;
    /** Volume marks made by this unsaved draft; discard must roll only these back. */
    @Unique private Set<Integer> cpb$uncommittedVolumeMarks;
    /** A fresh import drops an old colored number only when vanilla actually saves it. */
    @Unique private boolean cpb$dropTrackedOnSave;
    /** One-shot tag consumed by the next bulk page replacement. */
    @Unique private boolean cpb$nextBulkIsFreshImport;

    protected BookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void copypastebooks$captureOriginalDraft(CallbackInfo ci) {
        cpb$originalPages = BookDraft.canonicalPages(this.pages);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void copypastebooks$addButtons(CallbackInfo ci) {
        if (cpb$trackedId == Long.MIN_VALUE) {
            // Full resolve, not a plain slot lookup: the screen may open mid-resync while
            // the mark is briefly "lost" — recover it by content before giving up.
            cpb$trackedId = VolumeTracker.resolveForEdit(copypastebooks$handSlot());
        }
        if (!cpb$volumeContextResolved) {
            int remembered = VolumeState.boundVolumeAtSlot(copypastebooks$handSlot(), List.copyOf(this.pages));
            cpb$currentImportedVolume = Math.max(0, remembered);
            cpb$volumeContextResolved = true;
        }
        if (cpb$originalPages == null) {
            cpb$originalPages = BookDraft.canonicalPages(this.pages);
        }
        // Wrap vanilla's page-value listener so manual edits enter the undo journal.
        // updateLocalCopy runs only when the book is saved, not after each keystroke.
        this.page.setValueListener(this::copypastebooks$onPageEdited);
        int y = 220; // vanilla [Sign][Done] row sits at 196..216
        int x = this.width / 2 - 100;

        this.addRenderableWidget(Button.builder(Lang.tr("btn.from_file"),
                        b -> InsertFlow.intoEditor(this, true))
                .bounds(x, y, 62, 20)
                .tooltip(Lang.tip("btn.from_file.tip"))
                .build());
        this.addRenderableWidget(Button.builder(Lang.tr("btn.from_clipboard"),
                        b -> InsertFlow.intoEditor(this, false))
                .bounds(x + 65, y, 62, 20)
                .tooltip(Lang.tip("btn.from_clipboard.tip"))
                .build());
        this.addRenderableWidget(Button.builder(Lang.tr("btn.copy_out"),
                        b -> CopyOutFlow.copyWithDefaults(copypastebooks$source(), (Screen) (Object) this))
                .bounds(x + 130, y, 47, 20)
                .tooltip(Lang.tip("btn.copy_out.tip", Lang.trs(copypastebooks$destKey())))
                .build());
        this.addRenderableWidget(Button.builder(Lang.tr("btn.gear"),
                        b -> ScreenNav.open(new SettingsScreen((Screen) (Object) this, copypastebooks$source())))
                .bounds(x + 180, y, 20, 20)
                .tooltip(Lang.tip("btn.gear.tip"))
                .build());

        SpriteIconButton trash = SpriteIconButton.builder(Lang.tr("btn.erase.tip"),
                        b -> copypastebooks$requestErase(), true)
                .size(20, 20)
                .sprite(Identifier.fromNamespaceAndPath("copypastebooks", "widget/trash"), 16, 16)
                .build();
        // Builder.tooltip(...) only exists on 26.2; setTooltip works on both versions.
        trash.setTooltip(Lang.tip("btn.erase.tip"));
        // Bottom-right corner of the screen, 4px margin.
        trash.setX(this.width - 24);
        trash.setY(this.height - 24);
        this.addRenderableWidget(trash);

        // Volume selector: a column right of the book. The threshold is low (48px) so this
        // works on every legal GUI width (>=320) and never falls back to a bottom row that
        // could overlap the button row at small window heights.
        int bookLeft = (this.width - 192) / 2;
        int columnX = bookLeft + 196;
        int available = this.width - columnX;
        if (available >= 48) {
            int applyWidth = Math.min(90, available - 4);
            cpb$volumeUp = Button.builder(Component.literal("^"), b -> copypastebooks$selectVolume(1))
                    .bounds(columnX, 96, 20, 20).tooltip(Lang.tip("volume.up.tip")).build();
            cpb$volumeApply = Button.builder(Component.empty(), b -> copypastebooks$applyVolume())
                    .bounds(columnX, 120, applyWidth, 20)
                    .tooltip(Lang.tip("volume.apply.tip")).build();
            cpb$volumeDown = Button.builder(Component.literal("v"), b -> copypastebooks$selectVolume(-1))
                    .bounds(columnX, 144, 20, 20).tooltip(Lang.tip("volume.down.tip")).build();
            boolean resetFitsRight = available - applyWidth - 4 >= 24;
            cpb$volumeReset = Button.builder(Component.literal("X"), b -> copypastebooks$resetVolumes())
                    .bounds(resetFitsRight ? columnX + applyWidth + 4 : columnX,
                            resetFitsRight ? 120 : 168, 20, 20)
                    .tooltip(Lang.tip("volume.reset.tip")).build();
        } else {
            int rowY = Math.min(244, this.height - 22);
            cpb$volumeDown = Button.builder(Component.literal("v"), b -> copypastebooks$selectVolume(-1))
                    .bounds(this.width / 2 - 64, rowY, 20, 20).tooltip(Lang.tip("volume.down.tip")).build();
            cpb$volumeApply = Button.builder(Component.empty(), b -> copypastebooks$applyVolume())
                    .bounds(this.width / 2 - 40, rowY, 80, 20).tooltip(Lang.tip("volume.apply.tip")).build();
            cpb$volumeUp = Button.builder(Component.literal("^"), b -> copypastebooks$selectVolume(1))
                    .bounds(this.width / 2 + 44, rowY, 20, 20).tooltip(Lang.tip("volume.up.tip")).build();
            cpb$volumeReset = Button.builder(Component.literal("X"), b -> copypastebooks$resetVolumes())
                    .bounds(this.width / 2 + 68, rowY, 20, 20).tooltip(Lang.tip("volume.reset.tip")).build();
        }
        this.addRenderableWidget(cpb$volumeUp);
        this.addRenderableWidget(cpb$volumeApply);
        this.addRenderableWidget(cpb$volumeDown);
        this.addRenderableWidget(cpb$volumeReset);
        copypastebooks$refreshVolumeWidgets();
    }

    @Unique
    private String copypastebooks$destKey() {
        return CPB.config().destination == Config.Destination.CLIPBOARD
                ? "dest.value.clipboard" : "dest.value.file";
    }

    @Unique
    private BookSource copypastebooks$source() {
        return BookSource.fromRawPages(List.copyOf(this.pages), "");
    }

    @Unique
    private int copypastebooks$handSlot() {
        return this.hand == InteractionHand.MAIN_HAND
                ? this.minecraft.player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
    }

    @Unique
    private void copypastebooks$requestErase() {
        switch (CPB.config().eraseBehavior) {
            case IMMEDIATE -> copypastebooks$eraseBook();
            case ASK -> ScreenNav.open(new EraseConfirmScreen(
                    (Screen) (Object) this, this::copypastebooks$eraseBook, false));
            case ASK_DELAY -> ScreenNav.open(new EraseConfirmScreen(
                    (Screen) (Object) this, this::copypastebooks$eraseBook, true));
        }
    }

    @Unique
    private void copypastebooks$eraseBook() {
        // Editor-only, like every other edit: nothing is sent and no mark dies until
        // Done saves it; Ctrl+Z brings the text back. Escape follows the configured
        // close behavior (ask/save/discard) instead of silently deciding here.
        copypastebooks$setPages(List.of(""));
    }

    /**
     * Smart paste: Ctrl+V lands at the caret and continues onto following pages instead
     * of being cut at the page limit (existing text shifts, nothing is erased);
     * Ctrl+Z / Ctrl+Shift+Z undo/redo all editor operations. When smart paste is off,
     * only Ctrl+V goes back to vanilla; undo remains available for typing/bulk actions.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void copypastebooks$smartPasteKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.isPaste() && CPB.config().smartPaste) {
            copypastebooks$smartPaste();
            cir.setReturnValue(true);
        } else if (event.key() == GLFW.GLFW_KEY_Z
                // The "quirk" variant is what vanilla's own isPaste() uses: Cmd on macOS.
                && event.hasControlDownWithQuirk() && !event.hasAltDown()) {
            copypastebooks$undoRedo(event.hasShiftDown());
            cir.setReturnValue(true);
        }
    }

    /** The vanilla value listener plus journaling: every manual change lands here first. */
    @Unique
    private void copypastebooks$onPageEdited(String value) {
        int index = Math.clamp(this.currentPage, 0, this.pages.size() - 1);
        String old = this.pages.get(index);
        if (!cpb$applying && !value.equals(old)) {
            // Page flips re-set the same text (value == old) and are ignored above. A new
            // undo step starts when the edit is "elsewhere": another page, after a pause,
            // or the caret jumped (clicked into a different paragraph) — so one Ctrl+Z
            // never swallows two unrelated typing runs.
            long now = System.currentTimeMillis();
            MultilineTextField field = copypastebooks$textField();
            int caretAfter = field.cursor();
            int caretBefore = caretAfter;
            int anchorBefore = copypastebooks$selectionAnchor();
            TextFieldEditBridge edit = (TextFieldEditBridge) field;
            if (edit.copypastebooks$hasPreEditSelection()) {
                caretBefore = edit.copypastebooks$preEditCaret();
                anchorBefore = edit.copypastebooks$preEditAnchor();
            }
            caretBefore = Math.clamp(caretBefore, 0, old.length());
            anchorBefore = Math.clamp(anchorBefore, 0, old.length());
            // Replacing a selection is a distinct editor operation. In particular,
            // Ctrl+A + Delete must not merge into a preceding typing batch.
            boolean replacedSelection = caretBefore != anchorBefore;
            boolean newStep = cpb$lastEditPage != this.currentPage
                    || now - cpb$lastEditTimeMs > 3000
                    || Math.abs(caretBefore - cpb$lastEditCaret) > 32
                    || replacedSelection;
            copypastebooks$journal().noteChange(new BookSnapshot(List.copyOf(this.pages), this.currentPage,
                    caretBefore, anchorBefore, cpb$currentImportedVolume, 0, false,
                    cpb$dropTrackedOnSave), newStep);
            cpb$lastEditPage = this.currentPage;
            cpb$lastEditCaret = caretAfter;
            cpb$lastEditTimeMs = now;
        }
        this.pages.set(index, value); // vanilla listener behavior
    }

    @Unique
    private void copypastebooks$smartPaste() {
        String clip = ClipboardIO.get();
        if (clip.isEmpty()) {
            return; // key still eaten: vanilla would paste nothing either
        }
        MultilineTextField field = ((MultiLineEditBoxAccessor) (Object) this.page).copypastebooks$textField();
        // Selection bounds (equal when nothing is selected); SmartPaste sorts them itself.
        int selA = field.cursor();
        int selB = ((MultilineTextFieldAccessor) field).copypastebooks$selectCursor();
        List<String> current = new ArrayList<>(this.pages);
        if (!current.isEmpty()) {
            // The box's live value is the source of truth for the page being edited.
            current.set(Math.clamp(this.currentPage, 0, current.size() - 1), field.value());
        }
        SmartPaste.Result result = SmartPaste.paste(current, this.currentPage,
                selA, selB, PageFit.normalize(clip),
                PageFit::splitLossless, 100 /* vanilla writable-book page cap */);
        if (result.rejected()) {
            // Applying it would erase existing text at the 100-page cap — refuse whole.
            Feedback.error("err.paste.toobig");
            return;
        }
        // A paste is always its own undo step, and the caret returns to where it happened.
        copypastebooks$journal().noteChange(
                new BookSnapshot(List.copyOf(current), this.currentPage, selA, selB,
                        cpb$currentImportedVolume, 0, false, cpb$dropTrackedOnSave), true);
        copypastebooks$applyState(result.pages(), result.focusPage(), result.caret(), result.caret());
        copypastebooks$journal().boundary(); // typing after the paste is a separate step
        cpb$lastEditPage = this.currentPage;
        cpb$lastEditCaret = result.caret();
        cpb$lastEditTimeMs = System.currentTimeMillis();
        if (result.truncated()) {
            Feedback.warn("warn.paste.trimmed");
        }
    }

    @Unique
    private void copypastebooks$undoRedo(boolean redo) {
        EditJournal<BookSnapshot> journal = copypastebooks$journal();
        List<String> nowPages = List.copyOf(this.pages);
        int nowPage = this.currentPage;
        int nowCaret = copypastebooks$caret();
        int nowAnchor = copypastebooks$selectionAnchor();
        // The step tag (volume load) rides over to the opposite stack so redo/undo of
        // the same step keeps toggling the volume bookkeeping.
        java.util.function.UnaryOperator<BookSnapshot> current =
                restored -> new BookSnapshot(nowPages, nowPage, nowCaret, nowAnchor, cpb$currentImportedVolume,
                        restored.loadedVolume(), restored.volumeWasInserted(), cpb$dropTrackedOnSave);
        BookSnapshot restored = redo ? journal.redo(current) : journal.undo(current);
        if (restored != null) {
            copypastebooks$applyState(restored.pages(), restored.page(),
                    restored.caret(), restored.anchor());
            cpb$currentImportedVolume = restored.importedVolume();
            cpb$dropTrackedOnSave = restored.dropTrackedOnSave();
            cpb$lastEditPage = this.currentPage;
            int volume = restored.loadedVolume();
            if (volume > 0) {
                // Undoing a volume load un-counts it and brings a hidden selector back;
                // redoing counts it again (and may hide the selector again).
                if (redo) {
                    VolumeState.markInserted(volume);
                    if (!restored.volumeWasInserted()) {
                        copypastebooks$uncommittedVolumeMarks().add(volume);
                    }
                } else if (!restored.volumeWasInserted()) {
                    VolumeState.markUninserted(volume);
                    copypastebooks$uncommittedVolumeMarks().remove(volume);
                }
                copypastebooks$refreshVolumeWidgets();
            }
        }
    }

    @Unique
    private EditJournal<BookSnapshot> copypastebooks$journal() {
        if (cpb$editJournal == null) {
            cpb$editJournal = new EditJournal<>(64);
        }
        return cpb$editJournal;
    }

    @Unique
    private int copypastebooks$caret() {
        return copypastebooks$textField().cursor();
    }

    @Unique
    private int copypastebooks$selectionAnchor() {
        return ((MultilineTextFieldAccessor) copypastebooks$textField()).copypastebooks$selectCursor();
    }

    @Unique
    private MultilineTextField copypastebooks$textField() {
        return ((MultiLineEditBoxAccessor) (Object) this.page).copypastebooks$textField();
    }

    /**
     * Like setPages, but lands on the given page with the exact caret/selection state.
     * Does not touch the ItemStack: vanilla sends the
     * edit packet only on Done/Sign, and mutating the client item without a packet
     * leaves a ghost book if the screen is closed with Esc. The pages list is the only
     * state until vanilla's own saveChanges persists it.
     */
    @Unique
    private void copypastebooks$applyState(List<String> newPages, int focusPage,
                                            int caret, int anchor) {
        cpb$applying = true;
        try {
            this.pages.clear();
            this.pages.addAll(newPages.isEmpty() ? List.of("") : newPages);
            this.currentPage = Math.clamp(focusPage, 0, this.pages.size() - 1);
            this.updatePageContent();
            this.updateButtonVisibility();
            if (caret >= 0) {
                MultilineTextField field = copypastebooks$textField();
                int length = this.pages.get(this.currentPage).length();
                int safeCaret = Math.clamp(caret, 0, length);
                int safeAnchor = Math.clamp(anchor, 0, length);
                // Establish the anchor with selection off, then move only the caret while
                // selection is on. Finish with selecting=false so the next arrow/click has
                // normal vanilla behavior while the restored highlight remains visible.
                field.setSelecting(false);
                field.seekCursor(Whence.ABSOLUTE, safeAnchor);
                if (safeCaret != safeAnchor) {
                    field.setSelecting(true);
                    field.seekCursor(Whence.ABSOLUTE, safeCaret);
                    field.setSelecting(false);
                }
            }
        } finally {
            cpb$applying = false;
        }
    }

    /**
     * Runs when vanilla persists the book (Done/Sign → saveChanges): normal editing
     * keeps a tracked number with the new content, while saving an empty book or a fresh
     * import removes the old identity. Nothing changes at draft time, so undo/discard
     * still preserve the original number.
     */
    @Inject(method = "updateLocalCopy", at = @At("TAIL"))
    private void copypastebooks$syncTrackedContent(CallbackInfo ci) {
        if (cpb$trackedId <= 0) {
            // Init may have run before the tracker re-claimed the book — keep retrying
            // until the id is known (harmless when the book simply isn't tracked).
            cpb$trackedId = VolumeTracker.idAtSlot(copypastebooks$handSlot());
        }
        if (cpb$trackedId > 0) {
            if (cpb$dropTrackedOnSave || this.pages.stream().allMatch(String::isBlank)) {
                VolumeTracker.removeId(cpb$trackedId);
                cpb$trackedId = -1;
            } else {
                VolumeTracker.updateContent(cpb$trackedId, List.copyOf(this.pages));
            }
        }
        if (cpb$currentImportedVolume > 0) {
            if (this.pages.isEmpty() || this.pages.stream().allMatch(String::isBlank)) {
                VolumeState.forgetAtSlot(copypastebooks$handSlot());
                cpb$currentImportedVolume = 0;
            } else {
                VolumeState.rememberAtSlot(copypastebooks$handSlot(), cpb$currentImportedVolume,
                        List.copyOf(this.pages));
            }
        }
        // updateLocalCopy is vanilla's commit point (Done/Sign/our save-on-close).
        // Everything that was draft-only is now real and must no longer roll back.
        cpb$dropTrackedOnSave = false;
        copypastebooks$uncommittedVolumeMarks().clear();
    }

    @Override
    public void copypastebooks$onFreshImport() {
        // Do not mutate the real/local book identity yet: the import is only a draft
        // until Done/Sign (and Escape may explicitly discard it).
        cpb$nextBulkIsFreshImport = true;
        cpb$volumeContextResolved = true;
    }

    @Unique
    private void copypastebooks$selectVolume(int delta) {
        VolumeState.select(VolumeState.selected() + delta);
        copypastebooks$refreshVolumeWidgets();
    }

    @Unique
    private void copypastebooks$resetVolumes() {
        VolumeState.clear();
        cpb$currentImportedVolume = 0;
        cpb$volumeContextResolved = true;
        copypastebooks$refreshVolumeWidgets();
    }

    @Unique
    private void copypastebooks$applyVolume() {
        int volume = VolumeState.selected();
        copypastebooks$setPagesFromVolume(VolumeState.volume(volume), volume);
        Feedback.info("msg.volume.loaded", volume, VolumeState.count());
        copypastebooks$refreshVolumeWidgets();
    }

    @Override
    public void copypastebooks$setPages(List<String> newPages) {
        copypastebooks$setPagesInternal(newPages, 0, false, cpb$currentImportedVolume);
    }

    @Override
    public void copypastebooks$setPagesFromVolume(List<String> newPages, int volume) {
        boolean alreadyInserted = VolumeState.isInserted(volume);
        copypastebooks$setPagesInternal(newPages, volume, alreadyInserted, volume);
        VolumeState.markInserted(volume);
        if (!alreadyInserted) {
            copypastebooks$uncommittedVolumeMarks().add(volume);
        }
        copypastebooks$refreshVolumeWidgets();
    }

    @Unique
    private void copypastebooks$setPagesInternal(List<String> newPages, int loadedVolume,
                                                  boolean volumeWasInserted, int newImportedVolume) {
        // Bulk operations (erase, volume load, import into the editor) are their own
        // undo steps too — Ctrl+Z brings the previous text back. A volume load tags its
        // step so undoing it also un-counts the volume.
        copypastebooks$journal().noteChange(
                new BookSnapshot(List.copyOf(this.pages), this.currentPage,
                        copypastebooks$caret(), copypastebooks$selectionAnchor(),
                        cpb$currentImportedVolume, loadedVolume, volumeWasInserted,
                        cpb$dropTrackedOnSave), true);
        boolean freshImport = cpb$nextBulkIsFreshImport;
        cpb$nextBulkIsFreshImport = false;
        this.pages.clear();
        this.pages.addAll(newPages.isEmpty() ? List.of("") : newPages);
        this.currentPage = 0;
        cpb$currentImportedVolume = newImportedVolume;
        if (freshImport) {
            cpb$dropTrackedOnSave = true;
        }
        cpb$applying = true;
        try {
            // No updateLocalCopy here either: this remains a pages-only draft until
            // vanilla's saveChanges persists the item and sends its edit packet.
            this.updatePageContent();
            this.updateButtonVisibility();
        } finally {
            cpb$applying = false;
        }
        copypastebooks$journal().boundary();
    }

    @Override
    public int copypastebooks$currentImportedVolume() {
        return cpb$currentImportedVolume;
    }

    /**
     * Vanilla inherits Screen.onClose(), which simply discards the draft. Intercept only
     * that user-close path (Escape/inventory key): Done still runs vanilla saveChanges,
     * and Sign/settings switch screens directly without calling this method.
     */
    @Override
    public void onClose() {
        if (!copypastebooks$isDirty()) {
            copypastebooks$rollbackDraftSideEffects();
            super.onClose();
            return;
        }
        switch (CPB.config().closeBehavior) {
            case DISCARD -> {
                copypastebooks$rollbackDraftSideEffects();
                super.onClose();
            }
            case SAVE -> {
                copypastebooks$saveIfConnected();
                super.onClose();
            }
            case ASK -> ScreenNav.open(new UnsavedBookScreen((Screen) (Object) this,
                    this::copypastebooks$saveFromPrompt,
                    this::copypastebooks$discardFromPrompt));
        }
    }

    @Unique
    private boolean copypastebooks$isDirty() {
        return cpb$originalPages != null && BookDraft.changed(cpb$originalPages, this.pages);
    }

    @Unique
    private void copypastebooks$saveIfConnected() {
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.getConnection() != null) {
            this.saveChanges();
            cpb$originalPages = BookDraft.canonicalPages(this.pages);
        }
    }

    @Unique
    private void copypastebooks$saveFromPrompt() {
        copypastebooks$saveIfConnected();
        ScreenNav.open(null);
    }

    @Unique
    private void copypastebooks$discardFromPrompt() {
        copypastebooks$rollbackDraftSideEffects();
        ScreenNav.open(null);
    }

    @Unique
    private Set<Integer> copypastebooks$uncommittedVolumeMarks() {
        if (cpb$uncommittedVolumeMarks == null) {
            cpb$uncommittedVolumeMarks = new HashSet<>();
        }
        return cpb$uncommittedVolumeMarks;
    }

    /** Restores client-only state when vanilla discards the unsaved page draft. */
    @Unique
    private void copypastebooks$rollbackDraftSideEffects() {
        for (int volume : Set.copyOf(copypastebooks$uncommittedVolumeMarks())) {
            VolumeState.markUninserted(volume);
        }
        cpb$uncommittedVolumeMarks.clear();
        cpb$dropTrackedOnSave = false;
        cpb$nextBulkIsFreshImport = false;
    }

    @Override
    public List<String> copypastebooks$getPages() {
        return List.copyOf(this.pages);
    }

    @Override
    public void copypastebooks$refreshVolumeWidgets() {
        boolean show = VolumeState.hasMultiple();
        if (cpb$volumeApply == null) {
            return;
        }
        cpb$volumeApply.visible = show;
        cpb$volumeUp.visible = show;
        cpb$volumeDown.visible = show;
        cpb$volumeReset.visible = show;
        if (show) {
            cpb$volumeApply.setMessage(Lang.tr("volume.apply", VolumeState.selected(), VolumeState.count()));
            cpb$volumeUp.active = VolumeState.selected() < VolumeState.count();
            cpb$volumeDown.active = VolumeState.selected() > 1;
        }
    }
}
