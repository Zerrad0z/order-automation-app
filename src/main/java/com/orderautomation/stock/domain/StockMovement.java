package com.orderautomation.stock.domain;

public record StockMovement(
    long id, long stockItemId, Long orderId, int delta, String reason, String createdAt) {}
