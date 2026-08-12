package io.github.rianpls.copypastebooks.fabric;

import io.github.rianpls.copypastebooks.command.CPBCommands;
import io.github.rianpls.copypastebooks.mc.CPB;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class CPBFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CPB.init(FabricLoader.getInstance().getConfigDir());
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && Boolean.getBoolean("copypastebooks.classloadtest")) {
            classLoadTest();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> CPB.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CPB.onDisconnect());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(CPBCommands.copybook());
            dispatcher.register(CPBCommands.importbook());
            dispatcher.register(CPBCommands.copypastebooks());
        });
    }

    /**
     * Debug aid (-Dcopypastebooks.classloadtest=true): force-loads the mixed-into screens
     * so mixin application errors surface at startup instead of on first book open.
     */
    private static void classLoadTest() {
        String[] targets = {
                "net.minecraft.client.gui.screens.inventory.BookEditScreen",
                "net.minecraft.client.gui.screens.inventory.BookViewScreen",
                "net.minecraft.client.gui.screens.inventory.BookSignScreen",
                "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen",
                "net.minecraft.client.gui.Gui",
                "net.minecraft.client.gui.Hud",
                "net.minecraft.world.inventory.AbstractContainerMenu",
                "net.minecraft.client.gui.components.MultiLineEditBox",
                "net.minecraft.client.gui.components.MultilineTextField",
        };
        for (String name : targets) {
            try {
                Class.forName(name);
                io.github.rianpls.copypastebooks.CopyPasteBooks.LOGGER.info("[CPB-TEST] mixin target loads fine: {}", name);
            } catch (ClassNotFoundException absent) {
                // Gui/Hud: only one of the two exists per game version — that's expected.
                io.github.rianpls.copypastebooks.CopyPasteBooks.LOGGER.info("[CPB-TEST] class absent on this version (ok): {}", name);
            } catch (Throwable t) {
                io.github.rianpls.copypastebooks.CopyPasteBooks.LOGGER.error("[CPB-TEST] MIXIN FAILED on {}", name, t);
            }
        }
    }
}
