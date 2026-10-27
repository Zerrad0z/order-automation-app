package com.orderautomation.stock.service;

import com.orderautomation.shared.AppException;
import com.orderautomation.stock.domain.StockItem;
import com.orderautomation.stock.domain.StockMovement;
import com.orderautomation.stock.repository.StockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
  private final StockRepository stockRepository;
  private final Clock clock;

  public StockService(StockRepository stockRepository, Clock clock) {
    this.stockRepository = stockRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<StockItem> findAll() {
    return stockRepository.findAll();
  }

  @Transactional(readOnly = true)
  public List<StockMovement> findMovements(long id) {
    stockRepository
        .findById(id)
        .orElseThrow(() -> new AppException("STOCK_ITEM_NOT_FOUND", "Stock item was not found."));
    return stockRepository.findMovements(id);
  }

  @Transactional
  public StockItem updateQuantity(long id, int quantity, String reason) {
    StockItem current =
        stockRepository
            .findById(id)
            .orElseThrow(
                () -> new AppException("STOCK_ITEM_NOT_FOUND", "Stock item was not found."));
    int delta = quantity - current.quantity();
    if (delta != 0) {
      String updatedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
      stockRepository.updateQuantity(id, quantity, updatedAt, delta, reason.trim());
    }
    return stockRepository.findById(id).orElseThrow();
  }
}
