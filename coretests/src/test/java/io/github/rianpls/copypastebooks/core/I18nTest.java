package io.github.rianpls.copypastebooks.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class I18nTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");

    @Test
    void bundlesContainTheSameKeysAndPlaceholders() throws Exception {
        JsonObject en = bundle("en");
        JsonObject ru = bundle("ru");

        assertFalse(en.isEmpty());
        assertEquals(en.keySet(), ru.keySet());
        for (Map.Entry<String, JsonElement> entry : en.entrySet()) {
            String key = entry.getKey();
            assertEquals(placeholders(entry.getValue().getAsString()),
                    placeholders(ru.get(key).getAsString()), key);
        }
    }

    @Test
    void translatorLoadsBothBundles() {
        assertEquals("Done", I18n.tr("en", "settings.done"));
        assertEquals("Готово", I18n.tr("ru", "settings.done"));
        assertEquals("Done", I18n.tr("pl", "settings.done"));
        assertEquals("missing", I18n.tr("en", "missing", 7));
    }

    private static JsonObject bundle(String language) throws Exception {
        String path = "/assets/copypastebooks/i18n/" + language + ".json";
        try (InputStream stream = I18nTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    private static List<String> placeholders(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }
}
