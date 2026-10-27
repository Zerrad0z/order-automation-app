package com.orderautomation.proforma.domain;

public record IgnoredProformaLine(
    String itemCode, String description, int quantity, String reason) {}
