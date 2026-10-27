package com.orderautomation.order.web;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orderService;
  private final OrderMapper orderMapper;

  public OrderController(OrderService orderService, OrderMapper orderMapper) {
    this.orderService = orderService;
    this.orderMapper = orderMapper;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    CustomerOrder order = orderService.create(request);
    return ResponseEntity.created(URI.create("/api/orders/" + order.id()))
        .body(orderMapper.toResponse(order));
  }

  @GetMapping
  public List<OrderResponse> findAll() {
    return orderMapper.toResponses(orderService.findAll());
  }

  @GetMapping("/{id}")
  public OrderResponse get(@PathVariable long id) {
    return orderMapper.toResponse(orderService.get(id));
  }

  @PutMapping("/{id}/lines")
  public OrderResponse replaceLines(
      @PathVariable long id, @Valid @RequestBody ReplaceOrderLinesRequest request) {
    return orderMapper.toResponse(orderService.replaceLines(id, request.lines()));
  }
}
