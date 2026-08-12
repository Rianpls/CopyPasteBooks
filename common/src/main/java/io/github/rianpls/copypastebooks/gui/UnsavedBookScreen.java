package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Three-way guard shown when an edited book is closed without pressing Done. */
public final class UnsavedBookScreen extends Screen {
    private final Screen parent;
    private final Runnable onSave;
    private final Runnable onDiscard;

    public UnsavedBookScreen(Screen parent, Runnable onSave, Runnable onDiscard) {
        super(Lang.tr("close.title"));
        this.parent = parent;
        this.onSave = onSave;
        this.onDiscard = onDiscard;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int textWidth = Math.max(1, Math.min(300, this.width - 20));
        List<String> bodyLines = Widgets.wrap(this.font, Lang.trs("close.body"), textWidth);

        // Keep the full prompt usable at large GUI scales as well. The normal layout
        // is comfortably spaced; short windows only tighten the vertical gaps.
        boolean compact = 130 + 11 * bodyLines.size() > this.height - 12;
        int titleToBody = compact ? 14 : 22;
        int lineStep = compact ? 10 : 11;
        int bodyToButtons = compact ? 5 : 12;
        int buttonStep = compact ? 21 : 24;
        int noteGap = compact ? 4 : 8;
        int noteStep = compact ? 10 : 11;
        int totalHeight = titleToBody + bodyLines.size() * lineStep + bodyToButtons
                + 2 * buttonStep + 20 + noteGap + noteStep + 9;
        int y = Math.max(4, (this.height - totalHeight) / 2);

        this.addRenderableWidget(Widgets.centeredLine(this.font,
                this.getTitle().copy().withStyle(ChatFormatting.GOLD), centerX, y));
        y += titleToBody;
        for (String line : bodyLines) {
            this.addRenderableWidget(Widgets.centeredLine(this.font,
                    Component.literal(line), centerX, y));
            y += lineStep;
        }
        y += bodyToButtons;
        this.addRenderableWidget(Button.builder(Lang.tr("close.save"), b -> onSave.run())
                .bounds(centerX - 100, y, 200, 20).build());
        y += buttonStep;
        this.addRenderableWidget(Button.builder(Lang.tr("close.discard"), b -> onDiscard.run())
                .bounds(centerX - 100, y, 200, 20).build());
        y += buttonStep;
        this.addRenderableWidget(Button.builder(Lang.tr("close.continue"), b -> ScreenNav.open(parent))
                .bounds(centerX - 100, y, 200, 20).build());

        y += 20 + noteGap;
        this.addRenderableWidget(Widgets.centeredLine(this.font,
                Lang.tr("close.settings.question").copy().withStyle(ChatFormatting.GRAY), centerX, y));
        y += noteStep;
        Component answer = Lang.tr("close.settings.answer").copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" "))
                .append(Lang.tr("btn.gear").copy().withStyle(ChatFormatting.GOLD));
        // Deliberately a label, not a button: the gear only points out which setting to find.
        this.addRenderableWidget(Widgets.centeredLine(this.font, answer, centerX, y));
    }

    /** Escape is the safest choice here: return to the draft and change nothing. */
    @Override
    public void onClose() {
        ScreenNav.open(parent);
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }
}
