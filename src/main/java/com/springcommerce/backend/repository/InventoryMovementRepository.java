package com.springcommerce.backend.repository;

import com.springcommerce.backend.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement>
    findByProductIdOrderByCreatedAtDesc(Long productId);
}
