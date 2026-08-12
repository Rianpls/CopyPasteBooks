package io.github.rianpls.copypastebooks.core;

import java.util.regex.Pattern;

/**
 * Legacy §-code helpers. "Effective" codes are §-sequences the vanilla renderer would
 * actually interpret (§ + 0-9 a-f k-o r); a lone § or § before any other character is
 * plain text and is left untouched everywhere in this mod.
 */
public final class LegacyText {
    public static final char SECTION = '§';
    private static final Pattern EFFECTIVE = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private LegacyText() {
    }

    public static boolean hasEffectiveCodes(String s) {
        return EFFECTIVE.matcher(s).find();
    }

    public static String strip(String s) {
        return EFFECTIVE.matcher(s).replaceAll("");
    }

    /** Mutable legacy style state used while serializing styled text to §-codes. */
    public static final class StyleState {
        /** legacy color code char ('0'-'9','a'-'f'), or 0 for "no explicit color". */
        public char color;
        public boolean bold;
        public boolean italic;
        public boolean underline;
        public boolean strikethrough;
        public boolean obfuscated;

        public boolean isPlain() {
            return color == 0 && !bold && !italic && !underline && !strikethrough && !obfuscated;
        }

        public StyleState copy() {
            StyleState c = new StyleState();
            c.color = color;
            c.bold = bold;
            c.italic = italic;
            c.underline = underline;
            c.strikethrough = strikethrough;
            c.obfuscated = obfuscated;
            return c;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StyleState s
                    && s.color == color && s.bold == bold && s.italic == italic
                    && s.underline == underline && s.strikethrough == strikethrough
                    && s.obfuscated == obfuscated;
        }

        @Override
        public int hashCode() {
            return (color << 5) ^ (bold ? 1 : 0) ^ (italic ? 2 : 0) ^ (underline ? 4 : 0)
                    ^ (strikethrough ? 8 : 0) ^ (obfuscated ? 16 : 0);
        }
    }

    /**
     * Emits the minimal §-code sequence that turns {@code from} into {@code to},
     * honoring legacy render semantics: a color code clears active styles, §r clears
     * everything.
     */
    public static String transition(StyleState from, StyleState to) {
        if (from.equals(to)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean stylesRemoved = (from.bold && !to.bold)
                || (from.italic && !to.italic)
                || (from.underline && !to.underline)
                || (from.strikethrough && !to.strikethrough)
                || (from.obfuscated && !to.obfuscated);
        boolean colorChanged = from.color != to.color;

        if (colorChanged || stylesRemoved) {
            // A color code resets styles; when the target has no color, §r does the reset.
            if (to.color != 0) {
                out.append(SECTION).append(to.color);
            } else {
                out.append(SECTION).append('r');
            }
            appendStyles(out, to, new StyleState());
        } else {
            appendStyles(out, to, from);
        }
        return out.toString();
    }

    private static void appendStyles(StringBuilder out, StyleState to, StyleState base) {
        if (to.obfuscated && !base.obfuscated) {
            out.append(SECTION).append('k');
        }
        if (to.bold && !base.bold) {
            out.append(SECTION).append('l');
        }
        if (to.strikethrough && !base.strikethrough) {
            out.append(SECTION).append('m');
        }
        if (to.underline && !base.underline) {
            out.append(SECTION).append('n');
        }
        if (to.italic && !base.italic) {
            out.append(SECTION).append('o');
        }
    }
}
