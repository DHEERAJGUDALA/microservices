package com.notificationservice.consumer;

import com.notificationservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    @RabbitListener(queues = "order-notification-queue")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("========================================================");
        log.info("RECEIVED OrderCreatedEvent!");
        log.info("Order ID: {}", event.getOrderId());
        log.info("User ID: {}", event.getUserId());
        log.info("Product ID: {}", event.getProductId());
        log.info("Quantity: {}", event.getQuantity());
        log.info("Created At: {}", event.getCreatedAt());
        log.info("--------------------------------------------------------");
        log.info("Sending email notification to user {} about order {}",
                event.getUserId(), event.getOrderId());
        log.info("========================================================");
    }
}