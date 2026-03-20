package com.orderservice.service;

import com.orderservice.client.ProductClient;
import com.orderservice.client.UserClient;
import com.orderservice.dto.*;
import com.orderservice.entity.Order;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;       // Feign client for user-service
    private final ProductClient productClient; // Feign client for product-service

    /**
     * Creates a new order.
     * 1. Validates user exists by calling user-service
     * 2. Validates product exists and gets price from product-service
     * 3. Calculates total price
     * 4. Saves order to database
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for userId={}, productId={}", request.getUserId(), request.getProductId());

        // Call user-service to validate user exists
        UserDto user = userClient.getUserById(request.getUserId());
        log.info("Found user: {}", user.getName());

        // Call product-service to validate product and get price
        ProductDto product = productClient.getProductById(request.getProductId());
        log.info("Found product: {} with price {}", product.getName(), product.getPrice());

        // Calculate total price
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        // Build and save order
        Order order = Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(totalPrice)
                .status(Order.OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id={}", savedOrder.getId());

        return buildOrderResponse(savedOrder, user, product);
    }

    /**
     * Gets an order by ID with enriched user and product details.
     */
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        // Fetch user and product details for enriched response
        UserDto user = userClient.getUserById(order.getUserId());
        ProductDto product = productClient.getProductById(order.getProductId());

        return buildOrderResponse(order, user, product);
    }

    /**
     * Gets all orders with enriched details.
     */
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(order -> {
                    UserDto user = userClient.getUserById(order.getUserId());
                    ProductDto product = productClient.getProductById(order.getProductId());
                    return buildOrderResponse(order, user, product);
                })
                .toList();
    }

    /**
     * Gets all orders for a specific user.
     */
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        // Validate user exists first
        UserDto user = userClient.getUserById(userId);

        return orderRepository.findByUserId(userId).stream()
                .map(order -> {
                    ProductDto product = productClient.getProductById(order.getProductId());
                    return buildOrderResponse(order, user, product);
                })
                .toList();
    }

    /**
     * Updates order status.
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        UserDto user = userClient.getUserById(order.getUserId());
        ProductDto product = productClient.getProductById(order.getProductId());

        return buildOrderResponse(updatedOrder, user, product);
    }

    /**
     * Deletes an order.
     */
    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
        log.info("Order deleted with id={}", id);
    }

    /**
     * Helper method to build enriched OrderResponse.
     */
    private OrderResponse buildOrderResponse(Order order, UserDto user, ProductDto product) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .productId(order.getProductId())
                .productName(product.getName())
                .productPrice(product.getPrice())
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
