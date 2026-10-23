
package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // SKU validation

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);

    Optional<Product> findBySku(String sku);


    // Existing queries

    Page<Product> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );

    Page<Product> findByCategoryId(
            Long categoryId,
            Pageable pageable
    );

    Page<Product> findByNameContainingIgnoreCaseAndCategoryId(
            String name,
            Long categoryId,
            Pageable pageable
    );


    // Active products only

    Page<Product> findByActiveTrue(
            Pageable pageable
    );

    Page<Product> findByNameContainingIgnoreCaseAndActiveTrue(
            String name,
            Pageable pageable
    );

    Page<Product> findByCategoryIdAndActiveTrue(
            Long categoryId,
            Pageable pageable
    );

    Page<Product> findByNameContainingIgnoreCaseAndCategoryIdAndActiveTrue(
            String name,
            Long categoryId,
            Pageable pageable
    );

    Optional<Product> findByIdAndActiveTrue(Long id);


    Page<Product> findByActiveFalse(Pageable pageable);

    Optional<Product> findByIdAndActiveFalse(Long id);



    // Row locking for inventory and order operations.
    // Retained for compatibility with existing callers.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Product p
            WHERE p.id = :id
            """)
    Optional<Product> findByIdForUpdate(
            @Param("id") Long id
    );
}
