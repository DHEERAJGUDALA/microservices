package com.orderservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to user from user-service (stored as ID, not a foreign key)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Reference to product from product-service (stored as ID, not a foreign key)
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "order_status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        // Initial state — order exists but Saga hasn't started yet
        PENDING,

        // Saga Step 1: Payment Service received the event and is processing
        PAYMENT_PROCESSING,

        // Saga Step 2: Payment succeeded, now waiting for inventory
        PAYMENT_COMPLETED,

        // Saga terminal failure: payment was declined by gateway
        PAYMENT_FAILED,

        // Saga Step 3: Inventory Service is reserving stock
        INVENTORY_RESERVING,

        // Saga SUCCESS: payment charged + stock reserved — order is live
        CONFIRMED,

        // Saga FAILURE: something went wrong, compensating transactions ran
        CANCELLED,

        // Post-saga lifecycle states
        SHIPPED,
        DELIVERED
    }
}
