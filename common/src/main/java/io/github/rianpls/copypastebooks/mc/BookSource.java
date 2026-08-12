package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.LegacyText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A book's content captured for copying out: per-page plain text, §-coded text, and
 * whether formatting is actually applied (styles on components, or effective §-codes).
 */
public record BookSource(List<Page> pages, String title) {

    public record Page(String plain, String withCodes, boolean formatted) {
        static Page fromRaw(String raw) {
            boolean formatted = LegacyText.hasEffectiveCodes(raw);
            return new Page(formatted ? LegacyText.strip(raw) : raw, raw, formatted);
        }

        static Page fromComponent(Component component) {
            String withCodes = LegacyComponentSerializer.serialize(component);
            String visible = component.getString();
            boolean formatted = LegacyComponentSerializer.hasAppliedStyles(component)
                    || LegacyText.hasEffectiveCodes(visible);
            return new Page(LegacyText.strip(visible), withCodes, formatted);
        }
    }

    /** Draft pages straight from the book edit screen. */
    public static BookSource fromRawPages(List<String> rawPages, String title) {
        List<Page> pages = new ArrayList<>(rawPages.size());
        for (String raw : rawPages) {
            pages.add(Page.fromRaw(raw));
        }
        return new BookSource(List.copyOf(pages), title);
    }

    /** Signed-book pages as shown in the view screen (or from a lectern). */
    public static BookSource fromComponents(List<Component> components, String title) {
        List<Page> pages = new ArrayList<>(components.size());
        for (Component c : components) {
            pages.add(Page.fromComponent(c));
        }
        return new BookSource(List.copyOf(pages), title);
    }

    /** @return null when the stack is not a book */
    public static BookSource fromStack(ItemStack stack) {
        boolean filtered = Minecraft.getInstance().isTextFilteringEnabled();
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            return fromComponents(written.getPages(filtered), written.title().raw());
        }
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return fromRawPages(writable.getPages(filtered).toList(), "");
        }
        return null;
    }
}
