package com.orderautomation.order.domain;

public record OrderLine(
    String itemCode,
    String clientRef,
    String collection,
    String color,
    String format,
    int quantity) {}
