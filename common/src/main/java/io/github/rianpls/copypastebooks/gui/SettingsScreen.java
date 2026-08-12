package io.github.rianpls.copypastebooks.gui;

import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.mc.BookSource;
import io.github.rianpls.copypastebooks.mc.CPB;
import io.github.rianpls.copypastebooks.mc.CopyOutFlow;
import io.github.rianpls.copypastebooks.mc.Feedback;
import io.github.rianpls.copypastebooks.mc.FileDialogs;
import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The ⚙ screen: a one-off "copy out now" section (page range, markers) when opened from
 * a book, plus the persistent defaults. Every control carries a tooltip.
 *
 * When the content doesn't fit the window height (e.g. GUI scale 4), the middle part
 * becomes scrollable: mouse wheel plus ^/v buttons on the right edge. Rows that would
 * stick out of the visible area are hidden entirely (no clipping needed). The screen
 * title and the Done button stay fixed.
 */
public class SettingsScreen extends Screen {
    private static final int COLUMN_WIDTH = 310;

    private final Screen parent;
    private final BookSource source;

    private EditBox rangeBox;
    private Checkbox markersBox;
    private EditBox folderBox;
    private EditBox byteLimitBox;
    private EditBox creativeDelayBox;
    private EditBox survivalDelayBox;
    private String keptRange = "";

    private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();
    private int scrollPx;
    private int maxScroll;
    private int scrollStep;
    private int contentTop;
    private int contentBottom;

    public SettingsScreen(Screen parent, BookSource source) {
        super(Lang.tr("settings.title"));
        this.parent = parent;
        this.source = source;
    }

    @Override
    protected void init() {
        scrollableWidgets.clear();
        Config config = CPB.config();
        boolean compact = this.height < 265;
        int step = compact ? 21 : 24;
        int headerStep = compact ? 13 : 16;
        scrollStep = step;
        int x0 = this.width / 2 - COLUMN_WIDTH / 2;
        int titleY = compact ? 8 : 14;

        // Fixed screen title.
        this.addRenderableWidget(Widgets.centeredLine(this.font, this.getTitle(), this.width / 2, titleY));

        contentTop = titleY + headerStep + 2;
        int nowBlock = source != null ? headerStep + 3 * step + 4 : headerStep + 2;
        int defaultRows = 12;
        int contentHeight = nowBlock + headerStep + defaultRows * step + 2;
        // +4 exactly matches contentBottom = doneY - 4, so maxScroll is 0 when it all fits.
        int doneY = Math.min(contentTop + contentHeight + 4, this.height - 24);
        contentBottom = doneY - 4;
        maxScroll = Math.max(0, contentHeight - (contentBottom - contentTop));
        scrollPx = Math.max(0, Math.min(scrollPx, maxScroll));

        int y = contentTop - scrollPx;

        if (source != null) {
            addSectionHeader(y, Lang.tr("settings.section.now"), ChatFormatting.GOLD);
            y += headerStep;

            Component rangeLabel = Lang.tr("settings.now.range.label");
            int labelWidth = this.font.width(rangeLabel) + 6;
            addScrollable(new StringWidget(x0, y + 5, labelWidth, 10, rangeLabel, this.font));
            rangeBox = new EditBox(this.font, x0 + labelWidth + 4, y, COLUMN_WIDTH - labelWidth - 4, 18,
                    Lang.tr("settings.now.range.label"));
            rangeBox.setHint(Lang.tr("settings.now.range.hint").withStyle(ChatFormatting.DARK_GRAY));
            rangeBox.setTooltip(Lang.tip("settings.now.range.tip"));
            rangeBox.setMaxLength(256);
            rangeBox.setValue(keptRange);
            addScrollable(rangeBox);
            y += step;

            markersBox = Checkbox.builder(Lang.tr("settings.now.markers.label"), this.font)
                    .pos(x0, y)
                    .selected(config.pageMarkers)
                    .tooltip(Lang.tip("settings.now.markers.tip"))
                    .build();
            addScrollable(markersBox);
            y += step;

            int half = (COLUMN_WIDTH - 6) / 2;
            addScrollable(Button.builder(Lang.tr("settings.now.tofile"),
                            b -> copyNow(Config.Destination.FILE))
                    .bounds(x0, y, half, 20)
                    .tooltip(Lang.tip("settings.now.tofile.tip"))
                    .build());
            addScrollable(Button.builder(Lang.tr("settings.now.toclip"),
                            b -> copyNow(Config.Destination.CLIPBOARD))
                    .bounds(x0 + half + 6, y, half, 20)
                    .tooltip(Lang.tip("settings.now.toclip.tip"))
                    .build());
            y += step + 4;
        } else {
            addSectionHeader(y, Lang.tr("settings.now.nobook").withStyle(ChatFormatting.DARK_GRAY), null);
            y += headerStep + 2;
        }

        addSectionHeader(y, Lang.tr("settings.section.defaults"), ChatFormatting.GOLD);
        y += headerStep;

        addScrollable(CycleButton.<Config.Destination>builder(
                        v -> Lang.tr(v == Config.Destination.CLIPBOARD ? "dest.clipboard" : "dest.file"),
                        config.destination)
                .withValues(Config.Destination.values())
                .withTooltip(v -> Lang.tip("settings.defaults.dest.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.dest.label"),
                        (button, value) -> config.destination = value));
        y += step;

        addScrollable(CycleButton.<Config.FormattingMode>builder(
                        v -> Lang.tr(switch (v) {
                            case ASK -> "fmt.ask";
                            case WITH_CODES -> "fmt.with";
                            case PLAIN -> "fmt.plain";
                        }),
                        config.formatting)
                .withValues(Config.FormattingMode.values())
                .withTooltip(v -> Lang.tip("settings.defaults.fmt.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.fmt.label"),
                        (button, value) -> config.formatting = value));
        y += step;

        addScrollable(CycleButton.<Config.MultivolumeMethod>builder(
                        v -> Lang.tr(v == Config.MultivolumeMethod.AUTO ? "method.auto" : "method.manual"),
                        config.multivolumeMethod)
                .withValues(Config.MultivolumeMethod.values())
                .withTooltip(v -> Lang.tip("settings.defaults.method.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.method.label"),
                        (button, value) -> config.multivolumeMethod = value));
        y += step;

        addScrollable(Checkbox.builder(Lang.tr("settings.defaults.autoname.label"), this.font)
                .pos(x0, y)
                .selected(config.autoNameVolumes)
                .onValueChange((box, value) -> config.autoNameVolumes = value)
                .tooltip(Lang.tip("settings.defaults.autoname.tip"))
                .build());
        y += step;

        addScrollable(Checkbox.builder(Lang.tr("settings.defaults.hidevol.label"), this.font)
                .pos(x0, y)
                .selected(config.autoHideVolumeSelector)
                .onValueChange((box, value) -> config.autoHideVolumeSelector = value)
                .tooltip(Lang.tip("settings.defaults.hidevol.tip"))
                .build());
        y += step;

        addScrollable(Checkbox.builder(Lang.tr("settings.defaults.smartpaste.label"), this.font)
                .pos(x0, y)
                .selected(config.smartPaste)
                .onValueChange((box, value) -> config.smartPaste = value)
                .tooltip(Lang.tip("settings.defaults.smartpaste.tip"))
                .build());
        y += step;

        addScrollable(CycleButton.<Config.CloseBehavior>builder(
                        v -> Lang.tr(switch (v) {
                            case ASK -> "close.mode.ask";
                            case SAVE -> "close.mode.save";
                            case DISCARD -> "close.mode.discard";
                        }).copy().withStyle(v == Config.CloseBehavior.SAVE
                                ? ChatFormatting.RED : ChatFormatting.WHITE),
                        config.closeBehavior)
                .withValues(Config.CloseBehavior.values())
                .withTooltip(v -> Lang.tip("settings.defaults.close.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.close.label"),
                        (button, value) -> config.closeBehavior = value));
        y += step;

        addScrollable(CycleButton.<Config.EraseBehavior>builder(
                        v -> Lang.tr(switch (v) {
                            case ASK_DELAY -> "erase.mode.delay";
                            case ASK -> "erase.mode.ask";
                            case IMMEDIATE -> "erase.mode.immediate";
                        }).copy().withStyle(v == Config.EraseBehavior.IMMEDIATE
                                ? ChatFormatting.RED : ChatFormatting.WHITE),
                        config.eraseBehavior)
                .withValues(Config.EraseBehavior.values())
                .withTooltip(v -> Lang.tip("settings.defaults.erase.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.erase.label"),
                        (button, value) -> config.eraseBehavior = value));
        y += step;

        Component folderLabel = Lang.tr("settings.defaults.folder.label");
        int folderLabelWidth = this.font.width(folderLabel) + 6;
        addScrollable(new StringWidget(x0, y + 5, folderLabelWidth, 10, folderLabel, this.font));
        folderBox = new EditBox(this.font, x0 + folderLabelWidth + 4, y,
                COLUMN_WIDTH - folderLabelWidth - 4 - 48, 18, folderLabel);
        folderBox.setMaxLength(1024);
        folderBox.setValue(config.saveFolder);
        folderBox.setHint(Lang.tr("settings.defaults.folder.hint").withStyle(ChatFormatting.DARK_GRAY));
        folderBox.setTooltip(Lang.tip("settings.defaults.folder.tip"));
        addScrollable(folderBox);
        addScrollable(Button.builder(Component.literal("..."), b -> browseFolder())
                .bounds(x0 + COLUMN_WIDTH - 44, y, 20, 18)
                .tooltip(Lang.tip("settings.defaults.folder.browse.tip"))
                .build());
        addScrollable(Button.builder(Component.literal("X"), b -> folderBox.setValue(""))
                .bounds(x0 + COLUMN_WIDTH - 20, y, 20, 18)
                .tooltip(Lang.tip("settings.defaults.folder.clear.tip"))
                .build());
        y += step;

        Component limitLabel = Lang.tr("settings.defaults.bytelimit.label");
        int limitLabelWidth = this.font.width(limitLabel) + 6;
        addScrollable(new StringWidget(x0, y + 5, limitLabelWidth, 10, limitLabel, this.font));
        byteLimitBox = new EditBox(this.font, x0 + limitLabelWidth + 4, y, 70, 18, limitLabel);
        byteLimitBox.setMaxLength(6);
        byteLimitBox.setValue(String.valueOf(config.pageByteLimit));
        byteLimitBox.setTooltip(Lang.tip("settings.defaults.bytelimit.tip"));
        addScrollable(byteLimitBox);
        y += step;

        // One row, two delay fields: creative | survival (milliseconds).
        Component creLabel = Lang.tr("settings.defaults.delay.creative.label");
        Component surLabel = Lang.tr("settings.defaults.delay.survival.label");
        int creLabelWidth = this.font.width(creLabel) + 6;
        int surLabelWidth = this.font.width(surLabel) + 6;
        int half = COLUMN_WIDTH / 2;
        addScrollable(new StringWidget(x0, y + 5, creLabelWidth, 10, creLabel, this.font));
        creativeDelayBox = new EditBox(this.font, x0 + creLabelWidth + 4, y,
                Math.max(40, half - creLabelWidth - 12), 18, creLabel);
        creativeDelayBox.setMaxLength(5);
        creativeDelayBox.setValue(String.valueOf(config.creativeDelayMs));
        creativeDelayBox.setTooltip(Lang.tip("settings.defaults.delay.creative.tip", Config.DEFAULT_CREATIVE_DELAY_MS));
        addScrollable(creativeDelayBox);
        addScrollable(new StringWidget(x0 + half + 4, y + 5, surLabelWidth, 10, surLabel, this.font));
        survivalDelayBox = new EditBox(this.font, x0 + half + 4 + surLabelWidth + 4, y,
                Math.max(40, half - surLabelWidth - 12), 18, surLabel);
        survivalDelayBox.setMaxLength(5);
        survivalDelayBox.setValue(String.valueOf(config.survivalDelayMs));
        survivalDelayBox.setTooltip(Lang.tip("settings.defaults.delay.survival.tip", Config.DEFAULT_SURVIVAL_DELAY_MS));
        addScrollable(survivalDelayBox);
        y += step;

        addScrollable(CycleButton.<Config.LangMode>builder(
                        v -> Lang.tr(switch (v) {
                            case AUTO -> "lang.auto";
                            case EN -> "lang.en";
                            case RU -> "lang.ru";
                        }),
                        config.language)
                .withValues(Config.LangMode.values())
                .withTooltip(v -> Lang.tip("settings.defaults.lang.tip"))
                .create(x0, y, COLUMN_WIDTH, 20, Lang.tr("settings.defaults.lang.label"),
                        (button, value) -> {
                            persistFields();
                            config.language = value;
                            CPB.saveConfig();
                            this.rebuildWidgets();
                        }));

        // Fixed Done button.
        this.addRenderableWidget(Button.builder(Lang.tr("settings.done"), b -> done())
                .bounds(this.width / 2 - 75, doneY, 150, 20)
                .build());

        // Scroll arrows on the right edge, only when scrolling is actually needed.
        if (maxScroll > 0) {
            Button up = Button.builder(Component.literal("^"), b -> scrollBy(-3 * scrollStep))
                    .bounds(this.width - 22, contentTop, 20, 20).build();
            up.active = scrollPx > 0;
            this.addRenderableWidget(up);
            Button down = Button.builder(Component.literal("v"), b -> scrollBy(3 * scrollStep))
                    .bounds(this.width - 22, contentBottom - 20, 20, 20).build();
            down.active = scrollPx < maxScroll;
            this.addRenderableWidget(down);
        }

        applyScrollVisibility();
    }

    private <T extends AbstractWidget> void addScrollable(T widget) {
        scrollableWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void addSectionHeader(int y, Component text, ChatFormatting color) {
        Component styled = color == null ? text : text.copy().withStyle(color);
        addScrollable(Widgets.centeredLine(this.font, styled, this.width / 2, y));
    }

    /** Hides rows that stick out of the scroll viewport (whole-row paging, no clipping). */
    private void applyScrollVisibility() {
        if (maxScroll <= 0) {
            return;
        }
        for (AbstractWidget widget : scrollableWidgets) {
            boolean inside = widget.getY() >= contentTop - 2
                    && widget.getY() + widget.getHeight() <= contentBottom + 2;
            widget.visible = inside;
        }
    }

    private void scrollBy(int delta) {
        int clamped = Math.max(0, Math.min(scrollPx + delta, maxScroll));
        if (clamped == scrollPx) {
            return;
        }
        persistFields();
        scrollPx = clamped;
        this.rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0 && scrollY != 0) {
            scrollBy((int) (-scrollY * scrollStep));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Opens the native folder picker; on failure the text field stays as the fallback. */
    private void browseFolder() {
        FileDialogs.selectFolder(Lang.trs("dialog.folder.title"), folderBox.getValue().trim(), result -> {
            if (result.failed()) {
                Feedback.error("err.folder.dialog");
                return;
            }
            if (result.path() == null) {
                return;
            }
            String path = result.path().toAbsolutePath().toString();
            // Persist immediately: while the dialog was open the screen may have been
            // re-initialized (resize/minimize) or closed entirely.
            CPB.config().saveFolder = path;
            CPB.saveConfig();
            if (ScreenNav.current() == this && folderBox != null) {
                folderBox.setValue(path);
            }
            Feedback.info("msg.folder.set", path);
        });
    }

    private void copyNow(Config.Destination destination) {
        persistFields();
        CPB.saveConfig();
        CopyOutFlow.copy(source, rangeBox.getValue(), markersBox.selected(), destination, this);
    }

    private void persistFields() {
        Config config = CPB.config();
        if (rangeBox != null) {
            keptRange = rangeBox.getValue();
        }
        if (markersBox != null) {
            config.pageMarkers = markersBox.selected();
        }
        if (folderBox != null) {
            config.saveFolder = folderBox.getValue().trim();
        }
        if (byteLimitBox != null) {
            try {
                config.pageByteLimit = Math.max(0, Integer.parseInt(byteLimitBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
                // keep the previous value on junk input
            }
        }
        if (creativeDelayBox != null) {
            try {
                config.creativeDelayMs = Config.clampDelay(Integer.parseInt(creativeDelayBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (survivalDelayBox != null) {
            try {
                config.survivalDelayMs = Config.clampDelay(Integer.parseInt(survivalDelayBox.getValue().trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void done() {
        persistFields();
        CPB.saveConfig();
        ScreenNav.open(parent);
    }

    @Override
    public void onClose() {
        done();
    }

    @Override
    public boolean isInGameUi() {
        return this.minecraft != null && this.minecraft.level != null;
    }
}
