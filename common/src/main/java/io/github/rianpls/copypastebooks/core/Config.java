package io.github.rianpls.copypastebooks.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Mod settings + JSON persistence (config/copypastebooks.json). */
public final class Config {

    public enum LangMode { AUTO, EN, RU }

    public enum Destination { CLIPBOARD, FILE }

    public enum FormattingMode { ASK, WITH_CODES, PLAIN }

    /** How text over one book (100 pages) is turned into multiple books. */
    public enum MultivolumeMethod { AUTO, MANUAL }

    /** What Escape/the inventory key does when the open draft has unsaved edits. */
    public enum CloseBehavior { ASK, SAVE, DISCARD }

    /** How strongly the destructive erase-all button is guarded. */
    public enum EraseBehavior { ASK_DELAY, ASK, IMMEDIATE }

    public LangMode language = LangMode.AUTO;
    public Destination destination = Destination.CLIPBOARD;
    public FormattingMode formatting = FormattingMode.ASK;
    /** Absolute path of the silent-save folder; empty = ask with a file dialog. */
    public String saveFolder = "";
    /** Default state of the "page break markers" option when copying out. */
    public boolean pageMarkers = false;
    /** Extra per-page byte cap for strict servers; 0 = the safe default (2048 bytes). */
    public int pageByteLimit = 0;
    /** Default multivolume handling: AUTO hands out/fills books, MANUAL uses the selector. */
    public MultivolumeMethod multivolumeMethod = MultivolumeMethod.AUTO;
    /** When AUTO, sign each volume with an auto-numbered title instead of leaving it highlighted. */
    public boolean autoNameVolumes = false;
    /** Pause between handed-out books in creative, milliseconds. */
    public int creativeDelayMs = DEFAULT_CREATIVE_DELAY_MS;
    /** Pause between filled books in survival, milliseconds (servers kick on fast book edits). */
    public int survivalDelayMs = DEFAULT_SURVIVAL_DELAY_MS;
    /** Hide the manual volume selector once every volume has been inserted. */
    public boolean autoHideVolumeSelector = true;
    /** Ctrl+V in the book editor paste-with-overflow (off = vanilla single-page paste). */
    public boolean smartPaste = true;
    /** Safe default: ask before an edited draft is closed without pressing Done. */
    public CloseBehavior closeBehavior = CloseBehavior.ASK;
    /** Safe default: checkbox confirmation plus a short delay before erasing all pages. */
    public EraseBehavior eraseBehavior = EraseBehavior.ASK_DELAY;

    public static final int DEFAULT_CREATIVE_DELAY_MS = 100;
    public static final int DEFAULT_SURVIVAL_DELAY_MS = 1300;
    public static final int MAX_DELAY_MS = 60000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config load(Path file) {
        Config config = new Config();
        try {
            if (Files.isRegularFile(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                if (obj != null) {
                    config.language = readEnum(obj, "language", LangMode.class, config.language);
                    config.destination = readEnum(obj, "destination", Destination.class, config.destination);
                    config.formatting = readEnum(obj, "formatting", FormattingMode.class, config.formatting);
                    config.multivolumeMethod =
                            readEnum(obj, "multivolumeMethod", MultivolumeMethod.class, config.multivolumeMethod);
                    config.closeBehavior =
                            readEnum(obj, "closeBehavior", CloseBehavior.class, config.closeBehavior);
                    config.eraseBehavior =
                            readEnum(obj, "eraseBehavior", EraseBehavior.class, config.eraseBehavior);
                    if (obj.has("autoNameVolumes") && obj.get("autoNameVolumes").isJsonPrimitive()) {
                        config.autoNameVolumes = obj.get("autoNameVolumes").getAsBoolean();
                    }
                    if (obj.has("saveFolder") && obj.get("saveFolder").isJsonPrimitive()) {
                        config.saveFolder = obj.get("saveFolder").getAsString();
                    }
                    if (obj.has("pageMarkers") && obj.get("pageMarkers").isJsonPrimitive()) {
                        config.pageMarkers = obj.get("pageMarkers").getAsBoolean();
                    }
                    if (obj.has("pageByteLimit") && obj.get("pageByteLimit").isJsonPrimitive()) {
                        config.pageByteLimit = Math.max(0, obj.get("pageByteLimit").getAsInt());
                    }
                    if (obj.has("creativeDelayMs") && obj.get("creativeDelayMs").isJsonPrimitive()) {
                        config.creativeDelayMs = clampDelay(obj.get("creativeDelayMs").getAsInt());
                    }
                    if (obj.has("survivalDelayMs") && obj.get("survivalDelayMs").isJsonPrimitive()) {
                        config.survivalDelayMs = clampDelay(obj.get("survivalDelayMs").getAsInt());
                    }
                    if (obj.has("autoHideVolumeSelector") && obj.get("autoHideVolumeSelector").isJsonPrimitive()) {
                        config.autoHideVolumeSelector = obj.get("autoHideVolumeSelector").getAsBoolean();
                    }
                    if (obj.has("smartPaste") && obj.get("smartPaste").isJsonPrimitive()) {
                        config.smartPaste = obj.get("smartPaste").getAsBoolean();
                    }
                }
            }
        } catch (Exception ignored) {
            // Unreadable/corrupt config falls back to defaults; saving will rewrite it.
        }
        return config;
    }

    public void save(Path file) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("language", language.name().toLowerCase(Locale.ROOT));
        obj.addProperty("destination", destination.name().toLowerCase(Locale.ROOT));
        obj.addProperty("formatting", formatting.name().toLowerCase(Locale.ROOT));
        obj.addProperty("multivolumeMethod", multivolumeMethod.name().toLowerCase(Locale.ROOT));
        obj.addProperty("autoNameVolumes", autoNameVolumes);
        obj.addProperty("saveFolder", saveFolder);
        obj.addProperty("pageMarkers", pageMarkers);
        obj.addProperty("pageByteLimit", pageByteLimit);
        obj.addProperty("creativeDelayMs", creativeDelayMs);
        obj.addProperty("survivalDelayMs", survivalDelayMs);
        obj.addProperty("autoHideVolumeSelector", autoHideVolumeSelector);
        obj.addProperty("smartPaste", smartPaste);
        obj.addProperty("closeBehavior", closeBehavior.name().toLowerCase(Locale.ROOT));
        obj.addProperty("eraseBehavior", eraseBehavior.name().toLowerCase(Locale.ROOT));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
    }

    public static int clampDelay(int ms) {
        return Math.max(0, Math.min(MAX_DELAY_MS, ms));
    }

    private static <E extends Enum<E>> E readEnum(JsonObject obj, String key, Class<E> type, E fallback) {
        try {
            if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                return Enum.valueOf(type, obj.get(key).getAsString().trim().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
        }
        return fallback;
    }
}
