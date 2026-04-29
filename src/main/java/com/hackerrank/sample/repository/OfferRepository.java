package com.hackerrank.sample.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OfferRepository extends JpaRepository<OfferEntity, Long> {

    List<OfferEntity> findAllByCatalogProductId(Long catalogProductId);

    List<OfferEntity> findAllByCatalogProductIdIn(Collection<Long> catalogProductIds);
}
