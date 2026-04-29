package com.hackerrank.sample.service.compare;

import java.util.List;
import java.util.Set;

/**
 * Parsed sparse-fieldset request. {@code explicit=false} means the caller
 * did not pass {@code ?fields=}; defaults apply. {@code explicit=true} means
 * only the listed paths are projected and considered for diffing.
 */
public record FieldSet(
        boolean explicit,
        Set<String> topLevel,
        boolean wantsBuyBox,
        Set<String> buyBoxFields,
        boolean wantsAttributes,
        Set<String> attributeKeys,
        boolean wantsOffers,
        List<String> rawPaths) {

    public static FieldSet defaultSet() {
        return new FieldSet(
                false,
                Set.of("name", "imageUrl", "rating", "category"),
                true, Set.of(),
                true, Set.of(),
                false,
                null);
    }

    public boolean wantsTopLevel(String key) {
        return topLevel.contains(key);
    }

    public boolean wantsBuyBoxField(String field) {
        return wantsBuyBox && (buyBoxFields.isEmpty() || buyBoxFields.contains(field));
    }

    public boolean wantsAttributeKey(String key) {
        return wantsAttributes && (attributeKeys.isEmpty() || attributeKeys.contains(key));
    }
}
