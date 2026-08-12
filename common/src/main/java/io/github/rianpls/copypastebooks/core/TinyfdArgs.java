package io.github.rianpls.copypastebooks.core;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class TinyfdArgs {
    private static final int MAX_DISPLAY_BYTES = 256;
    private static final int MAX_PATH_BYTES = 512;

    private TinyfdArgs() {
    }

    public static String display(String value, boolean shellBacked) {
        return display(value, shellBacked, true);
    }

    public static String pathDisplay(String value, boolean shellBacked) {
        String shown = display(value, shellBacked, true);
        return pathOrNull(shown) != null ? shown : display(value, shellBacked, false);
    }

    private static String display(String value, boolean shellBacked, boolean unicode) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(value.length(), MAX_DISPLAY_BYTES));
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                codePoint = ' ';
            } else if (codePoint == '\'') {
                codePoint = unicode ? '\u2019' : '_';
            } else if (codePoint == '"') {
                codePoint = unicode ? '\u201D' : '_';
            } else if (codePoint == '`') {
                codePoint = unicode ? '\u02CB' : '_';
            } else if (shellBacked && codePoint == '$') {
                codePoint = unicode ? '\uFF04' : '_';
            } else if (shellBacked && codePoint == '\\') {
                codePoint = unicode ? '\uFF3C' : '_';
            } else if (!unicode && codePoint > 0x7F) {
                codePoint = '_';
            }
            if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                codePoint = 0xFFFD;
            }
            int codePointBytes = utf8Bytes(codePoint);
            if (bytes + codePointBytes > MAX_DISPLAY_BYTES) {
                break;
            }
            safe.appendCodePoint(codePoint);
            bytes += codePointBytes;
        }
        return safe.toString();
    }

    public static String startPath(String value, boolean shellBacked) {
        if (value == null || utf8Bytes(value) > MAX_PATH_BYTES || pathOrNull(value) == null) {
            return "";
        }
        boolean unsafe = value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                || codePoint == '\''
                || codePoint == '"'
                || codePoint == '`'
                || shellBacked && (codePoint == '$' || codePoint == '\\'));
        return unsafe ? "" : value;
    }

    public static Path pathOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Path restoreSuggestedName(Path picked, String shownName, String suggestedName) {
        Path pickedName = picked.getFileName();
        if (pickedName == null || !pickedName.toString().equals(shownName)
                || suggestedName == null || suggestedName.isBlank()
                || suggestedName.equals(".") || suggestedName.equals("..")
                || suggestedName.indexOf('/') >= 0 || suggestedName.indexOf('\\') >= 0
                || suggestedName.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                        || "<>:\"|?*".indexOf(codePoint) >= 0)) {
            return picked;
        }
        try {
            Path target = picked.resolveSibling(suggestedName);
            if (target.equals(picked)) {
                return picked;
            }
            return Files.notExists(picked, LinkOption.NOFOLLOW_LINKS)
                    && Files.notExists(target, LinkOption.NOFOLLOW_LINKS) ? target : picked;
        } catch (RuntimeException ignored) {
            return picked;
        }
    }

    private static int utf8Bytes(String value) {
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            bytes += utf8Bytes(codePoint);
        }
        return bytes;
    }

    private static int utf8Bytes(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        return codePoint <= 0xFFFF ? 3 : 4;
    }
}
