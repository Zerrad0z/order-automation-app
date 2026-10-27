package com.orderautomation.stock.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStockRequest(
    @NotNull @Min(0) Integer quantity, @NotBlank @Size(max = 200) String reason) {}
