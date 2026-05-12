package com.paymentservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by: inventory-service → inventory-events topic
 * Consumed by:  payment-service (triggers automatic refund — COMPENSATING TRANSACTION)
 *
 * This is the heart of the Saga compensating logic.
 * When inventory reservation fails AFTER payment succeeded,
 * payment-service must reverse the charge.
 *
 * Senior note: This is called a "compensating transaction" — the Saga equivalent of a ROLLBACK.
 * Unlike a DB rollback, it is a new forward-moving transaction that UNDOES the previous one.
 * The payment record stays in the DB with status=REFUNDED for audit trail.
 * You never physically delete or undo — you append a compensating action.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryFailedEvent {
    private Long orderId;
    private Long productId;
    private String reason;
}
