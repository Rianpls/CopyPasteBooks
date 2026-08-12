package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small "copy with § codes or plain?" question, shown only when formatting is applied. */
public class FormattingAskScreen extends Screen {
    private final Screen parent;
    private final Consumer<Boolean> onChoice;

    public FormattingAskScreen(Screen parent, Consumer<Boolean> onChoice) {
        super(Lang.tr("ask.title"));
        this.parent = parent;
        this.onChoice = onChoice;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(30, this.height / 2 - 50);

        this.addRenderableWidget(Widgets.centeredLine(this.font, this.getTitle(), centerX, y));
        y += 18;

        for (String line : Widgets.wrap(this.font, Lang.trs("ask.body"), 280)) {
            this.addRenderableWidget(Widgets.centeredLine(this.font, Component.literal(line), centerX, y));
            y += 11;
        }
        y += 12;

        int buttonWidth = 118;
        Button withCodes = Button.builder(Lang.tr("ask.with"), b -> close(Boolean.TRUE))
                .bounds(centerX - buttonWidth - 4, y, buttonWidth, 20)
                .tooltip(Lang.tip("ask.with.tip"))
                .build();
        Button plain = Button.builder(Lang.tr("ask.plain"), b -> close(Boolean.FALSE))
                .bounds(centerX + 4, y, buttonWidth, 20)
                .tooltip(Lang.tip("ask.plain.tip"))
                .build();
        this.addRenderableWidget(withCodes);
        this.addRenderableWidget(plain);
        y += 24;
        this.addRenderableWidget(Button.builder(Lang.tr("ask.cancel"), b -> close(null))
                .bounds(centerX - 60, y, 120, 20)
                .build());
    }

    private void close(Boolean choice) {
        ScreenNav.open(parent);
        if (choice != null) {
            onChoice.accept(choice);
        }
    }

    @Override
    public void onClose() {
        close(null);
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }
}
