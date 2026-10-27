package db.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V3__allow_reusing_archived_order_numbers extends BaseJavaMigration {
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    boolean originalAutoCommit = connection.getAutoCommit();
    if (!originalAutoCommit) {
      connection.commit();
      connection.setAutoCommit(true);
    }

    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = OFF");
      connection.setAutoCommit(false);
      try {
        statement.execute(
            "CREATE TABLE customer_order_new ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "customer TEXT NOT NULL, "
                + "client_order_number TEXT NOT NULL, "
                + "proforma_number TEXT, "
                + "proforma_date TEXT, "
                + "incoterm TEXT, "
                + "month TEXT, "
                + "status TEXT NOT NULL DEFAULT 'DRAFT', "
                + "folder_path TEXT, "
                + "created_at TEXT NOT NULL)");
        statement.execute(
            "INSERT INTO customer_order_new ("
                + "id, customer, client_order_number, proforma_number, proforma_date, "
                + "incoterm, month, status, folder_path, created_at) "
                + "SELECT id, customer, client_order_number, proforma_number, proforma_date, "
                + "incoterm, month, status, folder_path, created_at FROM customer_order");
        statement.execute("DROP TABLE customer_order");
        statement.execute("ALTER TABLE customer_order_new RENAME TO customer_order");
        statement.execute(
            "CREATE UNIQUE INDEX idx_customer_order_active_number "
                + "ON customer_order (customer, client_order_number) "
                + "WHERE status <> 'ARCHIVED'");
        try (ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
          if (violations.next()) {
            throw new SQLException(
                "Order-number migration would leave a foreign-key violation in table "
                    + violations.getString("table"));
          }
        }
        connection.commit();
      } catch (Exception exception) {
        connection.rollback();
        throw exception;
      } finally {
        connection.setAutoCommit(true);
        statement.execute("PRAGMA foreign_keys = ON");
      }
    } finally {
      if (!originalAutoCommit) {
        connection.setAutoCommit(false);
      }
    }
  }
}
