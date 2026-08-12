package io.github.rianpls.copypastebooks.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

/**
 * Small text-layout helpers. In 26.x StringWidget renders its message left-aligned
 * inside the widget box, so "centered" labels must be positioned by measured width.
 */
final class Widgets {

    private Widgets() {
    }

    /** A single-line label centered on {@code centerX} by its actual text width. */
    static StringWidget centeredLine(Font font, Component text, int centerX, int y) {
        int width = Math.max(1, font.width(text));
        return new StringWidget(centerX - width / 2, y, width, 9, text, font);
    }

    /** Word-wraps plain text to {@code maxWidth} pixels. */
    static List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}
