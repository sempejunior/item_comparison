package com.hackerrank.sample.model;

import com.hackerrank.sample.exception.InvalidLanguageException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageTest {

    @Test
    void fromTag_parsesCanonicalTags() {
        assertThat(Language.fromTag("pt-BR")).isEqualTo(Language.PT_BR);
        assertThat(Language.fromTag("en")).isEqualTo(Language.EN);
    }

    @Test
    void fromTag_isCaseInsensitiveAndTrims() {
        assertThat(Language.fromTag(" PT-br ")).isEqualTo(Language.PT_BR);
        assertThat(Language.fromTag("EN")).isEqualTo(Language.EN);
    }

    @Test
    void fromTag_roundTripsThroughTag() {
        for (Language language : Language.values()) {
            assertThat(Language.fromTag(language.tag())).isEqualTo(language);
        }
    }

    @Test
    void fromTag_rejectsUnknownTag() {
        assertThatThrownBy(() -> Language.fromTag("fr-FR"))
                .isInstanceOf(InvalidLanguageException.class)
                .hasMessageContaining("fr-FR");
    }

    @Test
    void fromTag_rejectsNull() {
        assertThatThrownBy(() -> Language.fromTag(null))
                .isInstanceOf(InvalidLanguageException.class);
    }
}
