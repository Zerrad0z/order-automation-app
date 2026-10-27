package com.orderautomation.order.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import com.orderautomation.order.service.OrderService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class DaFacingDocumentServiceTest {
  @TempDir Path tempDirectory;

  private OrderService orderService;
  private PlateLayoutPlanner layoutPlanner;
  private JdbcTemplate jdbcTemplate;
  private DaFacingDocumentService service;

  @BeforeEach
  void setUp() throws Exception {
    orderService = mock(OrderService.class);
    layoutPlanner = new PlateLayoutPlanner();
    jdbcTemplate = mock(JdbcTemplate.class);
    Path templateDirectory = Files.createDirectories(tempDirectory.resolve("templates"));
    createTemplate(templateDirectory.resolve("DA SVERRE RESTAER 23-10-2026.xlsx"));
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(10);
    service =
        new DaFacingDocumentService(
            orderService,
            layoutPlanner,
            jdbcTemplate,
            Clock.systemUTC(),
            templateDirectory.toString(),
            tempDirectory.resolve("orders").toString());
  }

  @Test
  void generate_whenDraftOrderWritesDemandAndPlateLayoutWithoutHistoryData() throws Exception {
    CustomerOrder order =
        new CustomerOrder(
            12,
            "RESTAER",
            "DA_TEST_ORDER",
            "2600087",
            LocalDate.of(2026, 10, 22),
            "CFR",
            "DRAFT",
            List.of(
                new OrderLine("1", null, "ZEYNA", "BLANC", "10x15", 2),
                new OrderLine("2", null, "ZEYNA", "NOIR", "10X15", 3)));
    when(orderService.get(12)).thenReturn(order);

    Path generated = service.generate(12);

    assertThat(generated).exists();
    try (var workbook = WorkbookFactory.create(generated.toFile())) {
      var summary = workbook.getSheet("RESTAER (2)");
      assertThat(summary.getRow(0).getCell(3).getStringCellValue()).contains("DA_TEST_ORDER");
      assertThat(summary.getRow(6).getCell(0).getDateCellValue())
          .isEqualTo(java.sql.Date.valueOf("2026-10-22"));
      assertThat(summary.getRow(6).getCell(1).getStringCellValue()).isEqualTo("RESTAER");
      assertThat(summary.getRow(6).getCell(2).getStringCellValue()).isEqualTo("DA_TEST_ORDER");
      assertThat(summary.getRow(6).getCell(3).getStringCellValue()).isEqualTo("10X15");
      assertThat(summary.getRow(6).getCell(4).getNumericCellValue()).isEqualTo(20);
      assertThat(summary.getRow(6).getCell(5).getNumericCellValue()).isEqualTo(5);
      assertThat(summary.getRow(6).getCell(7).getStringCellValue()).isEqualTo("93X64");
      assertThat(summary.getRow(6).getCell(12).getCellFormula()).contains("CUTTING_LAYOUTS");
      assertThat(summary.getRow(6).getCell(14).getCellFormula()).isEqualTo("E7+M7-F7");
      assertThat(summary.getRow(6).getCell(3).getStringCellValue()).doesNotContain("OLD");
      assertThat(summary.getRow(7).getCell(3).getCellType()).isEqualTo(CellType.BLANK);
      assertThat(summary.getMergedRegions())
          .noneMatch(region -> region.getFirstRow() >= 6 && region.getLastRow() <= 22);

      var layout = workbook.getSheet("CUTTING_LAYOUTS");
      assertThat(layout.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(1);
      assertThat(layout.getLastRowNum()).isEqualTo(8);
      assertThat(layout.getRow(4).getCell(1).getStringCellValue()).isEqualTo("10X15");
      assertThat(layout.getRow(4).getCell(7).getNumericCellValue()).isEqualTo(1);
    }

    verify(jdbcTemplate)
        .update(
            eq(
                "INSERT INTO generated_document (order_id, type, path, created_at) "
                    + "VALUES (?, ?, ?, ?)"),
            eq(12L),
            eq("DA_FACING"),
            eq(generated.toString()),
            anyString());
  }

  private void createTemplate(Path path) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      var sheet = workbook.createSheet("RESTAER (2)");
      sheet.createRow(0).createCell(3).setCellValue("DA S/VERRE RESTAER");
      var header = sheet.createRow(5);
      header.createCell(3).setCellValue("DESIG CADRE");
      header.createCell(5).setCellValue("BESOIN");
      for (int rowIndex = 6; rowIndex <= 22; rowIndex++) {
        var row = sheet.createRow(rowIndex);
        row.createCell(2).setCellValue("STALE_ORDER");
        row.createCell(3).setCellValue("50X70");
        row.createCell(4).setCellValue(123);
        row.createCell(5).setCellValue(456);
      }
      sheet.addMergedRegion(new CellRangeAddress(6, 22, 0, 0));
      sheet.addMergedRegion(new CellRangeAddress(6, 22, 7, 7));
      try (var output = Files.newOutputStream(path)) {
        workbook.write(output);
      }
    }
  }
}
