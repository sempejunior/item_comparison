package com.hackerrank.sample.model;

import java.util.Locale;

/**
 * Language tag accepted by the compare endpoint summary feature.
 *
 * The wire format follows BCP 47 short tags: {@code pt-BR} and {@code en}.
 * Internal representation uses underscore (PT_BR) so the enum literals stay
 * Java-legal. Parsing is performed via {@link #fromTag(String)}; unknown
 * tags raise {@link IllegalArgumentException} until T-15 wires the
 * dedicated {@code InvalidLanguageException} into the global advice.
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
            throw new IllegalArgumentException("language tag must not be null");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.tag.toLowerCase(Locale.ROOT).equals(normalized)) {
                return language;
            }
        }
        throw new IllegalArgumentException("unsupported language tag: " + raw);
    }
}
