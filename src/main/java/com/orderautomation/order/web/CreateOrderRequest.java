package com.orderautomation.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

public record CreateOrderRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String clientOrderNumber,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String proformaNumber,
    @NotNull LocalDate proformaDate,
    @NotBlank String incoterm,
    @NotEmpty List<@NotNull @Valid OrderLineRequest> lines) {}
