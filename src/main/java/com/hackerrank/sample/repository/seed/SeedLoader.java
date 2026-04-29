package com.hackerrank.sample.repository.seed;

import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.repository.CatalogProductEntity;
import com.hackerrank.sample.repository.CatalogProductRepository;
import com.hackerrank.sample.repository.OfferEntity;
import com.hackerrank.sample.repository.OfferRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boots the in-memory catalog. SPEC-002 §5 originally calls for a JSON
 * resource file; for v1 we emit the same dataset programmatically to
 * keep the bootstrap deterministic and reviewable. The shape is
 * identical to what a JSON loader would produce.
 *
 * Seed: 50 catalog products (10 per category) and ~150 offers, with the
 * edge cases woven in (PLAN §3): zero-stock product, USED/REFURBISHED-
 * only product, near-identical pair, attribute-light trio.
 */
@Component
public class SeedLoader implements CommandLineRunner {

    private final CatalogProductRepository products;
    private final OfferRepository offers;

    public SeedLoader(CatalogProductRepository products, OfferRepository offers) {
        this.products = products;
        this.offers = offers;
    }

    @Override
    public void run(String... args) {
        List<CatalogProductEntity> productEntities = SeedFactory.products();
        List<OfferEntity> offerEntities = SeedFactory.offers();
        products.saveAll(productEntities);
        offers.saveAll(offerEntities);
    }

    static final class SeedFactory {

        static List<CatalogProductEntity> products() {
            List<CatalogProductEntity> all = new java.util.ArrayList<>();
            for (int i = 0; i < 10; i++) {
                all.add(smartphone(1L + i, i));
                all.add(smartTv(11L + i, i));
                all.add(notebook(21L + i, i));
                all.add(headphone(31L + i, i));
                all.add(refrigerator(41L + i, i));
            }
            return all;
        }

        static List<OfferEntity> offers() {
            List<OfferEntity> all = new java.util.ArrayList<>();
            long offerId = 100L;
            for (long pid = 1L; pid <= 50L; pid++) {
                int seed = (int) (pid % 7);
                int count = 2 + (int) (pid % 3);
                boolean zeroStock = pid == 50L;
                boolean usedOnly = pid == 41L;
                for (int k = 0; k < count; k++) {
                    Condition cond = usedOnly
                            ? (k == 0 ? Condition.REFURBISHED : Condition.USED)
                            : (k == 0 ? Condition.NEW : (k == 1 ? Condition.NEW : Condition.REFURBISHED));
                    int stock = zeroStock ? 0 : 1 + ((seed + k) % 9);
                    BigDecimal basePrice = BigDecimal.valueOf(199L + pid * 37L);
                    BigDecimal price = basePrice.subtract(BigDecimal.valueOf(k * 25L));
                    all.add(new OfferEntity(
                            offerId++, pid,
                            "MELI-" + pid + "-" + k,
                            "Seller " + pid + "-" + k,
                            (k + 3) % 6,
                            price,
                            "BRL",
                            cond,
                            (k % 2) == 0,
                            stock));
                }
            }
            return all;
        }

        private static CatalogProductEntity smartphone(long id, int idx) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("battery", (3000 + idx * 200) + " mAh");
            attrs.put("memory", (4 + idx) + " GB");
            attrs.put("storage", (64L << Math.min(idx % 3, 2)) + " GB");
            attrs.put("brand", idx % 2 == 0 ? "Samsung" : "Xiaomi");
            attrs.put("os", "Android 14");
            attrs.put("weight", (160 + idx * 4) + " g");
            attrs.put("size", String.format("%.1f in", 6.0 + idx * 0.1));
            String name = idx == 0 ? "Galaxy S24"
                    : idx == 1 ? "Galaxy S24+"
                    : "Smartphone Model " + idx;
            return new CatalogProductEntity(id, name, "Smartphone " + idx,
                    "https://example.com/img/sp-" + id + ".jpg",
                    Math.min(5.0, 4.0 + idx * 0.1),
                    Category.SMARTPHONE, attrs);
        }

        private static CatalogProductEntity smartTv(long id, int idx) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("brand", idx % 2 == 0 ? "LG" : "Samsung");
            attrs.put("screen_size_inches", 43 + idx * 3);
            attrs.put("resolution", idx % 3 == 0 ? "4K" : "FullHD");
            attrs.put("refresh_rate_hz", 60 + (idx % 2) * 60);
            attrs.put("hdmi_ports", 2 + (idx % 3));
            attrs.put("weight_kg", 10.0 + idx);
            return new CatalogProductEntity(id, "Smart TV " + (43 + idx * 3) + "\"",
                    "Smart TV idx " + idx,
                    "https://example.com/img/tv-" + id + ".jpg",
                    Math.min(5.0, 4.2 + idx * 0.05),
                    Category.SMART_TV, attrs);
        }

        private static CatalogProductEntity notebook(long id, int idx) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("brand", idx % 2 == 0 ? "Dell" : "Lenovo");
            attrs.put("memory", (8 + idx * 2) + " GB");
            attrs.put("storage", (256 + idx * 128) + " GB");
            attrs.put("cpu", "Intel i" + (5 + (idx % 4)));
            attrs.put("weight", String.format("%.2f kg", 1.2 + idx * 0.05));
            attrs.put("battery", (4500 + idx * 100) + " mAh");
            return new CatalogProductEntity(id, "Notebook Model " + idx,
                    "Notebook " + idx,
                    "https://example.com/img/nb-" + id + ".jpg",
                    Math.min(5.0, 4.1 + idx * 0.05),
                    Category.NOTEBOOK, attrs);
        }

        private static CatalogProductEntity headphone(long id, int idx) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("brand", idx % 2 == 0 ? "Sony" : "JBL");
            attrs.put("color", idx % 2 == 0 ? "Black" : "White");
            attrs.put("type", idx % 3 == 0 ? "over-ear" : "in-ear");
            if (idx >= 3) {
                attrs.put("battery", (20 + idx) + " h");
            }
            return new CatalogProductEntity(id, "Headphone Model " + idx,
                    "Headphone " + idx,
                    "https://example.com/img/hp-" + id + ".jpg",
                    Math.min(5.0, 4.0 + idx * 0.08),
                    Category.HEADPHONES, attrs);
        }

        private static CatalogProductEntity refrigerator(long id, int idx) {
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("brand", idx % 2 == 0 ? "Brastemp" : "Electrolux");
            attrs.put("capacity_l", 300 + idx * 25);
            attrs.put("color", idx % 2 == 0 ? "Inox" : "White");
            attrs.put("energy_class", idx % 3 == 0 ? "A" : "A+");
            attrs.put("weight_kg", 60.0 + idx * 2);
            return new CatalogProductEntity(id, "Refrigerator Model " + idx,
                    "Fridge " + idx,
                    "https://example.com/img/rf-" + id + ".jpg",
                    Math.min(5.0, 4.0 + idx * 0.07),
                    Category.REFRIGERATOR, attrs);
        }
    }
}
