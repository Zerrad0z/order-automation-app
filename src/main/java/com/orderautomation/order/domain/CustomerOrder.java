package com.orderautomation.order.domain;

import java.time.LocalDate;
import java.util.List;

public record CustomerOrder(
    long id,
    String customer,
    String clientOrderNumber,
    String proformaNumber,
    LocalDate proformaDate,
    String incoterm,
    String status,
    List<OrderLine> lines) {
  public CustomerOrder {
    lines = List.copyOf(lines);
  }
}
