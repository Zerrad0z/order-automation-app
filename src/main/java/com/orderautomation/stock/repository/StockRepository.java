package com.orderautomation.stock.repository;

import com.orderautomation.stock.domain.StockItem;
import com.orderautomation.stock.domain.StockMovement;
import java.util.List;
import java.util.Optional;

public interface StockRepository {
  List<StockItem> findAll();

  Optional<StockItem> findById(long id);

  List<StockMovement> findMovements(long stockItemId);

  void updateQuantity(long id, int quantity, String updatedAt, int delta, String reason);
}
