package com.orderservice.service;

import com.orderservice.client.ProductClient;
import com.orderservice.client.UserClient;
import com.orderservice.config.KafkaTopicConfig;
import com.orderservice.dto.*;
import com.orderservice.entity.Order;
import com.orderservice.event.OrderCreatedEvent;
import com.orderservice.exception.OrderNotFoundException;
import com.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserClient userClient;
    private final ProductClient productClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for userId={}, productId={}", request.getUserId(), request.getProductId());

        UserDto user = userClient.getUserById(request.getUserId());
        log.info("Found user: {}", user.getName());

        ProductDto product = productClient.getProductById(request.getProductId());
        log.info("Found product: {} with price {}", product.getName(), product.getPrice());

        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalPrice(totalPrice)
                .status(Order.OrderStatus.PENDING)
                .build();

        Order savedOrder = orderRepository.save(order);
        log.info("Order created with id={}", savedOrder.getId());

        publishOrderCreatedEvent(savedOrder);

        return buildOrderResponse(savedOrder, user, product);
    }

    private void publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                LocalDateTime.now()
        );

        // Key = orderId as String. All events for orderId=42 go to the same Kafka partition,
        // guaranteeing ordering for that order's lifecycle (create → pay → reserve → confirm).
        kafkaTemplate.send(KafkaTopicConfig.ORDER_EVENTS_TOPIC,
                order.getId().toString(), event);
        log.info("Published OrderCreatedEvent to Kafka for orderId={}", order.getId());
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        UserDto user = userClient.getUserById(order.getUserId());
        ProductDto product = productClient.getProductById(order.getProductId());

        return buildOrderResponse(order, user, product);
    }

    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(order -> {
                    UserDto user = userClient.getUserById(order.getUserId());
                    ProductDto product = productClient.getProductById(order.getProductId());
                    return buildOrderResponse(order, user, product);
                });
    }

    public List<OrderResponse> getOrdersByUserId(Long userId) {
        UserDto user = userClient.getUserById(userId);

        return orderRepository.findByUserId(userId).stream()
                .map(order -> {
                    ProductDto product = productClient.getProductById(order.getProductId());
                    return buildOrderResponse(order, user, product);
                })
                .toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated to {}", id, newStatus);

        UserDto user = userClient.getUserById(order.getUserId());
        ProductDto product = productClient.getProductById(order.getProductId());

        return buildOrderResponse(updatedOrder, user, product);
    }

    @Transactional
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }
        orderRepository.deleteById(id);
        log.info("Order deleted with id={}", id);
    }

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
