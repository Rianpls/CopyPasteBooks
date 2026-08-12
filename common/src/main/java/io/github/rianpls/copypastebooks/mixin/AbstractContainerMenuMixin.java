package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.mc.VolumeTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes local container mutations around {@code AbstractContainerMenu.clicked}.
 * Normal screens reach this method through the game mode; the creative inventory calls
 * it directly, so a higher-level hook would miss creative drags. The tracker compares
 * player slots and cursor state before and after the call because clicks may replace
 * ItemStack instances. Integrated-server calls are ignored by {@link VolumeTracker}.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"))
    private void copypastebooks$beforeClick(int slotId, int button, ContainerInput input, Player player,
                                            CallbackInfo ci) {
        VolumeTracker.beforeMenuClick((AbstractContainerMenu) (Object) this, slotId, button, input, player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void copypastebooks$afterClick(int slotId, int button, ContainerInput input, Player player,
                                           CallbackInfo ci) {
        VolumeTracker.afterMenuClick(player);
    }
}
