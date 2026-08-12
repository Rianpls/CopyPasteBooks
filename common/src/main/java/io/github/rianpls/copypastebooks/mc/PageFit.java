package io.github.rianpls.copypastebooks.mc;

import io.github.rianpls.copypastebooks.core.LosslessSplit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FormattedText;

/**
 * Vanilla writable-book page constraints (edit length 1024, 14 rendered lines at 114px)
 * plus the optional configurable byte cap for strict servers.
 *
 * One splitting algorithm ({@link LosslessSplit}: verbatim text, densely packed pages,
 * breaks at the separator closest to the page end) serves both the button import and
 * smart paste — the same text yields the same pages either way. The only difference is
 * the fit test: the button import applies the byte cap ({@link #INSTANCE}), Ctrl+V
 * behaves like typing and uses vanilla limits only ({@link #fitsVanilla}).
 */
public final class PageFit implements LosslessSplit.FitTester {
    public static final PageFit INSTANCE = new PageFit();
    /** Default per-page byte cap when the config is 0; safely under Paper's 2560 default. */
    public static final int SAFE_DEFAULT_PAGE_BYTES = 2048;
    /** Hard char ceiling per page; one below vanilla's 1024 as a safety margin. */
    private static final int MAX_PAGE_CHARS = 1023;

    private PageFit() {
    }

    @Override
    public boolean fits(String pageText) {
        if (pageText.length() > MAX_PAGE_CHARS) {
            return false;
        }
        if (!fitsVanillaHeight(pageText)) {
            return false;
        }
        // Paper rejects pages over ~2560 bytes ("Book too big"); multi-byte chars (em dash,
        // CJK) can push 1000+ chars past that, so cap bytes even when the config is 0.
        return pageText.getBytes(StandardCharsets.UTF_8).length <= effectiveByteLimit();
    }

    /**
     * Vanilla-only page limits (length and rendered height), without the byte cap. Smart paste must
     * behave like typing — the byte limit is a button-import concern, configurable there.
     */
    public static boolean fitsVanilla(String pageText) {
        return pageText.length() <= MAX_PAGE_CHARS && fitsVanillaHeight(pageText);
    }

    private static boolean fitsVanillaHeight(String pageText) {
        return Minecraft.getInstance().font.wordWrapHeight(FormattedText.of(pageText), 114) <= 128;
    }

    /**
     * Normalizes line endings and splits raw text into book pages (byte cap included).
     * Check {@link LosslessSplit.Result#unfittable()}: a character bigger than a tiny
     * configured byte cap aborts the split and must abort the import.
     */
    public static LosslessSplit.Result wrapText(String raw) {
        return LosslessSplit.split(normalize(raw), INSTANCE, MAX_PAGE_CHARS, Integer.MAX_VALUE);
    }

    /**
     * Splits text into pages for smart paste: same algorithm as {@link #wrapText}, but
     * vanilla limits only (no byte cap) and bounded by the page budget.
     */
    public static LosslessSplit.Result splitLossless(String text, int maxPages) {
        return LosslessSplit.split(text, PageFit::fitsVanilla, MAX_PAGE_CHARS, maxPages);
    }

    /** Re-splits a single page that came from markers but doesn't fit as-is. */
    public static LosslessSplit.Result rewrapPage(String pageText) {
        return LosslessSplit.split(pageText, INSTANCE, MAX_PAGE_CHARS, Integer.MAX_VALUE);
    }

    /** Effective per-page byte cap: the configured value, or the safe default at 0. */
    public static int effectiveByteLimit() {
        int limit = CPB.config().pageByteLimit;
        return limit <= 0 ? SAFE_DEFAULT_PAGE_BYTES : limit;
    }

    public static String normalize(String raw) {
        return raw.replace("\r\n", "\n").replace('\r', '\n');
    }
}
