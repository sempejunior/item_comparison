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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return assemble(product, productOffers);
    }

    public List<ProductDetail> getAllByCategory(Category category) {
        if (category == null) {
            return List.of();
        }
        List<CatalogProductEntity> all = products.findAllByCategory(category);
        if (all.isEmpty()) {
            return List.of();
        }
        List<Long> ids = all.stream().map(CatalogProductEntity::getId).toList();
        Map<Long, List<OfferEntity>> offersByProduct = new LinkedHashMap<>();
        for (OfferEntity offer : offers.findAllByCatalogProductIdIn(ids)) {
            offersByProduct.computeIfAbsent(offer.getCatalogProductId(), k -> new ArrayList<>()).add(offer);
        }
        List<ProductDetail> result = new ArrayList<>(all.size());
        for (CatalogProductEntity p : all) {
            result.add(assemble(p, offersByProduct.getOrDefault(p.getId(), List.of())));
        }
        return result;
    }

    public List<ProductDetail> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, CatalogProductEntity> productsById = new LinkedHashMap<>();
        for (CatalogProductEntity p : products.findAllByIdIn(ids)) {
            productsById.put(p.getId(), p);
        }
        Map<Long, List<OfferEntity>> offersByProduct = new LinkedHashMap<>();
        for (OfferEntity offer : offers.findAllByCatalogProductIdIn(productsById.keySet())) {
            offersByProduct.computeIfAbsent(offer.getCatalogProductId(), k -> new ArrayList<>()).add(offer);
        }
        List<ProductDetail> result = new ArrayList<>(ids.size());
        for (Long id : ids) {
            CatalogProductEntity product = productsById.get(id);
            if (product == null) {
                throw new ProductNotFoundException(id);
            }
            result.add(assemble(product, offersByProduct.getOrDefault(id, List.of())));
        }
        return result;
    }

    private ProductDetail assemble(CatalogProductEntity product, List<OfferEntity> productOffers) {
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
