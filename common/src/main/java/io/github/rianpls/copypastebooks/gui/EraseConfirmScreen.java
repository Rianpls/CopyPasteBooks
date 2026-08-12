package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirms erasing a book's text. It always requires ticking a checkbox; depending on
 * the configured safety level, the erase button either unlocks immediately or after
 * a three-second countdown.
 */
public class EraseConfirmScreen extends Screen {
    private static final long DELAY_MS = 3000L;

    private final Screen parent;
    private final Runnable onConfirm;
    private final boolean delayed;
    private Checkbox understand;
    private Button sure;
    private long armedAt = -1L;

    public EraseConfirmScreen(Screen parent, Runnable onConfirm, boolean delayed) {
        super(Lang.tr("erase.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.delayed = delayed;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = Math.max(40, this.height / 2 - 54);

        this.addRenderableWidget(Widgets.centeredLine(this.font,
                this.getTitle().copy().withStyle(ChatFormatting.RED), centerX, y));
        y += 20;
        for (String line : Widgets.wrap(this.font, Lang.trs("erase.body"), 300)) {
            this.addRenderableWidget(Widgets.centeredLine(this.font, Component.literal(line), centerX, y));
            y += 11;
        }
        y += 8;

        understand = Checkbox.builder(Lang.tr("erase.check"), this.font)
                .pos(0, y)
                .selected(false)
                .onValueChange((box, value) -> {
                    armedAt = value ? System.currentTimeMillis() : -1L;
                    refreshSure();
                })
                .build();
        // Center the checkbox (box + label) as a whole; its width is only known after build().
        understand.setX(centerX - understand.getWidth() / 2);
        this.addRenderableWidget(understand);
        y += 28;

        this.addRenderableWidget(Button.builder(Lang.tr("confirm.no"), b -> ScreenNav.open(parent))
                .bounds(centerX - 100, y, 98, 20).build());
        sure = Button.builder(Lang.tr("erase.sure"), b -> {
            ScreenNav.open(parent);
            onConfirm.run();
        }).bounds(centerX + 2, y, 98, 20).build();
        sure.active = false;
        this.addRenderableWidget(sure);
        refreshSure();
    }

    @Override
    public void tick() {
        refreshSure();
    }

    private void refreshSure() {
        if (sure == null || understand == null) {
            return;
        }
        boolean ticked = understand.selected();
        long remaining = delayed && ticked && armedAt > 0
                ? DELAY_MS - (System.currentTimeMillis() - armedAt) : DELAY_MS;
        if (ticked && (!delayed || remaining <= 0)) {
            sure.active = true;
            sure.setMessage(Lang.tr("erase.sure"));
        } else {
            sure.active = false;
            sure.setMessage(ticked
                    ? Lang.tr("erase.sure.wait", (int) Math.ceil(remaining / 1000.0))
                    : Lang.tr("erase.sure"));
        }
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
