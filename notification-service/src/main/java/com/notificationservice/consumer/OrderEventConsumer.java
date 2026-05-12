package com.notificationservice.consumer;

import com.notificationservice.event.InventoryFailedEvent;
import com.notificationservice.event.InventoryReservedEvent;
import com.notificationservice.event.OrderCreatedEvent;
import com.notificationservice.event.PaymentFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Notification Service — Saga Event Consumer
 *
 * This service sits at the END of the Saga and reacts to ALL outcome events.
 * It represents the customer-facing communication layer.
 *
 * It listens to THREE topics:
 * 1. order-events → "Order received! We're processing it."
 * 2. inventory-events → "Order confirmed!" OR "Order cancelled (out of stock)"
 * 3. payment-events → "Order cancelled (payment declined)"
 *
 * WHY notification-service listens to all three instead of just the final one?
 * UX reason: customers expect INSTANT acknowledgement.
 * "Order received" email goes the moment the order is created (order-events).
 * "Order confirmed" email goes when stock is reserved (inventory-events).
 * If you waited for all saga steps before any notification, the customer
 * would see nothing for several seconds — bad UX.
 *
 * PRODUCTION NOTE:
 * In production, this service would call an email provider (SendGrid, AWS SES)
 * or push notification service (Firebase). Here we use log.info() to simulate it.
 * The pattern is identical — just replace log.info() with emailService.send().
 *
 * WHY groupId="notification-group" for ALL topics?
 * Notification-service should process EVERY order event — not just some partitions.
 * Since notification-service isn't shared with any other consumer type,
 * using the same group for all its listeners is correct.
 * Each listener is a separate subscription tracked independently by Kafka.
 */
@Service
@Slf4j
public class OrderEventConsumer {

    // ==================== ORDER EVENTS ====================

    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("========================================================");
        log.info("📧 NOTIFICATION: Order Received");
        log.info("   Order ID:    {}", event.getOrderId());
        log.info("   User ID:     {}", event.getUserId());
        log.info("   Product ID:  {}", event.getProductId());
        log.info("   Quantity:    {}", event.getQuantity());
        log.info("   Total Price: {}", event.getTotalPrice());
        log.info("   → EMAIL: 'Hi User {}! Your order #{} has been received and is being processed.'",
                event.getUserId(), event.getOrderId());
        log.info("========================================================");
    }

    // ==================== INVENTORY EVENTS (Final States) ====================

    @KafkaListener(topics = "inventory-events", groupId = "notification-group")
    public void handleInventoryEvent(Object rawEvent) {
        if (rawEvent instanceof InventoryReservedEvent event) {
            handleOrderConfirmed(event);
        } else if (rawEvent instanceof InventoryFailedEvent event) {
            handleOrderCancelledOutOfStock(event);
        }
    }

    private void handleOrderConfirmed(InventoryReservedEvent event) {
        log.info("========================================================");
        log.info("📧 NOTIFICATION: Order CONFIRMED ✅");
        log.info("   Order ID:   {}", event.getOrderId());
        log.info("   Product ID: {}", event.getProductId());
        log.info("   Quantity:   {}", event.getQuantity());
        log.info("   → EMAIL: 'Great news! Your order #{} is confirmed and will be shipped soon.'",
                event.getOrderId());
        log.info("========================================================");
    }

    private void handleOrderCancelledOutOfStock(InventoryFailedEvent event) {
        log.warn("========================================================");
        log.warn("📧 NOTIFICATION: Order CANCELLED — Out of Stock ❌");
        log.warn("   Order ID:   {}", event.getOrderId());
        log.warn("   Product ID: {}", event.getProductId());
        log.warn("   Reason:     {}", event.getReason());
        log.warn("   → EMAIL: 'Sorry! Order #{} was cancelled. Reason: {}. Your payment has been refunded.'",
                event.getOrderId(), event.getReason());
        log.warn("========================================================");
    }

    // ==================== PAYMENT EVENTS (Failure State) ====================

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void handlePaymentEvent(Object rawEvent) {
        if (rawEvent instanceof PaymentFailedEvent event) {
            handlePaymentFailed(event);
        }
        // We intentionally IGNORE PaymentCompletedEvent here.
        // The confirmed notification will come from inventory-events (InventoryReservedEvent).
        // Sending a "payment success" email AND a "order confirmed" email = duplicate emails.
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("========================================================");
        log.warn("📧 NOTIFICATION: Order CANCELLED — Payment Failed ❌");
        log.warn("   Order ID: {}", event.getOrderId());
        log.warn("   User ID:  {}", event.getUserId());
        log.warn("   Reason:   {}", event.getReason());
        log.warn("   → EMAIL: 'Sorry! Your payment for order #{} was declined. Reason: {}. Please try again.'",
                event.getOrderId(), event.getReason());
        log.warn("========================================================");
    }
}
