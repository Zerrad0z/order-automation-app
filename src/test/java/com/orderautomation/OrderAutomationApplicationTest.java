package com.orderautomation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "order-automation.data-dir=${java.io.tmpdir}/order-automation-test-data",
      "logging.file.name=${java.io.tmpdir}/order-automation-test-data/app.log"
    })
@AutoConfigureMockMvc
class OrderAutomationApplicationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void healthEndpoint_whenApplicationStarts_returnsUp() throws Exception {
    String response =
        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).contains("\"status\":\"UP\"");
  }

  @Test
  void flywayMigration_whenApplicationStarts_createsOrderTables() {
    Integer tableCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN "
                + "('customer_order', 'order_line', 'stock_item', 'stock_movement', "
                + "'generated_document', 'da_facing_row')",
            Integer.class);

    assertThat(tableCount).isEqualTo(6);
  }

  @Test
  void sqliteConnection_whenApplicationStarts_enforcesForeignKeys() {
    Integer foreignKeys = jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class);

    assertThat(foreignKeys).isEqualTo(1);
  }
}
