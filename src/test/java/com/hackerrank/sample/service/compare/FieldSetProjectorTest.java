package com.hackerrank.sample.service.compare;

import com.hackerrank.sample.exception.InvalidFieldsException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.CompareItem;
import com.hackerrank.sample.model.Condition;
import com.hackerrank.sample.model.ProductDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldSetProjectorTest {

    private static ProductDetail sample() {
        return new ProductDetail(
                1L, "Galaxy S24", "desc", "img.jpg", 4.5, Category.SMARTPHONE,
                Map.of("battery", "4000 mAh", "memory", "8 GB", "brand", "Samsung"),
                List.of(),
                new BuyBox(100L, "MELI-1-0", "Seller", 4,
                        new BigDecimal("4899.00"), "BRL", Condition.NEW, true, 5));
    }

    @Test
    void blankCsv_returnsDefaultSet() {
        FieldSet f = FieldSetProjector.parse(null);
        assertThat(f.explicit()).isFalse();
        assertThat(f.wantsBuyBox()).isTrue();
        assertThat(f.wantsAttributes()).isTrue();
        assertThat(f.wantsOffers()).isFalse();

        FieldSet f2 = FieldSetProjector.parse("  ");
        assertThat(f2.explicit()).isFalse();
    }

    @Test
    void parsesNameAndBuyBoxPriceSparse() {
        FieldSet f = FieldSetProjector.parse("name,buyBox.price");
        assertThat(f.explicit()).isTrue();
        assertThat(f.topLevel()).containsExactlyInAnyOrder("name");
        assertThat(f.wantsBuyBox()).isTrue();
        assertThat(f.buyBoxFields()).containsExactly("price");
        assertThat(f.wantsAttributes()).isFalse();
    }

    @Test
    void offersTokenSwapsBuyBoxForOffers() {
        FieldSet f = FieldSetProjector.parse("offers,name");
        CompareItem item = FieldSetProjector.project(sample(), f);
        assertThat(item.offers()).isNotNull();
        assertThat(item.buyBox()).isNull();
        assertThat(item.name()).isEqualTo("Galaxy S24");
    }

    @Test
    void unknownTopLevel_throws() {
        assertThatThrownBy(() -> FieldSetProjector.parse("bogus"))
                .isInstanceOf(InvalidFieldsException.class);
    }

    @Test
    void unknownBuyBoxField_throws() {
        assertThatThrownBy(() -> FieldSetProjector.parse("buyBox.bogus"))
                .isInstanceOf(InvalidFieldsException.class);
    }

    @Test
    void offersWithSubpath_throws() {
        assertThatThrownBy(() -> FieldSetProjector.parse("offers.price"))
                .isInstanceOf(InvalidFieldsException.class);
    }

    @Test
    void projectDefault_includesNameCategoryBuyBoxAttributes() {
        CompareItem item = FieldSetProjector.project(sample(), FieldSet.defaultSet());
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.name()).isEqualTo("Galaxy S24");
        assertThat(item.category()).isEqualTo(Category.SMARTPHONE);
        assertThat(item.attributes()).containsKey("battery");
        assertThat(item.buyBox()).isNotNull();
        assertThat(item.description()).isNull();
        assertThat(item.offers()).isNull();
    }

    @Test
    void projectSparse_buyBoxPriceOnly() {
        FieldSet f = FieldSetProjector.parse("name,buyBox.price");
        CompareItem item = FieldSetProjector.project(sample(), f);
        assertThat(item.name()).isEqualTo("Galaxy S24");
        assertThat(item.category()).isNull();
        assertThat(item.attributes()).isNull();
        assertThat(item.buyBox().price()).isEqualByComparingTo("4899.00");
        assertThat(item.buyBox().sellerId()).isNull();
        assertThat(item.buyBox().currency()).isNull();
    }

    @Test
    void projectSparse_attributesByKey() {
        FieldSet f = FieldSetProjector.parse("attributes.battery,attributes.unknown");
        CompareItem item = FieldSetProjector.project(sample(), f);
        assertThat(item.attributes()).containsOnlyKeys("battery");
    }

    @Test
    void idIsImplicitInOutput() {
        FieldSet f = FieldSetProjector.parse("name");
        CompareItem item = FieldSetProjector.project(sample(), f);
        assertThat(item.id()).isEqualTo(1L);
    }
}
