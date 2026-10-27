package com.orderautomation.order.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderLineRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String itemCode,
    String clientRef,
    @NotNull String collection,
    @NotNull String color,
    @NotNull String format,
    @NotNull @PositiveOrZero Integer quantity) {}
