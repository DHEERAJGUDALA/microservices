package com.orderservice.repository;

import com.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Find all orders by user ID
    List<Order> findByUserId(Long userId);

    // Find all orders by product ID
    List<Order> findByProductId(Long productId);

    // Find all orders by status
    List<Order> findByStatus(Order.OrderStatus status);
}
