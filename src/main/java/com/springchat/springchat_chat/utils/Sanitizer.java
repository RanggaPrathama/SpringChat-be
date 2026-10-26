package com.springchat.springchat_chat.utils;

import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/** Shared request text sanitization and value normalization. */
public final class Sanitizer {
    private static final Cleaner PLAIN_TEXT_CLEANER = new Cleaner(Safelist.none());

    private Sanitizer() {}

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public static String normalize(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /** Removes HTML markup and active element contents while retaining the remaining text. */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        Document parsed = Jsoup.parseBodyFragment(value);
        parsed.select("script, style, iframe, object, embed, svg, math, template, noscript").remove();
        return PLAIN_TEXT_CLEANER.clean(parsed).body().wholeText();
    }
}
