package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.mc.BookEditBridge;
import io.github.rianpls.copypastebooks.mc.VolumeState;
import io.github.rianpls.copypastebooks.mc.VolumeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prefills the title box from the imported file's name (volume number appended for multi-volume texts). */
@Mixin(BookSignScreen.class)
public abstract class BookSignScreenMixin extends Screen {

    @Shadow private EditBox titleBox;
    @Shadow private String titleValue;
    @Shadow @Final private InteractionHand hand;
    @Shadow @Final private BookEditScreen bookEditScreen;

    protected BookSignScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void copypastebooks$prefillTitle(CallbackInfo ci) {
        if (!this.titleBox.getValue().isEmpty()) {
            return;
        }
        // The editor carries the explicit volume identity. Do not guess from page text:
        // edited volumes no longer match, identical volumes are ambiguous, and an
        // unrelated book can legitimately have the same text as an old import.
        int volume = ((BookEditBridge) this.bookEditScreen).copypastebooks$currentImportedVolume();
        if (volume < 1) {
            return;
        }
        String suggested = VolumeState.suggestedSignTitle(volume);
        if (!suggested.isBlank()) {
            this.titleBox.setValue(suggested);
            this.titleValue = suggested;
        }
    }

    @Inject(method = "saveChanges", at = @At("TAIL"))
    private void copypastebooks$onSigned(CallbackInfo ci) {
        int volume = ((BookEditBridge) this.bookEditScreen).copypastebooks$currentImportedVolume();
        VolumeState.onBookSigned(volume);
        // Signing turns the book into a WRITTEN_BOOK — its tracked identity ends here.
        int slot = this.hand == InteractionHand.MAIN_HAND
                ? Minecraft.getInstance().player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
        VolumeState.forgetAtSlot(slot);
        VolumeTracker.removeAtSlot(slot);
    }
}
