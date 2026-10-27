package com.orderautomation.order.web;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {
  OrderResponse toResponse(CustomerOrder order);

  List<OrderResponse> toResponses(List<CustomerOrder> orders);

  OrderLineResponse toResponse(OrderLine line);
}
