package com.orderautomation.order.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "order-automation.data-dir=${java.io.tmpdir}/order-automation-test-data",
      "logging.file.name=${java.io.tmpdir}/order-automation-test-data/app.log"
    })
@AutoConfigureMockMvc
class OrderControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void draftOrder_whenCreatedAndLinesReplaced_persistsAndReturnsOrder() throws Exception {
    String orderNumber = "TEST_" + UUID.randomUUID().toString().replace("-", "");
    Map<String, Object> request = createRequest(orderNumber);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customer").value("RESTAER"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.lines[0].quantity").value(2))
            .andReturn();

    JsonNode createdOrder = objectMapper.readTree(created.getResponse().getContentAsString());
    long orderId = createdOrder.get("id").asLong();
    Map<String, Object> correctedLine =
        Map.of(
            "itemCode", "9340300",
            "clientRef", "208118",
            "collection", "ZEYNA",
            "color", "BLANC",
            "format", "10x15",
            "quantity", 3);

    mockMvc
        .perform(
            put("/api/orders/{id}/lines", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("lines", List.of(correctedLine)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].quantity").value(3));

    mockMvc
        .perform(get("/api/orders/{id}", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientOrderNumber").value(orderNumber))
        .andExpect(jsonPath("$.lines[0].quantity").value(3));
    mockMvc
        .perform(get("/api/orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].clientOrderNumber").exists());
    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_ALREADY_EXISTS"));
  }

  @Test
  void draftOrder_whenOrderNumberContainsPathCharacters_rejectsRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest("../outside"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void draftOrder_whenArchivedAndRestored_preservesOrderAndProtectsGeneratedStatus()
      throws Exception {
    String orderNumber = "ARCHIVE_" + UUID.randomUUID().toString().replace("-", "");
    MvcResult created =
        mockMvc
            .perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest(orderNumber))))
            .andExpect(status().isCreated())
            .andReturn();
    long orderId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(post("/api/orders/{id}/archive", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"))
        .andExpect(jsonPath("$.lines[0].quantity").value(2));
    mockMvc
        .perform(post("/api/orders/{id}/restore", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));

    jdbcTemplate.update("UPDATE customer_order SET status = 'GENERATED' WHERE id = ?", orderId);
    mockMvc
        .perform(post("/api/orders/{id}/archive", orderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_DRAFT"));
    mockMvc
        .perform(get("/api/orders/{id}", orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("GENERATED"));
  }

  @Test
  void draftOrder_whenArchivedAllowsNumberReuseButCannotRestoreDuplicate() throws Exception {
    String orderNumber = "REUSE_" + UUID.randomUUID().toString().replace("-", "");
    MvcResult created =
        mockMvc
            .perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest(orderNumber))))
            .andExpect(status().isCreated())
            .andReturn();
    long archivedOrderId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

    mockMvc.perform(post("/api/orders/{id}/archive", archivedOrderId)).andExpect(status().isOk());
    MvcResult replacement =
        mockMvc
            .perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest(orderNumber))))
            .andExpect(status().isCreated())
            .andReturn();
    long replacementOrderId =
        objectMapper.readTree(replacement.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest(orderNumber))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_ALREADY_EXISTS"));
    mockMvc
        .perform(post("/api/orders/{id}/restore", archivedOrderId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORDER_ALREADY_EXISTS"));
    mockMvc
        .perform(get("/api/orders/{id}", archivedOrderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
    mockMvc
        .perform(get("/api/orders/{id}", replacementOrderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  private Map<String, Object> createRequest(String orderNumber) {
    return Map.of(
        "clientOrderNumber",
        orderNumber,
        "proformaNumber",
        "2600087",
        "proformaDate",
        "2026-10-22",
        "incoterm",
        "CFR",
        "lines",
        List.of(
            Map.of(
                "itemCode",
                "9340300",
                "clientRef",
                "208118",
                "collection",
                "ZEYNA",
                "color",
                "BLANC",
                "format",
                "10x15",
                "quantity",
                2)));
  }
}
