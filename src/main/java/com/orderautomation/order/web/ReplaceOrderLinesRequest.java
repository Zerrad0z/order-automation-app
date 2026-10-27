package com.orderautomation.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReplaceOrderLinesRequest(@NotEmpty List<@NotNull @Valid OrderLineRequest> lines) {}
