package com.inventoryservice.service;

import com.inventoryservice.dto.InventoryRequest;
import com.inventoryservice.dto.InventoryResponse;
import com.inventoryservice.dto.StockReservationRequest;
import com.inventoryservice.entity.Inventory;
import com.inventoryservice.exception.InsufficientStockException;
import com.inventoryservice.exception.InventoryNotFoundException;
import com.inventoryservice.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryResponse createInventory(InventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.getProductId())) {
            throw new IllegalArgumentException(
                    "Inventory already exists for product: " + request.getProductId());
        }

        Inventory inventory = Inventory.builder()
                .productId(request.getProductId())
                .availableStock(request.getAvailableStock())
                .reservedStock(0)
                .build();

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Created inventory for product {}: {} units", saved.getProductId(), saved.getAvailableStock());
        return mapToResponse(saved);
    }

    public InventoryResponse getByProductId(Long productId) {
        Inventory inventory = findByProductIdOrThrow(productId);
        return mapToResponse(inventory);
    }

    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public InventoryResponse addStock(Long productId, Integer quantity) {
        Inventory inventory = findByProductIdOrThrow(productId);
        inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
        Inventory saved = inventoryRepository.save(inventory);
        log.info("Added {} units to product {}. Available: {}", quantity, productId, saved.getAvailableStock());
        return mapToResponse(saved);
    }

    /**
     * Reserve stock for an order.
     * Moves units from availableStock to reservedStock.
     * The @Version field on Inventory ensures that if two threads try to reserve
     * the same stock simultaneously, one will fail with OptimisticLockingFailureException.
     */
    @Transactional
    public InventoryResponse reserveStock(StockReservationRequest request) {
        Inventory inventory = findByProductIdOrThrow(request.getProductId());

        if (inventory.getAvailableStock() < request.getQuantity()) {
            throw new InsufficientStockException(
                    "Insufficient stock for product " + request.getProductId()
                            + ". Available: " + inventory.getAvailableStock()
                            + ", Requested: " + request.getQuantity());
        }

        inventory.setAvailableStock(inventory.getAvailableStock() - request.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() + request.getQuantity());

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Reserved {} units for product {}. Available: {}, Reserved: {}",
                request.getQuantity(), request.getProductId(),
                saved.getAvailableStock(), saved.getReservedStock());
        return mapToResponse(saved);
    }

    /**
     * Release reserved stock back to available.
     * Called when payment fails or order is cancelled — the compensating action.
     */
    @Transactional
    public InventoryResponse releaseStock(StockReservationRequest request) {
        Inventory inventory = findByProductIdOrThrow(request.getProductId());

        if (inventory.getReservedStock() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot release " + request.getQuantity()
                            + " units — only " + inventory.getReservedStock() + " reserved");
        }

        inventory.setAvailableStock(inventory.getAvailableStock() + request.getQuantity());
        inventory.setReservedStock(inventory.getReservedStock() - request.getQuantity());

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Released {} units for product {}. Available: {}, Reserved: {}",
                request.getQuantity(), request.getProductId(),
                saved.getAvailableStock(), saved.getReservedStock());
        return mapToResponse(saved);
    }

    /**
     * Confirm a reservation — deduct from reservedStock permanently.
     * Called when payment succeeds and order is finalized.
     */
    @Transactional
    public InventoryResponse confirmReservation(StockReservationRequest request) {
        Inventory inventory = findByProductIdOrThrow(request.getProductId());

        if (inventory.getReservedStock() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot confirm " + request.getQuantity()
                            + " units — only " + inventory.getReservedStock() + " reserved");
        }

        inventory.setReservedStock(inventory.getReservedStock() - request.getQuantity());

        Inventory saved = inventoryRepository.save(inventory);
        log.info("Confirmed reservation of {} units for product {}. Reserved: {}",
                request.getQuantity(), request.getProductId(), saved.getReservedStock());
        return mapToResponse(saved);
    }

    private Inventory findByProductIdOrThrow(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "Inventory not found for product: " + productId));
    }

    private InventoryResponse mapToResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .productId(inventory.getProductId())
                .availableStock(inventory.getAvailableStock())
                .reservedStock(inventory.getReservedStock())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}
