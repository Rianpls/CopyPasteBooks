package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks for a base title to auto-name multivolume books. Empty = no name (highlight instead). */
public class VolumeNameScreen extends Screen {
    private final Screen parent;
    private final String prefill;
    private final int maxBase;
    private final Consumer<String> onConfirm;
    private EditBox nameBox;

    public VolumeNameScreen(Screen parent, String prefill, int maxBase, Consumer<String> onConfirm) {
        super(Lang.tr("prompt.volume.name"));
        this.parent = parent;
        this.maxBase = Math.max(1, maxBase);
        String base = prefill == null ? "" : prefill;
        this.prefill = base.length() > this.maxBase ? base.substring(0, this.maxBase) : base;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(40, this.height / 2 - 52);

        this.addRenderableWidget(Widgets.centeredLine(this.font, this.getTitle(), centerX, y));
        y += 16;

        nameBox = new EditBox(this.font, centerX - 100, y, 200, 20, this.getTitle());
        nameBox.setMaxLength(maxBase);
        nameBox.setValue(prefill);
        nameBox.setHint(Lang.tr("prompt.volume.name.hint").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(nameBox);
        y += 26;

        for (String line : Widgets.wrap(this.font, Lang.trs("prompt.volume.name.max", maxBase), 280)) {
            this.addRenderableWidget(Widgets.centeredLine(this.font,
                    Component.literal(line).withStyle(ChatFormatting.DARK_GRAY), centerX, y));
            y += 10;
        }
        y += 8;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm())
                .bounds(centerX - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(centerX + 2, y, 98, 20).build());
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(nameBox);
    }

    private void confirm() {
        String value = nameBox.getValue().trim();
        ScreenNav.open(parent);
        onConfirm.accept(value);
    }

    private void cancel() {
        ScreenNav.open(parent);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }
}
