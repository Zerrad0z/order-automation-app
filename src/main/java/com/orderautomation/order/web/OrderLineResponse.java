package com.orderautomation.order.web;

public record OrderLineResponse(
    String itemCode,
    String clientRef,
    String collection,
    String color,
    String format,
    int quantity) {}
