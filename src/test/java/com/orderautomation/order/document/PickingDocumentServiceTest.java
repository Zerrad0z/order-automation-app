package com.orderautomation.order.document;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class PickingDocumentServiceTest {
  @TempDir Path tempDirectory;

  private OrderService orderService;
  private JdbcTemplate jdbcTemplate;
  private PickingDocumentService service;

  @BeforeEach
  void setUp() throws Exception {
    orderService = mock(OrderService.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    Path templateDirectory = Files.createDirectories(tempDirectory.resolve("templates"));
    createTemplate(templateDirectory.resolve("fiche_de_ramassage_ZEYNA.xlsx"));
    service =
        new PickingDocumentService(
            orderService,
            jdbcTemplate,
            Clock.systemUTC(),
            templateDirectory.toString(),
            tempDirectory.resolve("orders").toString());
  }

  @Test
  void generate_whenDraftOrderWritesQuantitiesAndPreservesTemplateFormulas() throws Exception {
    CustomerOrder order =
        new CustomerOrder(
            7,
            "RESTAER",
            "TEST_ORDER",
            "2600087",
            LocalDate.of(2026, 10, 22),
            "CFR",
            "DRAFT",
            List.of(
                new OrderLine("1", null, "ZEYNA", "BLANC", "10x15", 5),
                new OrderLine("2", null, "ZEYNA", "BLANC", "10X15", 3),
                new OrderLine("3", null, "ZEYNA", "NOIR", "30x40", 2)));
    when(orderService.get(7)).thenReturn(order);

    Path generated = service.generate(7);

    assertThat(generated).exists();
    assertThat(generated.getParent())
        .isEqualTo(tempDirectory.resolve("orders").resolve("TEST_ORDER"));
    try (var workbook = WorkbookFactory.create(generated.toFile())) {
      var sheet = workbook.getSheet("RESTAER");
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("RESTAER");
      assertThat(sheet.getRow(1).getCell(5).getStringCellValue()).contains("TEST_ORDER");
      assertThat(sheet.getRow(9).getCell(2).getNumericCellValue()).isEqualTo(8);
      assertThat(sheet.getRow(10).getCell(10).getNumericCellValue()).isEqualTo(2);
      assertThat(sheet.getRow(9).getCell(1).getCellType().name()).isEqualTo("BLANK");
      assertThat(sheet.getRow(9).getCell(13).getCellFormula()).isEqualTo("SUM(B10:M10)");
      assertThat(sheet.getRow(17).getCell(1).getCellFormula()).contains("SUM(B10:B13)");
    }

    verify(jdbcTemplate)
        .update(
            org.mockito.ArgumentMatchers.eq(
                "INSERT INTO generated_document (order_id, type, path, created_at) "
                    + "VALUES (?, ?, ?, ?)"),
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("PICKING"),
            org.mockito.ArgumentMatchers.eq(generated.toString()),
            org.mockito.ArgumentMatchers.anyString());
  }

  private void createTemplate(Path path) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      var sheet = workbook.createSheet("RESTAER");
      sheet.createRow(0).createCell(0).setCellValue("FICHE DE RAMASSAGE");
      var header = sheet.createRow(1);
      header.createCell(0).setCellValue("CLIENT :  RESTAER");
      header.createCell(5).setCellValue("CMD N° :  M20261020");
      header.createCell(10).setCellValue(46319);
      var dimensions = sheet.createRow(3);
      String[] formats = {
        "9X13", "10X15", "13X18", "15X20", "18X24", "20X25", "21X29,7", "24X30", "29,7X42", "30X40",
        "40X50", "50X70"
      };
      for (int index = 0; index < formats.length; index++) {
        dimensions.createCell(index + 1).setCellValue(formats[index]);
      }
      String[] colors = {
        "ZEYNA BLANCO (Blanc)", "ZEYNA NEGRO (Noir)", "ZEYNA MARRON", "ZEYNA MADERA (Bois)"
      };
      for (int index = 0; index < colors.length; index++) {
        var row = sheet.createRow(index + 9);
        row.createCell(0).setCellValue(colors[index]);
        row.createCell(1).setCellValue(100 + index);
      }
      sheet.getRow(9).createCell(13).setCellFormula("SUM(B10:M10)");
      sheet.createRow(17).createCell(1).setCellFormula("SUM(B10:B13)");
      try (var output = Files.newOutputStream(path)) {
        workbook.write(output);
      }
    }
  }
}
