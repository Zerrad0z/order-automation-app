package com.orderautomation.stock.domain;

public record StockItem(
    long id,
    String customer,
    String collection,
    String color,
    String format,
    String material,
    int quantity,
    String updatedAt) {}
