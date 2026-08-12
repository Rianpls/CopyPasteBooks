package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.TrackerCore;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Adapts {@link TrackerCore} to Minecraft ItemStacks and inventory clicks. The
 * {@code AbstractContainerMenuMixin} reports click transitions, {@link CPB#tick()} runs
 * recovery, and the render mixins use side-effect-free lookups. State clears on disconnect.
 */
public final class VolumeTracker {

    /** ARGB colors, one per paste-group, cycled. */
    private static final int[] COLORS = {
            0xFF55FF55, 0xFFFFFF55, 0xFF55FFFF, 0xFFFF55FF,
            0xFFFFAA00, 0xFFFF7777, 0xFF88AAFF, 0xFF55FF99
    };

    public record Mark(int color, int number) {
    }

    private static final TrackerCore CORE = new TrackerCore();
    private static int nextColorIndex;

    private static final TrackerCore.View VIEW = new TrackerCore.View() {
        @Override
        public int slotCount() {
            return Inventory.SLOT_OFFHAND + 1;
        }

        @Override
        public Object stackAt(int slot) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return null;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            return stack.isEmpty() ? null : stack;
        }

        @Override
        public Object cursor() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return null;
            }
            ItemStack stack = player.containerMenu.getCarried();
            return stack.isEmpty() ? null : stack;
        }

        @Override
        public List<String> pagesOf(Object token) {
            ItemStack stack = (ItemStack) token;
            return BookItems.isWritable(stack) ? BookItems.writablePages(stack) : null;
        }

        @Override
        public boolean claimable(int slot) {
            return slot < 36 || slot == Inventory.SLOT_OFFHAND; // books never live in armor slots
        }
    };

    private VolumeTracker() {
    }

    // ------------------------------------------------------------------ registration

    public static synchronized int newGroupColor() {
        int color = COLORS[Math.floorMod(nextColorIndex, COLORS.length)];
        nextColorIndex++;
        return color;
    }

    /**
     * Registers a freshly filled book sitting in {@code inventorySlot}. Call after the
     * local stack was updated, so the current reference can be captured. Any older mark
     * in that slot is replaced.
     */
    public static synchronized void registerPlaced(int inventorySlot, List<String> pages, int color, int number) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        CORE.register(VIEW, inventorySlot, pages, color, number);
    }

    public static synchronized long idAtSlot(int inventorySlot) {
        return CORE.idAtSlot(inventorySlot);
    }

    /**
     * Resolve the slot's tracked id right now (token follow + content recovery, no
     * aging) — used when the book edit screen opens, possibly mid-resync.
     */
    public static synchronized long resolveForEdit(int inventorySlot) {
        if (Minecraft.getInstance().player == null) {
            return -1;
        }
        return CORE.resolveAt(VIEW, inventorySlot);
    }

    /** Keeps the number/color across a manual edit of a tracked book's text. */
    public static synchronized void updateContent(long id, List<String> pages) {
        CORE.updateContent(id, pages);
    }

    /** Removes the mark at (or, while briefly lost, last seen at) the slot. */
    public static synchronized void removeAtSlot(int inventorySlot) {
        CORE.removeAtSlot(inventorySlot);
    }

    /** Removes a mark by its exact id — preferred when the caller knows it (book editor). */
    public static synchronized void removeId(long id) {
        CORE.remove(id);
    }

    public static synchronized void clear() {
        CORE.clear();
    }

    // ------------------------------------------------------------------ rendering (pure)

    /**
     * Reference lookup for all render paths (HUD hotbar and container screens); no side
     * effects. Renderers cannot resolve by slot index: the creative inventory tab wraps
     * slots so their container-slot number is a menu index, not an inventory index.
     */
    public static synchronized Mark lookupByStack(ItemStack stack) {
        return convert(CORE.markByToken(stack.isEmpty() ? null : stack));
    }

    private static Mark convert(TrackerCore.Mark mark) {
        return mark == null ? null : new Mark(mark.color(), mark.number());
    }

    // ------------------------------------------------------------------ transitions & ticking

    /**
     * Called at the head of {@code AbstractContainerMenu.clicked}, before the click
     * mutates the menu locally. Only reacts to the local player's menus — the same
     * method runs for the integrated server's ServerPlayer on another thread. Maps the
     * clicked menu slot (and the hotbar slot of a swap key press) to player inventory
     * indices for the core's transition hints.
     */
    public static synchronized void beforeMenuClick(AbstractContainerMenu menu, int menuSlotId, int button,
                                                    ContainerInput input, Player player) {
        if (!(player instanceof LocalPlayer local) || Minecraft.getInstance().player != local
                || CORE.isEmpty()) {
            return;
        }
        int clicked = -1;
        if (menuSlotId >= 0 && menuSlotId < menu.slots.size()) {
            Slot slot = menu.slots.get(menuSlotId);
            if (slot.container == local.getInventory()) {
                clicked = slot.getContainerSlot();
            }
        }
        int swap = input == ContainerInput.SWAP && (button >= 0 && button < 9 || button == Inventory.SLOT_OFFHAND)
                ? button : -1;
        CORE.beginClick(VIEW, clicked, swap);
    }

    /** Called when {@code clicked} returns; consumes the snapshot (no-op without one). */
    public static synchronized void afterMenuClick(Player player) {
        if (!(player instanceof LocalPlayer local) || Minecraft.getInstance().player != local) {
            return;
        }
        CORE.endClick(VIEW);
    }

    /** Once per client tick from {@code CPB.tick}: token follow, content recovery, aging. */
    public static synchronized void tick() {
        if (Minecraft.getInstance().player == null) {
            CORE.clear();
            return;
        }
        CORE.tick(VIEW);
    }
}
