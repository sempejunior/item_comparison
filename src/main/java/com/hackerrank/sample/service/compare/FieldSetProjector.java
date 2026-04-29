package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.exception.InvalidFieldsException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.ProductDetail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses the {@code ?fields=} query and projects {@link ProductDetail}
 * into a sparse {@link CompareItem}. Pure / stateless.
 */
public final class FieldSetProjector {

    private static final Set<String> ALLOWED_TOP = Set.of(
            "name", "description", "imageUrl", "rating", "category");
    private static final Set<String> ALLOWED_BUYBOX = Set.of(
            "id", "sellerId", "sellerName", "sellerReputation",
            "price", "currency", "condition", "freeShipping", "stock");
    private static final Pattern SEGMENT = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private FieldSetProjector() {
    }

    public static FieldSet parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return FieldSet.defaultSet();
        }
        Set<String> topLevel = new LinkedHashSet<>();
        Set<String> buyBoxFields = new LinkedHashSet<>();
        Set<String> attributeKeys = new LinkedHashSet<>();
        boolean wantsBuyBox = false;
        boolean wantsAttributes = false;
        boolean wantsOffers = false;
        List<String> raw = new java.util.ArrayList<>();

        for (String token : csv.split(",")) {
            String path = token.trim();
            if (path.isEmpty()) {
                continue;
            }
            raw.add(path);
            String[] parts = path.split("\\.", -1);
            if (parts.length == 0 || parts.length > 2) {
                throw new InvalidFieldsException("invalid fields path: " + path);
            }
            for (String segment : parts) {
                if (!SEGMENT.matcher(segment).matches()) {
                    throw new InvalidFieldsException("invalid fields path: " + path);
                }
            }
            String head = parts[0];
            if ("offers".equals(head)) {
                if (parts.length != 1) {
                    throw new InvalidFieldsException("offers does not accept sub-paths: " + path);
                }
                wantsOffers = true;
            } else if ("buyBox".equals(head)) {
                wantsBuyBox = true;
                if (parts.length == 2) {
                    if (!ALLOWED_BUYBOX.contains(parts[1])) {
                        throw new InvalidFieldsException("unknown buyBox field: " + parts[1]);
                    }
                    buyBoxFields.add(parts[1]);
                } else {
                    buyBoxFields.clear();
                }
            } else if ("attributes".equals(head)) {
                wantsAttributes = true;
                if (parts.length == 2) {
                    attributeKeys.add(parts[1]);
                } else {
                    attributeKeys.clear();
                }
            } else if (ALLOWED_TOP.contains(head)) {
                if (parts.length != 1) {
                    throw new InvalidFieldsException("path does not accept sub-paths: " + path);
                }
                topLevel.add(head);
            } else if ("id".equals(head)) {
                if (parts.length != 1) {
                    throw new InvalidFieldsException("id is implicit: " + path);
                }
            } else {
                throw new InvalidFieldsException("unknown top-level path: " + head);
            }
        }
        return new FieldSet(
                true,
                Set.copyOf(topLevel),
                wantsBuyBox,
                wantsBuyBox && buyBoxFields.isEmpty() ? Set.of() : Set.copyOf(buyBoxFields),
                wantsAttributes,
                wantsAttributes && attributeKeys.isEmpty() ? Set.of() : Set.copyOf(attributeKeys),
                wantsOffers,
                List.copyOf(raw));
    }

    public static CompareItem project(ProductDetail product, FieldSet fields) {
        return new CompareItem(
                product.id(),
                fields.wantsTopLevel("name") ? product.name() : null,
                fields.wantsTopLevel("description") ? product.description() : null,
                fields.wantsTopLevel("imageUrl") ? product.imageUrl() : null,
                fields.wantsTopLevel("rating") ? product.rating() : null,
                fields.wantsTopLevel("category") ? product.category() : null,
                projectAttributes(product.attributes(), fields),
                fields.wantsOffers() ? null : projectBuyBox(product.buyBox(), fields),
                fields.wantsOffers() ? product.offers() : null);
    }

    private static Map<String, Object> projectAttributes(Map<String, Object> source, FieldSet fields) {
        if (!fields.wantsAttributes() || source == null) {
            return null;
        }
        if (fields.attributeKeys().isEmpty()) {
            return source.isEmpty() ? null : source;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : fields.attributeKeys()) {
            if (source.containsKey(key)) {
                out.put(key, source.get(key));
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static BuyBox projectBuyBox(BuyBox source, FieldSet fields) {
        if (!fields.wantsBuyBox() || source == null) {
            return null;
        }
        if (fields.buyBoxFields().isEmpty()) {
            return source;
        }
        return new BuyBox(
                fields.wantsBuyBoxField("id") ? source.id() : null,
                fields.wantsBuyBoxField("sellerId") ? source.sellerId() : null,
                fields.wantsBuyBoxField("sellerName") ? source.sellerName() : null,
                fields.wantsBuyBoxField("sellerReputation") ? source.sellerReputation() : null,
                fields.wantsBuyBoxField("price") ? source.price() : null,
                fields.wantsBuyBoxField("currency") ? source.currency() : null,
                fields.wantsBuyBoxField("condition") ? source.condition() : null,
                fields.wantsBuyBoxField("freeShipping") ? source.freeShipping() : null,
                fields.wantsBuyBoxField("stock") ? source.stock() : null);
    }

    static Set<String> allowedTopLevel() {
        return ALLOWED_TOP;
    }

    static Set<String> allowedBuyBoxFields() {
        return ALLOWED_BUYBOX;
    }

    @SuppressWarnings("unused")
    private static List<String> dummy(String[] parts) {
        return Arrays.asList(parts);
    }
}
