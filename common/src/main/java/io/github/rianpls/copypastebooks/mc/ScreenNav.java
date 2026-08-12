package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Screen navigation that works on both supported game versions:
 * 26.1.x has {@code Minecraft.setScreen}/{@code Minecraft.screen},
 * 26.2 moved both to {@code Gui} ({@code minecraft.gui.setScreen()}/{@code gui.screen()}).
 * Everything involved is public and unobfuscated, so a feature-detected MethodHandle is safe.
 */
public final class ScreenNav {
    private static final MethodHandle SET_SCREEN;
    private static final MethodHandle GET_SCREEN;

    static {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle set = null;
        MethodHandle get = null;
        boolean viaGui = false;
        try {
            set = lookup.findVirtual(Minecraft.class, "setScreen",
                    MethodType.methodType(void.class, Screen.class));
            get = lookup.findGetter(Minecraft.class, "screen", Screen.class);
        } catch (ReflectiveOperationException legacyMissing) {
            try {
                Class<?> guiClass = Class.forName("net.minecraft.client.gui.Gui");
                MethodHandle guiGetter = lookup.findGetter(Minecraft.class, "gui", guiClass);
                MethodHandle guiSet = lookup.findVirtual(guiClass, "setScreen",
                        MethodType.methodType(void.class, Screen.class));
                MethodHandle guiGet = lookup.findVirtual(guiClass, "screen",
                        MethodType.methodType(Screen.class));
                set = MethodHandles.filterArguments(guiSet, 0, guiGetter);
                get = MethodHandles.filterArguments(guiGet, 0, guiGetter);
                viaGui = true;
            } catch (ReflectiveOperationException e) {
                CopyPasteBooks.LOGGER.error("CopyPasteBooks can't find setScreen — screens won't open", e);
            }
        }
        SET_SCREEN = set;
        GET_SCREEN = get;
        CopyPasteBooks.LOGGER.debug("ScreenNav resolved (via Gui: {})", viaGui);
    }

    private ScreenNav() {
    }

    public static void open(Screen screen) {
        if (SET_SCREEN == null) {
            return;
        }
        try {
            SET_SCREEN.invoke(Minecraft.getInstance(), screen);
        } catch (Throwable t) {
            CopyPasteBooks.LOGGER.error("setScreen failed", t);
        }
    }

    public static Screen current() {
        if (GET_SCREEN == null) {
            return null;
        }
        try {
            return (Screen) GET_SCREEN.invoke(Minecraft.getInstance());
        } catch (Throwable t) {
            return null;
        }
    }
}
