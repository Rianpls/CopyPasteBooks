package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.mc.VolumeTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the volume number on hotbar/off-hand books on the in-game HUD.
 *
 * The hotbar slot renderer lives in {@code Gui} on 26.1.x and moved to the new {@code Hud}
 * class in 26.2 with an identical signature. String targets + {@code @Pseudo} +
 * {@code require = 0} make one mixin cover both: on each version exactly one of the two
 * targets has the method (the missing class / missing method is skipped silently).
 */
@Pseudo
@Mixin(targets = {"net.minecraft.client.gui.Gui", "net.minecraft.client.gui.Hud"})
public abstract class HotbarSlotMixin {

    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II"
                    + "Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("TAIL"),
            require = 0)
    private void copypastebooks$markHotbarVolume(GuiGraphicsExtractor graphics, int x, int y,
                                                 DeltaTracker deltaTracker, Player player, ItemStack itemStack,
                                                 int seed, CallbackInfo ci) {
        VolumeTracker.Mark mark = VolumeTracker.lookupByStack(itemStack);
        if (mark == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String label = String.valueOf(mark.number());
        graphics.text(font, Component.literal(label), x + 17 - font.width(label), y + 9, mark.color(), true);
    }
}
