package com.orderautomation.stock.repository;

import com.orderautomation.stock.domain.StockItem;
import com.orderautomation.stock.domain.StockMovement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcStockRepository implements StockRepository {
  private static final String STOCK_COLUMNS =
      "id, customer, collection, color, format, material, quantity, updated_at";
  private static final RowMapper<StockItem> STOCK_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new StockItem(
              resultSet.getLong("id"),
              resultSet.getString("customer"),
              resultSet.getString("collection"),
              resultSet.getString("color"),
              resultSet.getString("format"),
              resultSet.getString("material"),
              resultSet.getInt("quantity"),
              resultSet.getString("updated_at"));

  private final JdbcTemplate jdbcTemplate;

  public JdbcStockRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<StockItem> findAll() {
    return jdbcTemplate.query(
        "SELECT "
            + STOCK_COLUMNS
            + " FROM stock_item "
            + "ORDER BY customer, collection, color, format, material, id",
        STOCK_ROW_MAPPER);
  }

  @Override
  public Optional<StockItem> findById(long id) {
    return jdbcTemplate
        .query("SELECT " + STOCK_COLUMNS + " FROM stock_item WHERE id = ?", STOCK_ROW_MAPPER, id)
        .stream()
        .findFirst();
  }

  @Override
  public List<StockMovement> findMovements(long stockItemId) {
    return jdbcTemplate.query(
        "SELECT id, stock_item_id, order_id, delta, reason, created_at "
            + "FROM stock_movement WHERE stock_item_id = ? ORDER BY id DESC",
        (resultSet, rowNumber) -> {
          long orderIdValue = resultSet.getLong("order_id");
          Long orderId = resultSet.wasNull() ? null : orderIdValue;
          return new StockMovement(
              resultSet.getLong("id"),
              resultSet.getLong("stock_item_id"),
              orderId,
              resultSet.getInt("delta"),
              resultSet.getString("reason"),
              resultSet.getString("created_at"));
        },
        stockItemId);
  }

  @Override
  public void updateQuantity(long id, int quantity, String updatedAt, int delta, String reason) {
    jdbcTemplate.update(
        "UPDATE stock_item SET quantity = ?, updated_at = ? WHERE id = ?", quantity, updatedAt, id);
    jdbcTemplate.update(
        "INSERT INTO stock_movement (stock_item_id, delta, reason, created_at) "
            + "VALUES (?, ?, ?, ?)",
        id,
        delta,
        reason,
        updatedAt);
  }
}
