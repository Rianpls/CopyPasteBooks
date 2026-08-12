package io.github.rianpls.copypastebooks.mixin;

import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.gui.SettingsScreen;
import io.github.rianpls.copypastebooks.mc.BookSource;
import io.github.rianpls.copypastebooks.mc.CPB;
import io.github.rianpls.copypastebooks.mc.CopyOutFlow;
import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin extends Screen {

    @Shadow private BookViewScreen.BookAccess bookAccess;

    protected BookViewScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void copypastebooks$addButtons(CallbackInfo ci) {
        int y = 220; // vanilla Done (200 wide) sits at 196..216
        int x = (this.width - 200) / 2;
        String destKey = CPB.config().destination == Config.Destination.CLIPBOARD
                ? "dest.value.clipboard" : "dest.value.file";
        this.addRenderableWidget(Button.builder(Lang.tr("btn.copy_out"),
                        b -> CopyOutFlow.copyWithDefaults(copypastebooks$source(), (Screen) (Object) this))
                .bounds(x, y, 176, 20)
                .tooltip(Lang.tip("btn.copy_out.tip", Lang.trs(destKey)))
                .build());
        this.addRenderableWidget(Button.builder(Lang.tr("btn.gear"),
                        b -> ScreenNav.open(new SettingsScreen((Screen) (Object) this, copypastebooks$source())))
                .bounds(x + 180, y, 20, 20)
                .tooltip(Lang.tip("btn.gear.tip"))
                .build());
    }

    @Unique
    private BookSource copypastebooks$source() {
        List<Component> pages = new ArrayList<>();
        for (int i = 0; i < this.bookAccess.getPageCount(); i++) {
            pages.add(this.bookAccess.getPage(i));
        }
        return BookSource.fromComponents(pages, copypastebooks$guessTitle());
    }

    /**
     * The view screen doesn't keep its ItemStack. Use a held title only when every
     * displayed page matches; page count alone can select the other hand's book, while
     * an arbitrary fallback also misnames lectern books.
     */
    @Unique
    private String copypastebooks$guessTitle() {
        LocalPlayer player = this.minecraft != null ? this.minecraft.player : null;
        if (player == null) {
            return "";
        }
        boolean filtered = this.minecraft.isTextFilteringEnabled();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            if (content == null || content.pages().size() != this.bookAccess.getPageCount()) {
                continue;
            }
            List<Component> heldPages = content.getPages(filtered);
            boolean same = true;
            for (int i = 0; i < heldPages.size(); i++) {
                if (!heldPages.get(i).equals(this.bookAccess.getPage(i))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return content.title().raw();
            }
        }
        return "";
    }
}
