package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.VolumeNaming;
import io.github.rianpls.copypastebooks.gui.ConfirmActionScreen;
import io.github.rianpls.copypastebooks.gui.VolumeNameScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * "Auto" multivolume handling. In creative it hands out several filled books at once
 * (reusing empty books & quills first) via the creative-slot packet. In survival it fills
 * the empty books & quills in the hotbar/off-hand — the only slots the edit packet can
 * target — pacing the packets so the server's "book edited too quickly" guard doesn't
 * kick. If the text needs more books than are available, it asks for confirmation and
 * fills up to the limit. A single resulting book gets no number; two or more are numbered.
 */
public final class AutoDispatch {

    private AutoDispatch() {
    }

    /** Entry point for a fresh multivolume auto paste (volumes.size() > 1). */
    public static void start(List<List<String>> volumes, String fileTitle) {
        if (BookSendQueue.isBusy()) {
            Feedback.error("err.busy.filling");
            return;
        }
        // A fresh AUTO import supersedes any old MANUAL selector/title state. Without
        // this, a selector deliberately kept visible by autoHideVolumeSelector=false
        // could reappear in the next unrelated writable book.
        VolumeState.clear();
        if (CPB.config().autoNameVolumes) {
            int maxBase = VolumeNaming.maxBaseLength(volumes.size(), BookItems.TITLE_LIMIT);
            // Deferred a tick: when invoked from a command the chat screen is still
            // closing and would immediately close a screen opened right now.
            CPB.later(() -> ScreenNav.open(new VolumeNameScreen(ScreenNav.current(), fileTitle, maxBase,
                    name -> dispatch(volumes, name))));
        } else {
            dispatch(volumes, "");
        }
    }

    private static void dispatch(List<List<String>> volumes, String name) {
        boolean sign = name != null && !name.isBlank();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.hasInfiniteMaterials()) {
            dispatchCreative(volumes, name, sign);
        } else {
            dispatchSurvival(volumes, name, sign);
        }
    }

    // ---------------------------------------------------------------- creative

    private static void dispatchCreative(List<List<String>> volumes, String name, boolean sign) {
        List<Integer> targets = creativeTargetMenuSlots(Minecraft.getInstance().player);
        int count = Math.min(targets.size(), volumes.size());
        if (count == 0) {
            Feedback.warn("warn.auto.morespace", 0, volumes.size());
            return;
        }
        fillCreative(volumes, name, sign, targets, count);
    }

    private static void fillCreative(List<List<String>> volumes, String name, boolean sign,
                                     List<Integer> targets, int count) {
        Minecraft mc = Minecraft.getInstance();
        String author = mc.player.getName().getString();
        boolean numbered = count >= 2;
        int color = !sign && numbered ? VolumeTracker.newGroupColor() : 0;
        int totalVolumes = volumes.size();
        int[] sent = {0};
        for (int i = 0; i < count; i++) {
            int menuSlot = targets.get(i);
            List<String> pages = volumes.get(i);
            String title = titleFor(sign, numbered, name, i + 1);
            ItemStack stack = sign ? BookItems.written(pages, title, author) : BookItems.writable(pages);
            boolean last = i == count - 1;
            int number = i + 1;
            BookSendQueue.enqueueCreative(() -> {
                // The queue delivers this seconds later — re-check the slot is still free
                // or still a blank book (the player may have moved items meanwhile).
                Slot menuSlotObj = mc.player.inventoryMenu.getSlot(menuSlot);
                ItemStack current = menuSlotObj.getItem();
                if (current.isEmpty() || BookItems.isBlankWritable(current)) {
                    // Update the client slot too — the creative packet only tells the server,
                    // so without this the book renders stale until dropped/re-picked.
                    menuSlotObj.set(stack.copy());
                    mc.gameMode.handleCreativeModeItemAdd(stack, menuSlot);
                    sent[0]++;
                    if (!sign && numbered) {
                        // Mark only the book that actually landed, with its exact slot.
                        VolumeTracker.registerPlaced(menuSlotObj.getContainerSlot(), pages, color, number);
                    } else {
                        // Unnumbered/signed fill replaced the slot — an old mark there dies.
                        VolumeTracker.removeAtSlot(menuSlotObj.getContainerSlot());
                    }
                } else {
                    Feedback.warn("warn.slot.changed");
                }
                if (last) {
                    if (sent[0] < totalVolumes) {
                        Feedback.warn("warn.auto.morespace", sent[0], totalVolumes);
                    } else {
                        Feedback.info("msg.auto.spawned", sent[0]);
                    }
                }
            });
        }
    }

    /** Target menu slots: reuse blank books first, then empty hotbar (36-44), then inventory (9-35), off-hand (45). */
    private static List<Integer> creativeTargetMenuSlots(LocalPlayer player) {
        List<Integer> reuse = new ArrayList<>();
        List<Integer> hotbar = new ArrayList<>();
        List<Integer> main = new ArrayList<>();
        List<Integer> offhand = new ArrayList<>();
        List<Slot> slots = player.inventoryMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container != player.getInventory()) {
                continue;
            }
            ItemStack item = slot.getItem();
            if (BookItems.isBlankWritable(item)) {
                reuse.add(i);
            } else if (item.isEmpty()) {
                if (i >= 36 && i <= 44) {
                    hotbar.add(i);
                } else if (i == 45) {
                    offhand.add(i);
                } else if (i >= 9 && i <= 35) {
                    main.add(i);
                }
            }
        }
        List<Integer> targets = new ArrayList<>(reuse);
        targets.addAll(hotbar);
        targets.addAll(main);
        targets.addAll(offhand);
        return targets;
    }

    // ---------------------------------------------------------------- survival

    private static void dispatchSurvival(List<List<String>> volumes, String name, boolean sign) {
        int available = blankSlots().size();
        int need = volumes.size();
        if (available == 0) {
            Feedback.error("err.auto.noblanks");
            return;
        }
        if (available >= need) {
            fillSurvival(volumes, name, sign, need);
            return;
        }
        // Not enough books for the whole text — confirm filling up to the limit.
        CPB.later(() -> ScreenNav.open(new ConfirmActionScreen(ScreenNav.current(),
                Lang.tr("confirm.capacity.title"),
                Lang.tr("confirm.capacity.body", need, available),
                () -> fillSurvival(volumes, name, sign, Math.min(blankSlots().size(), need)))));
    }

    private static void fillSurvival(List<List<String>> volumes, String name, boolean sign, int requested) {
        List<Integer> blanks = blankSlots();
        int count = Math.min(requested, Math.min(blanks.size(), volumes.size()));
        if (count == 0) {
            Feedback.error("err.auto.noblanks");
            return;
        }
        boolean numbered = count >= 2;
        int color = !sign && numbered ? VolumeTracker.newGroupColor() : 0;
        int total = volumes.size();
        int[] sent = {0};
        for (int k = 0; k < count; k++) {
            int invSlot = blanks.get(k);
            List<String> pages = volumes.get(k);
            String title = titleFor(sign, numbered, name, k + 1);
            boolean last = k == count - 1;
            int number = k + 1;
            BookSendQueue.enqueueSurvival(() -> {
                // Re-check at send time: the slot must still hold a blank book & quill
                // (the player may have moved items while the queue was draining).
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null && BookItems.isBlankWritable(player.getInventory().getItem(invSlot))) {
                    // Filling replaces whatever mark sat in this slot; the numbered path
                    // re-registers right after (registerPlaced replaces it itself).
                    InsertFlow.sendEditBySlot(invSlot, pages, title);
                    sent[0]++;
                    if (!sign && numbered) {
                        // Mark only the book that actually landed, with its exact slot.
                        VolumeTracker.registerPlaced(invSlot, pages, color, number);
                    } else {
                        VolumeTracker.removeAtSlot(invSlot);
                    }
                } else {
                    Feedback.warn("warn.slot.changed");
                }
                if (last) {
                    if (sent[0] < total) {
                        Feedback.warn("warn.auto.moreblanks", sent[0], total);
                    } else {
                        Feedback.info("msg.auto.filled", sent[0]);
                    }
                }
            });
        }
    }

    private static String titleFor(boolean sign, boolean numbered, String name, int oneBased) {
        if (!sign) {
            return null;
        }
        if (numbered) {
            return VolumeNaming.forVolume(name, oneBased, BookItems.TITLE_LIMIT);
        }
        return name.length() > BookItems.TITLE_LIMIT ? name.substring(0, BookItems.TITLE_LIMIT) : name;
    }

    /** Inventory indices of blank books & quills in the hotbar (0-8) and off-hand (40). */
    private static List<Integer> blankSlots() {
        List<Integer> out = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return out;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (BookItems.isBlankWritable(inventory.getItem(i))) {
                out.add(i);
            }
        }
        if (BookItems.isBlankWritable(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            out.add(Inventory.SLOT_OFFHAND);
        }
        return out;
    }
}
