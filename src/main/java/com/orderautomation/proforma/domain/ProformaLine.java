package com.orderautomation.proforma.domain;

import java.util.List;

public record ProformaLine(
    String itemCode,
    String clientRef,
    String collection,
    String color,
    String format,
    String rawFormat,
    int quantity,
    List<String> invalidReasons) {
  public ProformaLine {
    invalidReasons = List.copyOf(invalidReasons);
  }
}
