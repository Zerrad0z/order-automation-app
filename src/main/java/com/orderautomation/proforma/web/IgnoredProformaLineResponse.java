package com.orderautomation.proforma.web;

public record IgnoredProformaLineResponse(
    String itemCode, String description, int quantity, String reason) {}
