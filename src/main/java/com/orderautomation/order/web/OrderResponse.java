package com.orderautomation.order.web;

import java.time.LocalDate;
import java.util.List;

public record OrderResponse(
    long id,
    String customer,
    String clientOrderNumber,
    String proformaNumber,
    LocalDate proformaDate,
    String incoterm,
    String status,
    List<OrderLineResponse> lines) {}
