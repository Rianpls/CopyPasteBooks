package io.github.rianpls.copypastebooks.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The mod's own tiny translator. Bundles live at
 * {@code assets/copypastebooks/i18n/<lang>.json} inside the jar; the active language is
 * decided elsewhere (config override or game language). English is the fallback.
 */
public final class I18n {
    private static final Map<String, Map<String, String>> BUNDLES = new HashMap<>();

    private I18n() {
    }

    public static synchronized void bootstrap() {
        if (BUNDLES.isEmpty()) {
            BUNDLES.put("en", loadBundle("en"));
            BUNDLES.put("ru", loadBundle("ru"));
        }
    }

    public static String tr(String lang, String key, Object... args) {
        bootstrap();
        String pattern = BUNDLES.getOrDefault(lang, Map.of()).get(key);
        if (pattern == null) {
            pattern = BUNDLES.getOrDefault("en", Map.of()).get(key);
        }
        if (pattern == null) {
            return key;
        }
        if (args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (RuntimeException e) {
            return pattern;
        }
    }

    private static Map<String, String> loadBundle(String lang) {
        Map<String, String> map = new HashMap<>();
        String path = "/assets/copypastebooks/i18n/" + lang + ".json";
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject obj = new Gson().fromJson(
                        new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception ignored) {
            // A broken bundle degrades to raw keys rather than crashing the client.
        }
        return map;
    }
}
