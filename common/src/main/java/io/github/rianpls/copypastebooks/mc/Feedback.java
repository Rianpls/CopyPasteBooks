package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import java.nio.file.Path;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Player-facing messages. Chat is the only channel that's stable on both 26.1 and 26.2. */
public final class Feedback {

    private Feedback() {
    }

    public static void info(String key, Object... args) {
        send(Lang.tr(key, args).withStyle(ChatFormatting.GRAY));
    }

    public static void error(String key, Object... args) {
        send(Lang.tr(key, args).withStyle(ChatFormatting.RED));
    }

    public static void warn(String key, Object... args) {
        send(Lang.tr(key, args).withStyle(ChatFormatting.GOLD));
    }

    public static void savedFile(int pageCount, Path file) {
        MutableComponent link = Component.literal(file.toAbsolutePath().toString())
                .withStyle(style -> style
                        .withUnderlined(true)
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath().toString()))
                        .withHoverEvent(new HoverEvent.ShowText(Lang.tr("msg.saved.file.hover"))));
        // Raw pattern (no args -> unformatted), e.g. "Pages saved: %d — %s"
        String raw = Lang.trs("msg.saved.file");
        int at = raw.indexOf("%s");
        String before = at >= 0 ? raw.substring(0, at) : raw + " ";
        String after = at >= 0 ? raw.substring(at + 2) : "";
        MutableComponent message = Component.literal(String.format(before, pageCount))
                .append(link)
                .append(after);
        send(message.withStyle(ChatFormatting.GRAY));
    }

    private static void send(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(message);
        } else {
            CopyPasteBooks.LOGGER.info("[msg] {}", message.getString());
        }
    }
}
