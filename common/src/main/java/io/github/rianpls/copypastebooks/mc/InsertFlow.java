package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.core.PageMarkers;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

/** Text (file/clipboard) → book pages, with auto/manual multivolume handling. Dialogs are async. */
public final class InsertFlow {

    private record Input(String text, String title) {
    }

    private InsertFlow() {
    }

    /** Paste from the open book edit screen. Manual keeps the selector; auto hands out books. */
    public static void intoEditor(BookEditBridge editor, boolean fromFile) {
        if (busyGuard()) {
            return;
        }
        Config.MultivolumeMethod method = CPB.config().multivolumeMethod;
        acquire(fromFile, input -> {
            List<List<String>> volumes = prepare(input.text());
            if (volumes == null) {
                return;
            }
            if (volumes.size() > 1 && method == Config.MultivolumeMethod.AUTO) {
                ScreenNav.open(null); // leave the editor; auto works on the inventory
                AutoDispatch.start(volumes, input.title());
                return;
            }
            // Manual or single volume: needs the editor still open.
            if (ScreenNav.current() != editor) {
                Feedback.error("err.screen.closed");
                return;
            }
            // A fresh import repurposes the held book — its old tracked number (if any) dies.
            editor.copypastebooks$onFreshImport();
            VolumeState.setPending(volumes, input.title());
            VolumeState.select(1);
            // The editor records this as one atomic undoable action: text, current-volume
            // identity and selector bookkeeping all travel together through undo/redo.
            editor.copypastebooks$setPagesFromVolume(VolumeState.volume(1), 1);
            if (VolumeState.count() > 1) {
                Feedback.info("msg.volume.loaded", 1, VolumeState.count());
            } else {
                Feedback.info("msg.pasted.pages", VolumeState.volume(1).size());
            }
            editor.copypastebooks$refreshVolumeWidgets();
        });
    }

    /** Fresh /importbook: reads the source, then routes by method. */
    public static void commandImport(boolean fromFile, Config.MultivolumeMethod method) {
        if (busyGuard()) {
            return;
        }
        acquire(fromFile, input -> {
            List<List<String>> volumes = prepare(input.text());
            if (volumes == null) {
                return;
            }
            if (volumes.size() == 1) {
                fillHeldSingle(volumes.get(0), input.title());
                return;
            }
            if (method == Config.MultivolumeMethod.AUTO) {
                AutoDispatch.start(volumes, input.title());
                return;
            }
            InteractionHand hand = findHeldWritable();
            if (hand == null) {
                Feedback.error("err.need_writable");
                return;
            }
            VolumeState.setPending(volumes, input.title());
            VolumeState.select(1);
            sendEditPacket(hand, VolumeState.volume(1));
            VolumeState.markInserted(1);
            VolumeState.rememberAtSlot(slotOf(hand), 1, VolumeState.volume(1));
            reportInserted(1);
        });
    }

    private static void fillHeldSingle(List<String> pages, String title) {
        InteractionHand hand = findHeldWritable();
        if (hand == null) {
            Feedback.error("err.need_writable");
            return;
        }
        // Bind the single volume + file title to the exact held slot so the sign screen
        // can prefill it without guessing from another book with identical content.
        VolumeState.setPending(List.of(List.copyOf(pages)), title);
        sendEditPacket(hand, pages);
        VolumeState.rememberAtSlot(slotOf(hand), 1, pages);
        Feedback.info("msg.pasted.pages", pages.size());
    }

    /** Continues a pending MANUAL multi-volume paste into a fresh held book. */
    public static boolean continueIntoHeld(InteractionHand hand) {
        if (!VolumeState.hasNextToInsert()) {
            return false;
        }
        int next = VolumeState.nextToInsert();
        if (next < 1) {
            return false;
        }
        sendEditPacket(hand, VolumeState.volume(next));
        VolumeState.markInserted(next);
        VolumeState.rememberAtSlot(slotOf(hand), next, VolumeState.volume(next));
        reportInserted(next);
        return true;
    }

    private static void reportInserted(int volume) {
        int total = VolumeState.count();
        int pages = VolumeState.volume(volume).size();
        if (total == 1) {
            Feedback.info("msg.pasted.pages", pages);
        } else if (volume < total) {
            Feedback.info("msg.pasted.volume", volume, total, pages);
        } else {
            Feedback.info("msg.pasted.volume.last", volume, total, pages);
        }
    }

    /** True (with a message) while a queued fill is still running — no parallel pastes. */
    private static boolean busyGuard() {
        if (BookSendQueue.isBusy()) {
            Feedback.error("err.busy.filling");
            return true;
        }
        return false;
    }

    private static InteractionHand findHeldWritable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() == Items.WRITABLE_BOOK) {
                return hand;
            }
        }
        return null;
    }

    private static int slotOf(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? Minecraft.getInstance().player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
    }

    /** Gets the source text: clipboard synchronously, file via the async dialog. */
    private static void acquire(boolean fromFile, Consumer<Input> next) {
        if (!fromFile) {
            String text = ClipboardIO.get();
            if (text.isBlank()) {
                Feedback.error("err.clipboard.empty");
                return;
            }
            next.accept(new Input(text, ""));
            return;
        }
        FileDialogs.openTxt(Lang.trs("dialog.open.title"), result -> {
            if (result.failed()) {
                Feedback.error("err.dialog.open");
                return;
            }
            if (result.cancelled()) {
                return;
            }
            Path file = result.path();
            String text = readTextFile(file);
            if (text == null) {
                Feedback.error("err.file.read", file.getFileName().toString());
                return;
            }
            if (text.isBlank()) {
                Feedback.error("err.text.empty");
                return;
            }
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String title = dot > 0 ? name.substring(0, dot) : name;
            next.accept(new Input(text, title.trim()));
        });
    }

    private static String readTextFile(Path file) {
        try {
            return stripLeadingBom(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException utf8Failed) {
            // Not UTF-8 — retry with the Windows Cyrillic codepage before giving up.
            try {
                return stripLeadingBom(new String(Files.readAllBytes(file), Charset.forName("windows-1251")));
            } catch (Exception e) {
                CopyPasteBooks.LOGGER.error("Can't read {}", file, e);
                return null;
            }
        } catch (Exception e) {
            CopyPasteBooks.LOGGER.error("Can't read {}", file, e);
            return null;
        }
    }

    private static String stripLeadingBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** @return volumes of ≤100 pages each, or null (feedback already sent) */
    private static List<List<String>> prepare(String rawText) {
        String text = PageFit.normalize(rawText);
        List<String> pages;
        if (PageMarkers.hasMarkers(text)) {
            pages = new ArrayList<>();
            for (String page : PageMarkers.split(text)) {
                if (PageFit.INSTANCE.fits(page)) {
                    pages.add(page);
                } else {
                    io.github.rianpls.copypastebooks.core.LosslessSplit.Result result = PageFit.rewrapPage(page);
                    if (result.unfittable() != null) {
                        return charTooBig(result.unfittable());
                    }
                    pages.addAll(result.pages());
                }
            }
        } else {
            io.github.rianpls.copypastebooks.core.LosslessSplit.Result result = PageFit.wrapText(text);
            if (result.unfittable() != null) {
                return charTooBig(result.unfittable());
            }
            pages = new ArrayList<>(result.pages());
        }
        // No trailing-blank trimming: the splitter creates nothing spurious, and trailing
        // empty pages/newlines are the user's real content (e.g. "A" + 40 line breaks).
        if (pages.isEmpty() || pages.stream().allMatch(String::isBlank)) {
            Feedback.error("err.text.empty");
            return null;
        }
        return VolumeState.splitIntoVolumes(pages);
    }

    /** A single character exceeds the configured byte cap — refuse, never drop it silently. */
    private static List<List<String>> charTooBig(String character) {
        Feedback.error("err.char.toobig", character,
                character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                PageFit.effectiveByteLimit());
        return null;
    }

    public static void sendEditPacket(InteractionHand hand, List<String> pages) {
        int slot = hand == InteractionHand.MAIN_HAND
                ? Minecraft.getInstance().player.getInventory().getSelectedSlot()
                : Inventory.SLOT_OFFHAND;
        // Filling the held book repurposes it — any tracked number it carried dies here
        // (manual volume paste, single import, erase all come through this path).
        VolumeTracker.removeAtSlot(slot);
        sendEditBySlot(slot, pages, null);
    }

    /**
     * Sends an edit-book packet for a specific inventory slot (hotbar 0-8 or off-hand 40).
     * Tracker-neutral: callers decide explicitly what happens to a mark in that slot.
     */
    public static void sendEditBySlot(int slot, List<String> pages, String title) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            return;
        }
        // Mirror the server's result locally so the book doesn't render stale: a titled edit
        // becomes a written book, an untitled one stays a filled book & quill.
        if (title == null) {
            ItemStack stack = player.getInventory().getItem(slot);
            stack.set(DataComponents.WRITABLE_BOOK_CONTENT,
                    new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
        } else {
            player.getInventory().setItem(slot, BookItems.written(pages, title, player.getName().getString()));
        }
        minecraft.getConnection().send(new ServerboundEditBookPacket(
                slot, List.copyOf(pages), title == null ? Optional.empty() : Optional.of(title)));
    }
}
