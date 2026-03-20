package com.orderservice.dto;

import com.orderservice.entity.Order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private Long id;
    private Long userId;
    private String userName;       // Fetched from user-service
    private String userEmail;      // Fetched from user-service
    private Long productId;
    private String productName;    // Fetched from product-service
    private BigDecimal productPrice; // Fetched from product-service
    private Integer quantity;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
