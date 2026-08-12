package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.CopyPasteBooks;
import io.github.rianpls.copypastebooks.core.Config;
import io.github.rianpls.copypastebooks.core.FileNamer;
import io.github.rianpls.copypastebooks.core.PageMarkers;
import io.github.rianpls.copypastebooks.core.PageRange;
import io.github.rianpls.copypastebooks.gui.FormattingAskScreen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

/** Book → clipboard/file, with range filtering and the formatting question. */
public final class CopyOutFlow {

    private CopyOutFlow() {
    }

    public static void copyWithDefaults(BookSource source, Screen returnTo) {
        copy(source, "", CPB.config().pageMarkers, CPB.config().destination, returnTo);
    }

    /**
     * @param rangeText page selection ("1-13, 15"); blank = whole book
     * @param returnTo screen to come back to if the formatting question pops up (null = game)
     */
    public static void copy(BookSource source, String rangeText, boolean markers,
                            Config.Destination destination, Screen returnTo) {
        if (source == null || source.pages().isEmpty()) {
            Feedback.info("msg.book.empty");
            return;
        }
        PageRange.Result range = PageRange.parse(rangeText, source.pages().size());
        if (range.badToken() != null) {
            Feedback.error("err.range.bad", range.badToken());
            return;
        }
        if (!range.ok()) {
            Feedback.error("err.range.out", source.pages().size());
            return;
        }
        List<BookSource.Page> picked = range.pages().stream().map(source.pages()::get).toList();
        if (picked.stream().allMatch(p -> p.plain().isBlank() && p.withCodes().isBlank())) {
            Feedback.info("msg.book.empty");
            return;
        }
        boolean formatted = picked.stream().anyMatch(BookSource.Page::formatted);
        Config.FormattingMode mode = CPB.config().formatting;
        if (formatted && mode == Config.FormattingMode.ASK) {
            CPB.later(() -> ScreenNav.open(new FormattingAskScreen(returnTo,
                    withCodes -> deliver(picked, source.title(), withCodes, markers, destination))));
            return;
        }
        deliver(picked, source.title(), mode == Config.FormattingMode.WITH_CODES, markers, destination);
    }

    public static void deliver(List<BookSource.Page> picked, String title, boolean withCodes,
                               boolean markers, Config.Destination destination) {
        List<String> texts = picked.stream()
                .map(p -> withCodes ? p.withCodes() : p.plain())
                .toList();
        String body = markers ? PageMarkers.join(texts) : String.join("\n", texts);
        if (destination == Config.Destination.CLIPBOARD) {
            ClipboardIO.set(body);
            Feedback.info("msg.copied.clipboard", texts.size());
        } else {
            saveToFile(texts.size(), title, body);
        }
    }

    private static void saveToFile(int pageCount, String title, String body) {
        String base = FileNamer.sanitizeBase(title, "book_" + LocalDate.now());
        String folder = CPB.config().saveFolder.trim();
        if (!folder.isBlank()) {
            try {
                Path dir = Path.of(folder);
                Files.createDirectories(dir);
                String name = FileNamer.uniqueTxt(base, f -> Files.exists(dir.resolve(f)));
                Path file = dir.resolve(name);
                Files.writeString(file, body, StandardCharsets.UTF_8);
                Feedback.savedFile(pageCount, file);
            } catch (Exception e) {
                CopyPasteBooks.LOGGER.error("Silent save failed", e);
                Feedback.error("err.folder.create", folder);
            }
            return;
        }
        FileDialogs.saveTxt(Lang.trs("dialog.save.title"), base + ".txt", result -> {
            if (result.failed()) {
                Feedback.error("err.dialog.save");
                return;
            }
            if (result.cancelled()) {
                return;
            }
            try {
                Files.writeString(result.path(), body, StandardCharsets.UTF_8);
                Feedback.savedFile(pageCount, result.path());
            } catch (Exception e) {
                CopyPasteBooks.LOGGER.error("Save failed", e);
                Feedback.error("err.file.write", result.path().toString());
            }
        });
    }
}
