package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.mc.VolumeTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws a colored volume number on unsigned volume books, only in the player's own inventory. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void copypastebooks$markVolume(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                                           CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || slot.container != minecraft.player.getInventory()) {
            // Foreign container slots never draw a number (deposited books lose theirs).
            return;
        }
        // Creative inventory wrappers expose menu indices rather than player-inventory
        // indices, so rendering resolves the mark from the ItemStack reference.
        VolumeTracker.Mark mark = VolumeTracker.lookupByStack(slot.getItem());
        if (mark == null) {
            return;
        }
        String label = String.valueOf(mark.number());
        int x = slot.x + 17 - minecraft.font.width(label);
        int y = slot.y + 9;
        graphics.text(minecraft.font, Component.literal(label), x, y, mark.color(), true);
    }
}
