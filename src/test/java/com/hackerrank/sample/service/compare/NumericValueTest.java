package com.hackerrank.sample.service.compare;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NumericValueTest {

    @Test
    void parsesIntegerWithUnit() {
        NumericValue v = NumericValue.parse("4000 mAh");
        assertThat(v).isNotNull();
        assertThat(v.magnitude()).isEqualByComparingTo("4000");
        assertThat(v.unit()).isEqualTo("mah");
    }

    @Test
    void parsesDecimalWithUnit() {
        NumericValue v = NumericValue.parse("6.2 in");
        assertThat(v.magnitude()).isEqualByComparingTo("6.2");
        assertThat(v.unit()).isEqualTo("in");
    }

    @Test
    void parsesNumberWithoutUnit() {
        NumericValue v = NumericValue.parse(4.5);
        assertThat(v.magnitude()).isEqualByComparingTo("4.5");
        assertThat(v.unit()).isEmpty();
    }

    @Test
    void rejectsNonNumericString() {
        assertThat(NumericValue.parse("Apple")).isNull();
    }

    @Test
    void rejectsNull() {
        assertThat(NumericValue.parse(null)).isNull();
    }

    @Test
    void unitMatchesIsCaseInsensitive() {
        NumericValue a = NumericValue.parse("8 GB");
        NumericValue b = NumericValue.parse("16 gb");
        assertThat(a.unitMatches(b)).isTrue();
    }
}
