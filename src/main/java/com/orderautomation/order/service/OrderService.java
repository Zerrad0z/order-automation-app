package com.orderautomation.order.service;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import com.orderautomation.order.repository.OrderRepository;
import com.orderautomation.order.web.CreateOrderRequest;
import com.orderautomation.order.web.OrderLineRequest;
import com.orderautomation.shared.AppException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final OrderRepository orderRepository;
  private final Clock clock;

  public OrderService(OrderRepository orderRepository, Clock clock) {
    this.orderRepository = orderRepository;
    this.clock = clock;
  }

  @Transactional
  public CustomerOrder create(CreateOrderRequest request) {
    long id;
    try {
      id =
          orderRepository.create(
              request.clientOrderNumber(),
              request.proformaNumber(),
              request.proformaDate().toString(),
              request.incoterm(),
              toLines(request.lines()),
              LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString());
    } catch (DuplicateKeyException exception) {
      throw new AppException(
          "ORDER_ALREADY_EXISTS",
          "An order with customer order number " + request.clientOrderNumber() + " already exists.",
          exception);
    }
    return get(id);
  }

  @Transactional(readOnly = true)
  public List<CustomerOrder> findAll() {
    return orderRepository.findAll();
  }

  @Transactional(readOnly = true)
  public CustomerOrder get(long id) {
    return orderRepository
        .findById(id)
        .orElseThrow(() -> new AppException("ORDER_NOT_FOUND", "Order " + id + " was not found."));
  }

  @Transactional
  public CustomerOrder replaceLines(long id, List<OrderLineRequest> lineRequests) {
    CustomerOrder order = get(id);
    if (!order.status().equals("DRAFT")) {
      throw new AppException("ORDER_NOT_DRAFT", "Only draft order lines can be edited.");
    }
    orderRepository.replaceLines(id, toLines(lineRequests));
    return get(id);
  }

  @Transactional
  public CustomerOrder archiveDraft(long id) {
    return changeStatus(id, "DRAFT", "ARCHIVED", "ORDER_NOT_DRAFT");
  }

  @Transactional
  public CustomerOrder restoreArchived(long id) {
    return changeStatus(id, "ARCHIVED", "DRAFT", "ORDER_NOT_ARCHIVED");
  }

  private CustomerOrder changeStatus(
      long id, String expectedStatus, String newStatus, String errorCode) {
    CustomerOrder order = get(id);
    if (!order.status().equals(expectedStatus)) {
      throw new AppException(
          errorCode,
          expectedStatus.equals("DRAFT")
              ? "Only draft orders can be archived."
              : "Only archived orders can be restored.");
    }
    try {
      if (!orderRepository.updateStatus(id, expectedStatus, newStatus)) {
        throw new AppException(
            errorCode,
            expectedStatus.equals("DRAFT")
                ? "Only draft orders can be archived."
                : "Only archived orders can be restored.");
      }
    } catch (DuplicateKeyException exception) {
      throw new AppException(
          "ORDER_ALREADY_EXISTS",
          "An order with customer order number " + order.clientOrderNumber() + " already exists.",
          exception);
    }
    return get(id);
  }

  private List<OrderLine> toLines(List<OrderLineRequest> lines) {
    return lines.stream()
        .map(
            line ->
                new OrderLine(
                    line.itemCode(),
                    line.clientRef(),
                    line.collection(),
                    line.color(),
                    line.format(),
                    line.quantity()))
        .toList();
  }
}
