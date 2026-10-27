package com.orderautomation.stock.web;

import com.orderautomation.stock.domain.StockMovement;

public record StockMovementResponse(
    long id, long stockItemId, Long orderId, int delta, String reason, String createdAt) {
  public static StockMovementResponse from(StockMovement movement) {
    return new StockMovementResponse(
        movement.id(),
        movement.stockItemId(),
        movement.orderId(),
        movement.delta(),
        movement.reason(),
        movement.createdAt());
  }
}
