package com.orderautomation.proforma.web;

import java.util.List;

public record ProformaLineResponse(
    String itemCode,
    String clientRef,
    String collection,
    String color,
    String format,
    String rawFormat,
    int quantity,
    List<String> invalidReasons) {}
