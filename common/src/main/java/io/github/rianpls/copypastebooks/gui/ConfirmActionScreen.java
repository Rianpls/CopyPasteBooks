package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A small yes/no confirmation; runs {@code onConfirm} only on yes. */
public class ConfirmActionScreen extends Screen {
    private final Screen parent;
    private final Component body;
    private final Runnable onConfirm;

    public ConfirmActionScreen(Screen parent, Component title, Component body, Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.body = body;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(40, this.height / 2 - 50);

        this.addRenderableWidget(Widgets.centeredLine(this.font, this.getTitle(), centerX, y));
        y += 20;
        for (String line : Widgets.wrap(this.font, body.getString(), 300)) {
            this.addRenderableWidget(Widgets.centeredLine(this.font, Component.literal(line), centerX, y));
            y += 11;
        }
        y += 14;
        this.addRenderableWidget(Button.builder(Lang.tr("confirm.yes"), b -> {
            ScreenNav.open(parent);
            onConfirm.run();
        }).bounds(centerX - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(Lang.tr("confirm.no"), b -> ScreenNav.open(parent))
                .bounds(centerX + 2, y, 98, 20).build());
    }

    @Override
    public void onClose() {
        ScreenNav.open(parent);
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }
}
