package com.inventoryservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "availableStock is required")
    @Min(value = 0, message = "availableStock must be at least 0")
    private Integer availableStock;
}
