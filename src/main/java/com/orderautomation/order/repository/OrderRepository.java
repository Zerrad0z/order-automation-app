package com.orderautomation.order.repository;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
  long create(
      String clientOrderNumber,
      String proformaNumber,
      String proformaDate,
      String incoterm,
      List<OrderLine> lines,
      String createdAt);

  List<CustomerOrder> findAll();

  Optional<CustomerOrder> findById(long id);

  void replaceLines(long orderId, List<OrderLine> lines);
}
