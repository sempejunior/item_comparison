package com.hackerrank.sample.model;

import com.hackerrank.sample.exception.InvalidLanguageException;

import java.util.Locale;

/**
 * Language tag accepted by the compare endpoint summary feature.
 *
 * Wire format follows BCP 47 short tags: {@code pt-BR} and {@code en}.
 * Internal enum literals use underscore (PT_BR) so they stay Java-legal.
 * Parsing is performed via {@link #fromTag(String)}; unknown or null
 * tags raise {@link InvalidLanguageException}.
 */
public enum Language {
    PT_BR("pt-BR"),
    EN("en");

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public static Language fromTag(String raw) {
        if (raw == null) {
            throw new InvalidLanguageException("language tag must not be null");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.tag.toLowerCase(Locale.ROOT).equals(normalized)) {
                return language;
            }
        }
        throw new InvalidLanguageException("unsupported language tag: " + raw);
    }
}
