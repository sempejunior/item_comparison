package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.DifferenceEntry;
import com.hackerrank.sample.model.ProductDetail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure differences calculator. Given the resolved products and a parsed
 * {@link FieldSet}, returns the list of {@link DifferenceEntry} for paths
 * that differ across products. Order: comparable entries first (sorted
 * by relative spread descending), then non-comparable entries by path.
 */
public final class DifferencesCalculator {

    private DifferencesCalculator() {
    }

    public static DiffResult compute(List<ProductDetail> products, FieldSet fields) {
        return compute(products, fields, AttributeMetadata.defaultRegistry());
    }

    public static DiffResult compute(List<ProductDetail> products, FieldSet fields, AttributeMetadata metadata) {
        if (products == null || products.size() < 2) {
            return new DiffResult(List.of(), Map.of());
        }
        Set<String> intersection = intersectAttributeKeys(products);
        Map<Long, List<String>> exclusiveAttributes = exclusivesPerProduct(products, intersection);
        List<String> candidates = candidatePaths(fields, intersection);

        List<DifferenceEntry> comparable = new ArrayList<>();
        List<DifferenceEntry> nonComparable = new ArrayList<>();

        for (String path : candidates) {
            Map<Long, Object> values = gatherValues(path, products);
            if (allEqual(values)) {
                continue;
            }
            DiffOutcome outcome = computeOutcome(path, products, values, metadata);
            DifferenceEntry entry = new DifferenceEntry(
                    path, outcome.comparable(), outcome.winnerId(), values);
            if (outcome.comparable()) {
                comparable.add(entry);
            } else {
                nonComparable.add(entry);
            }
        }
        comparable.sort(Comparator
                .comparingDouble(DifferencesCalculator::spreadKey).reversed()
                .thenComparing(DifferenceEntry::path));
        nonComparable.sort(Comparator.comparing(DifferenceEntry::path));

        List<DifferenceEntry> all = new ArrayList<>(comparable.size() + nonComparable.size());
        all.addAll(comparable);
        all.addAll(nonComparable);
        return new DiffResult(all, exclusiveAttributes);
    }

    private static List<String> candidatePaths(FieldSet fields, Set<String> intersection) {
        List<String> out = new ArrayList<>();
        if (fields.wantsTopLevel("rating")) {
            out.add("rating");
        }
        if (fields.wantsBuyBoxField("price")) {
            out.add("buyBox.price");
        }
        if (fields.wantsAttributes()) {
            Set<String> picked = fields.attributeKeys().isEmpty() ? intersection : fields.attributeKeys();
            List<String> sorted = new ArrayList<>(picked);
            sorted.sort(Comparator.naturalOrder());
            for (String key : sorted) {
                out.add("attributes." + key);
            }
        }
        return out;
    }

    private static Map<Long, Object> gatherValues(String path, List<ProductDetail> products) {
        Map<Long, Object> values = new LinkedHashMap<>();
        for (ProductDetail p : products) {
            values.put(p.id(), readPath(path, p));
        }
        return values;
    }

    private static Object readPath(String path, ProductDetail p) {
        if ("rating".equals(path)) {
            return p.rating();
        }
        if ("buyBox.price".equals(path)) {
            BuyBox b = p.buyBox();
            return b == null ? null : b.price();
        }
        if (path.startsWith("attributes.")) {
            String key = path.substring("attributes.".length());
            return p.attributes() == null ? null : p.attributes().get(key);
        }
        return null;
    }

    private static boolean allEqual(Map<Long, Object> values) {
        Object reference = null;
        boolean first = true;
        for (Object v : values.values()) {
            if (first) {
                reference = v;
                first = false;
            } else if (!Objects.equals(reference, v)) {
                return false;
            }
        }
        return true;
    }

    private static DiffOutcome computeOutcome(
            String path, List<ProductDetail> products, Map<Long, Object> values, AttributeMetadata metadata) {
        boolean anyNull = values.values().stream().anyMatch(Objects::isNull);
        if (anyNull) {
            return new DiffOutcome(false, null);
        }
        if ("buyBox.price".equals(path)) {
            return priceOutcome(products, values);
        }
        if ("rating".equals(path)) {
            return numericOutcome(values, true);
        }
        if (path.startsWith("attributes.")) {
            String key = path.substring("attributes.".length());
            return switch (metadata.directionFor(key)) {
                case HIGHER_BETTER -> numericOutcome(values, true);
                case LOWER_BETTER -> numericOutcome(values, false);
                case NONE -> new DiffOutcome(false, null);
            };
        }
        return new DiffOutcome(false, null);
    }

    private static DiffOutcome priceOutcome(List<ProductDetail> products, Map<Long, Object> values) {
        String currency = null;
        for (ProductDetail p : products) {
            if (p.buyBox() == null) {
                return new DiffOutcome(false, null);
            }
            String c = p.buyBox().currency();
            if (currency == null) {
                currency = c;
            } else if (!currency.equals(c)) {
                return new DiffOutcome(false, null);
            }
        }
        Long winner = null;
        BigDecimal best = null;
        for (Map.Entry<Long, Object> e : values.entrySet()) {
            BigDecimal v = (BigDecimal) e.getValue();
            if (best == null || v.compareTo(best) < 0) {
                best = v;
                winner = e.getKey();
            }
        }
        return new DiffOutcome(true, winner);
    }

    private static DiffOutcome numericOutcome(Map<Long, Object> values, boolean higherBetter) {
        Map<Long, NumericValue> parsed = new LinkedHashMap<>();
        String unit = null;
        for (Map.Entry<Long, Object> e : values.entrySet()) {
            NumericValue nv = NumericValue.parse(e.getValue());
            if (nv == null) {
                return new DiffOutcome(false, null);
            }
            if (unit == null) {
                unit = nv.unit();
            } else if (!unit.equalsIgnoreCase(nv.unit())) {
                return new DiffOutcome(false, null);
            }
            parsed.put(e.getKey(), nv);
        }
        Long winner = null;
        BigDecimal best = null;
        for (Map.Entry<Long, NumericValue> e : parsed.entrySet()) {
            BigDecimal v = e.getValue().magnitude();
            if (best == null
                    || (higherBetter && v.compareTo(best) > 0)
                    || (!higherBetter && v.compareTo(best) < 0)) {
                best = v;
                winner = e.getKey();
            }
        }
        return new DiffOutcome(true, winner);
    }

    private static double spreadKey(DifferenceEntry entry) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Object v : entry.values().values()) {
            NumericValue nv = NumericValue.parse(v);
            if (nv == null) {
                return 0d;
            }
            double d = nv.magnitude().doubleValue();
            if (d < min) min = d;
            if (d > max) max = d;
        }
        if (min == 0d || min == Double.POSITIVE_INFINITY) {
            return max - min;
        }
        return (max - min) / Math.abs(min);
    }

    private static Set<String> intersectAttributeKeys(List<ProductDetail> products) {
        Set<String> intersection = null;
        for (ProductDetail p : products) {
            Set<String> own = p.attributes() == null ? Set.of() : p.attributes().keySet();
            if (intersection == null) {
                intersection = new TreeSet<>(own);
            } else {
                intersection.retainAll(own);
            }
        }
        return intersection == null ? new LinkedHashSet<>() : new LinkedHashSet<>(intersection);
    }

    private static Map<Long, List<String>> exclusivesPerProduct(List<ProductDetail> products, Set<String> intersection) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        for (ProductDetail p : products) {
            if (p.attributes() == null || p.attributes().isEmpty()) {
                continue;
            }
            List<String> exclusives = new ArrayList<>();
            for (String key : p.attributes().keySet()) {
                if (!intersection.contains(key)) {
                    exclusives.add(key);
                }
            }
            if (!exclusives.isEmpty()) {
                exclusives.sort(Comparator.naturalOrder());
                out.put(p.id(), List.copyOf(exclusives));
            }
        }
        return out;
    }

    private record DiffOutcome(boolean comparable, Long winnerId) {
    }

    public record DiffResult(List<DifferenceEntry> differences, Map<Long, List<String>> exclusiveAttributes) {
    }
}
