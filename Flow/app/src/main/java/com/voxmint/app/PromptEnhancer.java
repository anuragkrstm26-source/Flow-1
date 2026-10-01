package com.voxmint.app;

import java.util.Locale;
import java.util.regex.Pattern;

/** Small offline enhancer: deterministic, private, and safe when no network is available. */
public final class PromptEnhancer {
    private static final Pattern FILLERS = Pattern.compile("\\b(um+|uh+|erm|you know|basically|like)\\b", Pattern.CASE_INSENSITIVE);
    private PromptEnhancer() {}

    public static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        value = FILLERS.matcher(value).replaceAll(" ");
        value = value.replaceAll("\\s+", " ").trim();
        return value;
    }

    public static String enhance(String raw, boolean enabled) {
        String text = clean(raw);
        if (text.length() < 3) return text;
        if (!enabled) return sentenceCase(text);

        String body = sentenceCase(text);
        String lower = body.toLowerCase(Locale.US);
        String opener;
        if (lower.startsWith("create ") || lower.startsWith("design ") || lower.startsWith("write ") || lower.startsWith("build ")) {
            opener = body;
        } else if (lower.startsWith("make ")) {
            opener = "Create " + body.substring(5);
        } else {
            opener = "Create a clear result based on this request: " + body;
        }
        if (!opener.endsWith(".")) opener += ".";
        return opener + " Preserve every named subject, number, dimension, technical term, and specific constraint. Use clear structure, accurate wording, and an outcome-focused presentation.";
    }

    private static String sentenceCase(String text) {
        if (text.isEmpty()) return text;
        String result = Character.toUpperCase(text.charAt(0)) + text.substring(1);
        return result.endsWith(".") || result.endsWith("!") || result.endsWith("?") ? result : result + ".";
    }
}
