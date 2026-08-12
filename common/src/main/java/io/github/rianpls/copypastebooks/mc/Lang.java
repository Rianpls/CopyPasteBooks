package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.core.I18n;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The mod's translator: config override or (in AUTO mode) the game language. */
public final class Lang {

    private Lang() {
    }

    public static String code() {
        Config.LangMode mode = CPB.config().language;
        return switch (mode) {
            case EN -> "en";
            case RU -> "ru";
            case AUTO -> gameIsRussian() ? "ru" : "en";
        };
    }

    private static boolean gameIsRussian() {
        try {
            String selected = Minecraft.getInstance().getLanguageManager().getSelected();
            return selected != null && selected.toLowerCase(Locale.ROOT).startsWith("ru");
        } catch (Exception e) {
            return false;
        }
    }

    public static String trs(String key, Object... args) {
        return I18n.tr(code(), key, args);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.literal(trs(key, args));
    }

    public static Tooltip tip(String key, Object... args) {
        return Tooltip.create(tr(key, args));
    }
}
