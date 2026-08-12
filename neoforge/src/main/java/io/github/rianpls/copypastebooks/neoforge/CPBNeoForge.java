package io.github.rianpls.copypastebooks.neoforge;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import io.github.rianpls.copypastebooks.command.CPBCommands;
import io.github.rianpls.copypastebooks.gui.SettingsScreen;
import io.github.rianpls.copypastebooks.mc.CPB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = CopyPasteBooks.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CopyPasteBooks.MOD_ID, value = Dist.CLIENT)
public final class CPBNeoForge {
    public CPBNeoForge(ModContainer container) {
        CPB.init(FMLPaths.CONFIGDIR.get());
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new SettingsScreen(parent, null));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        CPB.tick();
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(CPBCommands.copybook());
        event.getDispatcher().register(CPBCommands.importbook());
        event.getDispatcher().register(CPBCommands.copypastebooks());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CPB.onDisconnect();
    }
}
