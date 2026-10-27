package com.orderautomation.stock.web;

import com.orderautomation.stock.domain.StockItem;

public record StockResponse(
    long id,
    String customer,
    String collection,
    String color,
    String format,
    String material,
    int quantity,
    String updatedAt) {
  public static StockResponse from(StockItem stockItem) {
    return new StockResponse(
        stockItem.id(),
        stockItem.customer(),
        stockItem.collection(),
        stockItem.color(),
        stockItem.format(),
        stockItem.material(),
        stockItem.quantity(),
        stockItem.updatedAt());
  }
}
