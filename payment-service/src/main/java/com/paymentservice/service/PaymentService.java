package com.paymentservice.service;

import com.paymentservice.dto.PaymentRequest;
import com.paymentservice.dto.PaymentResponse;
import com.paymentservice.entity.Payment;
import com.paymentservice.entity.Payment.PaymentStatus;
import com.paymentservice.exception.PaymentFailedException;
import com.paymentservice.exception.PaymentNotFoundException;
import com.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // Idempotency check: if a payment with this transactionId already exists, return it
        var existing = paymentRepository.findByTransactionId(request.getTransactionId());
        if (existing.isPresent()) {
            log.info("Idempotent request detected for transactionId: {}", request.getTransactionId());
            return mapToResponse(existing.get());
        }

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .transactionId(request.getTransactionId())
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created with id: {} for order: {}", payment.getId(), payment.getOrderId());

        // Simulate payment processing — in production, this calls Stripe/Razorpay SDK
        try {
            simulatePaymentGateway(payment);
            payment.setStatus(PaymentStatus.COMPLETED);
            log.info("Payment completed for order: {}", payment.getOrderId());
        } catch (PaymentFailedException e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            log.error("Payment failed for order: {} — reason: {}", payment.getOrderId(), e.getMessage());
        }

        payment = paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    @Transactional
    public PaymentResponse refundPayment(Long orderId) {
        List<Payment> payments = paymentRepository.findByOrderId(orderId);

        Payment completedPayment = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .findFirst()
                .orElseThrow(() -> new PaymentNotFoundException(
                        "No completed payment found for order: " + orderId));

        completedPayment.setStatus(PaymentStatus.REFUNDED);
        completedPayment = paymentRepository.save(completedPayment);
        log.info("Payment refunded for order: {}, amount: {}", orderId, completedPayment.getAmount());

        return mapToResponse(completedPayment);
    }

    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
        return mapToResponse(payment);
    }

    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with transactionId: " + transactionId));
        return mapToResponse(payment);
    }

    public List<PaymentResponse> getPaymentsByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<PaymentResponse> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void simulatePaymentGateway(Payment payment) {
        // Simulate ~95% success rate — amounts ending in .99 fail (for Saga testing)
        if (payment.getAmount().toString().endsWith(".99")) {
            throw new PaymentFailedException("Payment gateway declined: insufficient funds");
        }
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
