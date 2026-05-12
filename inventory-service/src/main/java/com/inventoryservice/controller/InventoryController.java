package com.inventoryservice.controller;

import com.inventoryservice.dto.InventoryRequest;
import com.inventoryservice.dto.InventoryResponse;
import com.inventoryservice.dto.StockReservationRequest;
import com.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @Valid @RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.createInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable Long productId) {
        InventoryResponse response = inventoryService.getByProductId(productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAllInventory() {
        List<InventoryResponse> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(inventory);
    }

    @PatchMapping("/product/{productId}/add")
    public ResponseEntity<InventoryResponse> addStock(
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        InventoryResponse response = inventoryService.addStock(productId, quantity);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(
            @Valid @RequestBody StockReservationRequest request) {
        InventoryResponse response = inventoryService.reserveStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/release")
    public ResponseEntity<InventoryResponse> releaseStock(
            @Valid @RequestBody StockReservationRequest request) {
        InventoryResponse response = inventoryService.releaseStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<InventoryResponse> confirmReservation(
            @Valid @RequestBody StockReservationRequest request) {
        InventoryResponse response = inventoryService.confirmReservation(request);
        return ResponseEntity.ok(response);
    }
}
