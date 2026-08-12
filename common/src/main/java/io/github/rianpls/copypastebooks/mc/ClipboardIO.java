package io.github.rianpls.copypastebooks.mc;

import net.minecraft.client.Minecraft;

public final class ClipboardIO {

    private ClipboardIO() {
    }

    public static String get() {
        try {
            String s = Minecraft.getInstance().keyboardHandler.getClipboard();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    public static void set(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }
}
