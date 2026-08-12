package io.github.rianpls.copypastebooks.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.gui.SettingsScreen;
import io.github.rianpls.copypastebooks.mc.BookSource;
import io.github.rianpls.copypastebooks.mc.CPB;
import io.github.rianpls.copypastebooks.mc.CopyOutFlow;
import io.github.rianpls.copypastebooks.mc.Feedback;
import io.github.rianpls.copypastebooks.mc.InsertFlow;
import io.github.rianpls.copypastebooks.mc.Lang;
import io.github.rianpls.copypastebooks.mc.ScreenNav;
import io.github.rianpls.copypastebooks.mc.VolumeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

/**
 * The command trees are generic over the source type, so the same definitions register
 * on Fabric (FabricClientCommandSource) and NeoForge (CommandSourceStack) unchanged.
 * Everything runs purely client-side; the source is never touched.
 */
public final class CPBCommands {

    private CPBCommands() {
    }

    /** /copybook [clipboard|file] — held book → out. */
    public static <S> LiteralArgumentBuilder<S> copybook() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("copybook");
        root.executes(context -> copyHeld(null));
        root.then(LiteralArgumentBuilder.<S>literal("clipboard")
                .executes(context -> copyHeld(Config.Destination.CLIPBOARD)));
        root.then(LiteralArgumentBuilder.<S>literal("file")
                .executes(context -> copyHeld(Config.Destination.FILE)));
        return root;
    }

    /** /importbook [clipboard|file] [auto|manual] — clipboard/file → one or more books. */
    public static <S> LiteralArgumentBuilder<S> importbook() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("importbook");
        root.executes(context -> insert(null, null));
        for (Config.Destination src : Config.Destination.values()) {
            String srcName = src == Config.Destination.CLIPBOARD ? "clipboard" : "file";
            LiteralArgumentBuilder<S> srcNode = LiteralArgumentBuilder.<S>literal(srcName)
                    .executes(context -> insert(src, null));
            srcNode.then(LiteralArgumentBuilder.<S>literal("auto")
                    .executes(context -> insert(src, Config.MultivolumeMethod.AUTO)));
            srcNode.then(LiteralArgumentBuilder.<S>literal("manual")
                    .executes(context -> insert(src, Config.MultivolumeMethod.MANUAL)));
            root.then(srcNode);
        }
        return root;
    }

    /** /copypastebooks [en|ru|auto] — settings screen / language override. */
    public static <S> LiteralArgumentBuilder<S> copypastebooks() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.literal("copypastebooks");
        root.executes(context -> {
            CPB.later(() -> ScreenNav.open(new SettingsScreen(null, null)));
            return 1;
        });
        root.then(LiteralArgumentBuilder.<S>literal("en")
                .executes(context -> setLanguage(Config.LangMode.EN, "lang.en")));
        root.then(LiteralArgumentBuilder.<S>literal("ru")
                .executes(context -> setLanguage(Config.LangMode.RU, "lang.ru")));
        root.then(LiteralArgumentBuilder.<S>literal("auto")
                .executes(context -> setLanguage(Config.LangMode.AUTO, "lang.auto")));
        LiteralArgumentBuilder<S> delay = LiteralArgumentBuilder.literal("delay");
        delay.then(LiteralArgumentBuilder.<S>literal("creative")
                .then(RequiredArgumentBuilder.<S, Integer>argument("ms",
                                IntegerArgumentType.integer(0, Config.MAX_DELAY_MS))
                        .suggests((context, builder) -> {
                            builder.suggest(Config.DEFAULT_CREATIVE_DELAY_MS);
                            return builder.buildFuture();
                        })
                        .executes(context -> setDelay(true, IntegerArgumentType.getInteger(context, "ms")))));
        delay.then(LiteralArgumentBuilder.<S>literal("survival")
                .then(RequiredArgumentBuilder.<S, Integer>argument("ms",
                                IntegerArgumentType.integer(0, Config.MAX_DELAY_MS))
                        .suggests((context, builder) -> {
                            builder.suggest(Config.DEFAULT_SURVIVAL_DELAY_MS);
                            return builder.buildFuture();
                        })
                        .executes(context -> setDelay(false, IntegerArgumentType.getInteger(context, "ms")))));
        root.then(delay);
        return root;
    }

    private static int copyHeld(Config.Destination override) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            Feedback.error("err.not_ingame");
            return 0;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            BookSource source = BookSource.fromStack(player.getItemInHand(hand));
            if (source != null) {
                Config.Destination destination = override != null ? override : CPB.config().destination;
                CopyOutFlow.copy(source, "", CPB.config().pageMarkers, destination, null);
                return 1;
            }
        }
        Feedback.error("err.no_book");
        return 0;
    }

    private static int insert(Config.Destination sourceOverride, Config.MultivolumeMethod methodOverride) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            Feedback.error("err.not_ingame");
            return 0;
        }
        Config.MultivolumeMethod method = methodOverride != null ? methodOverride : CPB.config().multivolumeMethod;
        Config.Destination source = sourceOverride != null ? sourceOverride : CPB.config().destination;
        boolean fromFile = source == Config.Destination.FILE;

        if (method == Config.MultivolumeMethod.AUTO) {
            InsertFlow.commandImport(fromFile, Config.MultivolumeMethod.AUTO);
            return 1;
        }

        if (VolumeState.hasNextToInsert()) {
            // Prefer any blank held book. A filled main-hand book must not mask a blank
            // off-hand book that is ready for the next manual volume.
            InteractionHand hand = findBlankHeldWritable(player);
            if (hand != null && InsertFlow.continueIntoHeld(hand)) {
                return 1;
            }
        }
        InsertFlow.commandImport(fromFile, Config.MultivolumeMethod.MANUAL);
        return 1;
    }

    private static InteractionHand findBlankHeldWritable(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() == Items.WRITABLE_BOOK && isBlankBook(stack)) {
                return hand;
            }
        }
        return null;
    }

    private static boolean isBlankBook(ItemStack stack) {
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return content == null || content.getPages(false).allMatch(String::isBlank);
    }

    private static int setDelay(boolean creative, int ms) {
        int clamped = Config.clampDelay(ms);
        if (creative) {
            CPB.config().creativeDelayMs = clamped;
        } else {
            CPB.config().survivalDelayMs = clamped;
        }
        CPB.saveConfig();
        Feedback.info("cmd.delay.set",
                Lang.trs(creative ? "cmd.delay.creative.name" : "cmd.delay.survival.name"), clamped);
        return 1;
    }

    private static int setLanguage(Config.LangMode mode, String nameKey) {
        CPB.config().language = mode;
        CPB.saveConfig();
        Feedback.info("cmd.lang.set", Lang.trs(nameKey));
        return 1;
    }
}
