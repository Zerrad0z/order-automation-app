package com.orderautomation.order.repository;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOrderRepository implements OrderRepository {
  private static final String ORDER_COLUMNS =
      "o.id, o.customer, o.client_order_number, o.proforma_number, o.proforma_date, "
          + "o.incoterm, o.status";
  private static final String ORDER_QUERY =
      "SELECT "
          + ORDER_COLUMNS
          + ", l.item_code, l.client_ref, l.collection, l.color, l.format, l.quantity "
          + "FROM customer_order o LEFT JOIN order_line l ON l.order_id = o.id ";

  private final JdbcTemplate jdbcTemplate;

  public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public long create(
      String clientOrderNumber,
      String proformaNumber,
      String proformaDate,
      String incoterm,
      List<OrderLine> lines,
      String createdAt) {
    insertOrder(clientOrderNumber, proformaNumber, proformaDate, incoterm, createdAt);
    long orderId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    insertLines(orderId, lines);
    return orderId;
  }

  private void insertOrder(
      String clientOrderNumber,
      String proformaNumber,
      String proformaDate,
      String incoterm,
      String createdAt) {
    try {
      jdbcTemplate.update(
          "INSERT INTO customer_order "
              + "(customer, client_order_number, proforma_number, proforma_date, incoterm, "
              + "status, created_at) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?)",
          "RESTAER",
          clientOrderNumber,
          proformaNumber,
          proformaDate,
          incoterm,
          createdAt);
    } catch (UncategorizedSQLException exception) {
      String message = exception.getSQLException().getMessage();
      if (exception.getSQLException().getErrorCode() == 19
          && message != null
          && message.contains("customer_order.customer, customer_order.client_order_number")) {
        throw new DuplicateKeyException("The customer order number already exists.", exception);
      }
      throw exception;
    }
  }

  @Override
  public List<CustomerOrder> findAll() {
    return mapOrders(
        jdbcTemplate.query(
            ORDER_QUERY + "ORDER BY o.created_at DESC, o.id DESC, l.id", ORDER_ROW_MAPPER));
  }

  @Override
  public Optional<CustomerOrder> findById(long id) {
    List<CustomerOrder> orders =
        mapOrders(
            jdbcTemplate.query(ORDER_QUERY + "WHERE o.id = ? ORDER BY l.id", ORDER_ROW_MAPPER, id));
    return orders.stream().findFirst();
  }

  @Override
  public void replaceLines(long orderId, List<OrderLine> lines) {
    jdbcTemplate.update("DELETE FROM order_line WHERE order_id = ?", orderId);
    insertLines(orderId, lines);
  }

  private void insertLines(long orderId, List<OrderLine> lines) {
    for (OrderLine line : lines) {
      jdbcTemplate.update(
          "INSERT INTO order_line "
              + "(order_id, item_code, client_ref, collection, color, format, quantity) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?)",
          orderId,
          line.itemCode(),
          line.clientRef(),
          line.collection(),
          line.color(),
          line.format(),
          line.quantity());
    }
  }

  private List<CustomerOrder> mapOrders(List<OrderSnapshot> rows) {
    Map<Long, OrderAccumulator> orders = new LinkedHashMap<>();
    for (OrderSnapshot row : rows) {
      OrderAccumulator order =
          orders.computeIfAbsent(row.id(), unused -> new OrderAccumulator(row));
      if (row.line() != null) {
        order.lines().add(row.line());
      }
    }
    return orders.values().stream().map(OrderAccumulator::toOrder).toList();
  }

  private static final RowMapper<OrderSnapshot> ORDER_ROW_MAPPER =
      (resultSet, rowNumber) -> {
        String itemCode = resultSet.getString("item_code");
        OrderLine line =
            itemCode == null
                ? null
                : new OrderLine(
                    itemCode,
                    resultSet.getString("client_ref"),
                    resultSet.getString("collection"),
                    resultSet.getString("color"),
                    resultSet.getString("format"),
                    resultSet.getInt("quantity"));
        return new OrderSnapshot(
            resultSet.getLong("id"),
            resultSet.getString("customer"),
            resultSet.getString("client_order_number"),
            resultSet.getString("proforma_number"),
            LocalDate.parse(resultSet.getString("proforma_date")),
            resultSet.getString("incoterm"),
            resultSet.getString("status"),
            line);
      };

  private record OrderSnapshot(
      long id,
      String customer,
      String clientOrderNumber,
      String proformaNumber,
      LocalDate proformaDate,
      String incoterm,
      String status,
      OrderLine line) {}

  private static final class OrderAccumulator {
    private final OrderSnapshot header;
    private final List<OrderLine> lines = new ArrayList<>();

    private OrderAccumulator(OrderSnapshot header) {
      this.header = header;
    }

    private List<OrderLine> lines() {
      return lines;
    }

    private CustomerOrder toOrder() {
      return new CustomerOrder(
          header.id(),
          header.customer(),
          header.clientOrderNumber(),
          header.proformaNumber(),
          header.proformaDate(),
          header.incoterm(),
          header.status(),
          lines);
    }
  }
}
