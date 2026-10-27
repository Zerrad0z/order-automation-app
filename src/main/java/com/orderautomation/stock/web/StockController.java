package com.orderautomation.stock.web;

import com.orderautomation.stock.service.StockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/stock")
public class StockController {
  private final StockService stockService;

  public StockController(StockService stockService) {
    this.stockService = stockService;
  }

  @GetMapping
  public List<StockResponse> findAll() {
    return stockService.findAll().stream().map(StockResponse::from).toList();
  }

  @GetMapping("/{id}/movements")
  public List<StockMovementResponse> findMovements(@PathVariable long id) {
    return stockService.findMovements(id).stream().map(StockMovementResponse::from).toList();
  }

  @PutMapping("/{id}")
  public StockResponse updateQuantity(
      @PathVariable long id, @Valid @RequestBody UpdateStockRequest request) {
    return StockResponse.from(
        stockService.updateQuantity(id, request.quantity(), request.reason()));
  }
}
