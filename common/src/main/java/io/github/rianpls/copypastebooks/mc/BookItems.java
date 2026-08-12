package io.github.rianpls.copypastebooks.mc;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

/** Builds and inspects book item stacks. */
public final class BookItems {
    /** Vanilla writable-book title limit (matches the sign screen's edit box). */
    public static final int TITLE_LIMIT = 15;

    private BookItems() {
    }

    public static ItemStack writable(List<String> pages) {
        ItemStack stack = new ItemStack(Items.WRITABLE_BOOK);
        stack.set(DataComponents.WRITABLE_BOOK_CONTENT,
                new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
        return stack;
    }

    public static ItemStack written(List<String> pages, String title, String author) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                pages.stream().<Component>map(Component::literal).map(Filterable::passThrough).toList(),
                true));
        return stack;
    }

    public static boolean isWritable(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == Items.WRITABLE_BOOK;
    }

    public static boolean isBlankWritable(ItemStack stack) {
        if (!isWritable(stack)) {
            return false;
        }
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return content == null || content.getPages(false).allMatch(String::isBlank);
    }

    public static List<String> writablePages(ItemStack stack) {
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return content == null ? List.of() : content.getPages(false).toList();
    }
}
