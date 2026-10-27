package com.orderautomation.stock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class StockControllerTest {
  private static final Path TEST_DATA_DIR =
      Path.of(
          System.getProperty("java.io.tmpdir"), "order-automation-stock-test-" + UUID.randomUUID());

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void registerTestProperties(DynamicPropertyRegistry registry) {
    registry.add("order-automation.data-dir", () -> TEST_DATA_DIR.toString());
    registry.add("logging.file.name", () -> TEST_DATA_DIR.resolve("app.log").toString());
  }

  @Test
  void stock_whenListedAndAdjusted_recordsAnAuditedMovement() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/stock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(48))
            .andExpect(jsonPath("$[0].quantity").value(999999))
            .andReturn();

    JsonNode firstItem = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
    long id = firstItem.get("id").asLong();
    int currentQuantity = firstItem.get("quantity").asInt();
    int adjustedQuantity = currentQuantity + 1;
    String reason = "Test adjustment " + UUID.randomUUID();

    mockMvc
        .perform(
            put("/api/stock/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("quantity", adjustedQuantity, "reason", reason))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.quantity").value(adjustedQuantity));

    Integer recordedDelta =
        jdbcTemplate.queryForObject(
            "SELECT delta FROM stock_movement WHERE stock_item_id = ? AND reason = ?",
            Integer.class,
            id,
            reason);
    assertThat(recordedDelta).isEqualTo(1);

    mockMvc
        .perform(get("/api/stock/{id}/movements", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].delta").value(1))
        .andExpect(jsonPath("$[0].reason").value(reason));

    mockMvc
        .perform(
            put("/api/stock/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("quantity", currentQuantity, "reason", "Restore test inventory"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantity").value(currentQuantity));
  }

  @Test
  void stock_whenQuantityIsNegative_rejectsTheUpdate() throws Exception {
    mockMvc
        .perform(
            put("/api/stock/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("quantity", -1, "reason", "count"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void stock_whenItemDoesNotExist_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            put("/api/stock/{id}", 999999)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("quantity", 10, "reason", "count"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STOCK_ITEM_NOT_FOUND"));
  }
}
