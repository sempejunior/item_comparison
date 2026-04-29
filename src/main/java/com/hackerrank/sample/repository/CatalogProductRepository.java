package com.hackerrank.sample.repository;

import com.hackerrank.sample.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CatalogProductRepository extends JpaRepository<CatalogProductEntity, Long> {

    Page<CatalogProductEntity> findAllByCategory(Category category, Pageable pageable);

    List<CatalogProductEntity> findAllByIdIn(Collection<Long> ids);
}
