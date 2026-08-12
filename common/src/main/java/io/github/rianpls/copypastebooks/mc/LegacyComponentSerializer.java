package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.LegacyColors;
import io.github.rianpls.copypastebooks.core.LegacyText;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Component → legacy §-string. Colors map to the nearest of the 16 legacy colors
 * (hex colors from plugin-made books get rounded); click/hover events are dropped.
 */
public final class LegacyComponentSerializer {

    private LegacyComponentSerializer() {
    }

    public static String serialize(Component root) {
        StringBuilder out = new StringBuilder();
        LegacyText.StyleState[] current = {new LegacyText.StyleState()};
        root.visit((style, text) -> {
            if (!text.isEmpty()) {
                LegacyText.StyleState target = toState(style);
                out.append(LegacyText.transition(current[0], target));
                current[0] = target;
                out.append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    /** True when any visible text carries an actually-applied style (color/bold/...). */
    public static boolean hasAppliedStyles(Component root) {
        return root.visit((style, text) -> {
            if (!text.isBlank() && !toState(style).isPlain()) {
                return Optional.of(Boolean.TRUE);
            }
            return Optional.<Boolean>empty();
        }, Style.EMPTY).isPresent();
    }

    private static LegacyText.StyleState toState(Style style) {
        LegacyText.StyleState state = new LegacyText.StyleState();
        TextColor color = style.getColor();
        if (color != null) {
            state.color = LegacyColors.nearestCode(color.getValue() & 0xFFFFFF);
        }
        state.bold = style.isBold();
        state.italic = style.isItalic();
        state.underline = style.isUnderlined();
        state.strikethrough = style.isStrikethrough();
        state.obfuscated = style.isObfuscated();
        return state;
    }
}
