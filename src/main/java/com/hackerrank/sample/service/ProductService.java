package com.hackerrank.sample.service;

import com.hackerrank.sample.exception.ProductNotFoundException;
import com.hackerrank.sample.model.BuyBox;
import com.hackerrank.sample.model.Category;
import com.hackerrank.sample.model.PageResponse;
import com.hackerrank.sample.model.ProductDetail;
import com.hackerrank.sample.model.ProductSummary;
import com.hackerrank.sample.repository.CatalogProductEntity;
import com.hackerrank.sample.repository.CatalogProductRepository;
import com.hackerrank.sample.repository.OfferEntity;
import com.hackerrank.sample.repository.OfferRepository;
import com.hackerrank.sample.service.compare.BuyBoxSelector;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final CatalogProductRepository products;
    private final OfferRepository offers;

    public ProductService(CatalogProductRepository products, OfferRepository offers) {
        this.products = products;
        this.offers = offers;
    }

    public PageResponse<ProductSummary> list(Category category, Pageable pageable) {
        Page<CatalogProductEntity> page = category == null
                ? products.findAll(pageable)
                : products.findAllByCategory(category, pageable);
        List<ProductSummary> items = page.getContent().stream().map(this::toSummary).toList();
        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Cacheable("products")
    public ProductDetail getById(long id) {
        CatalogProductEntity product = products.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        List<OfferEntity> productOffers = offers.findAllByCatalogProductId(id);
        Optional<OfferEntity> buyBox = BuyBoxSelector.select(productOffers);
        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getRating(),
                product.getCategory(),
                product.getAttributes(),
                productOffers.stream().map(this::toOffer).toList(),
                buyBox.map(this::toBuyBox).orElse(null));
    }

    private ProductSummary toSummary(CatalogProductEntity p) {
        return new ProductSummary(p.getId(), p.getName(), p.getImageUrl(), p.getRating(), p.getCategory());
    }

    private ProductDetail.Offer toOffer(OfferEntity o) {
        return new ProductDetail.Offer(
                o.getId(), o.getSellerId(), o.getSellerName(),
                o.getSellerReputation(), o.getPrice(), o.getCurrency(),
                o.getCondition(), o.getFreeShipping(), o.getStock());
    }

    private BuyBox toBuyBox(OfferEntity o) {
        return new BuyBox(
                o.getId(), o.getSellerId(), o.getSellerName(),
                o.getSellerReputation(), o.getPrice(), o.getCurrency(),
                o.getCondition(), o.getFreeShipping(), o.getStock());
    }
}
