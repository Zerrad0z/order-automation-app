package com.orderautomation.order.document;

import com.orderautomation.order.domain.CustomerOrder;
import com.orderautomation.order.domain.OrderLine;
import com.orderautomation.order.service.OrderService;
import com.orderautomation.shared.AppException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PickingDocumentService {
  private static final String TEMPLATE_NAME = "fiche_de_ramassage_ZEYNA.xlsx";
  private static final String SHEET_NAME = "RESTAER";
  private static final Map<String, Integer> COLOR_ROWS =
      Map.of("BLANC", 9, "NOIR", 10, "MARRON", 11, "BOIS", 12);

  private final OrderService orderService;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final Path templatePath;
  private final Path outputDirectory;

  public PickingDocumentService(
      OrderService orderService,
      JdbcTemplate jdbcTemplate,
      Clock clock,
      @Value("${order-automation.template-dir:}") String templateDirectory,
      @Value("${order-automation.output-dir:}") String outputDirectory) {
    this.orderService = orderService;
    this.jdbcTemplate = jdbcTemplate;
    this.clock = clock;
    this.templatePath =
        (templateDirectory.isBlank()
                ? Path.of(System.getProperty("user.dir")).resolve("docs")
                : Path.of(templateDirectory))
            .resolve(TEMPLATE_NAME)
            .toAbsolutePath()
            .normalize();
    this.outputDirectory =
        outputDirectory.isBlank()
            ? Path.of(System.getProperty("user.home"), "OrderAutomation", "orders")
            : Path.of(outputDirectory).toAbsolutePath().normalize();
  }

  @Transactional
  public Path generate(long orderId) {
    CustomerOrder order = orderService.get(orderId);
    if (!order.status().equals("DRAFT")) {
      throw new AppException("ORDER_NOT_DRAFT", "Only draft orders can generate documents.");
    }
    if (!Files.isRegularFile(templatePath)) {
      throw new AppException(
          "PICKING_TEMPLATE_MISSING", "Picking template was not found: " + templatePath);
    }

    Path orderDirectory = outputDirectory.resolve(order.clientOrderNumber()).normalize();
    if (!orderDirectory.startsWith(outputDirectory)
        || !orderDirectory.getFileName().toString().equals(order.clientOrderNumber())) {
      throw new AppException("ORDER_INVALID_NUMBER", "The order number is not a safe file name.");
    }
    Path outputPath = orderDirectory.resolve("fiche_ramassage.xlsx");
    Path temporaryPath = null;
    try {
      Files.createDirectories(orderDirectory);
      temporaryPath = Files.createTempFile(orderDirectory, "picking-", ".xlsx");
      writeWorkbook(order, temporaryPath);
      try {
        Files.move(
            temporaryPath,
            outputPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException exception) {
        Files.move(temporaryPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (AppException exception) {
      deleteTemporaryFile(temporaryPath, exception);
      throw exception;
    } catch (IOException exception) {
      deleteTemporaryFile(temporaryPath, exception);
      throw new AppException(
          "PICKING_GENERATION_FAILED", "Could not write the picking workbook.", exception);
    }

    jdbcTemplate.update(
        "INSERT INTO generated_document (order_id, type, path, created_at) VALUES (?, ?, ?, ?)",
        order.id(),
        "PICKING",
        outputPath.toString(),
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString());
    return outputPath;
  }

  private void writeWorkbook(CustomerOrder order, Path outputPath) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(templatePath.toFile())) {
      Sheet sheet = workbook.getSheet(SHEET_NAME);
      if (sheet == null) {
        throw new AppException(
            "PICKING_TEMPLATE_INVALID", "The picking template has no RESTAER sheet.");
      }
      validateTemplate(sheet);
      sheet.getRow(1).getCell(0).setCellValue("CLIENT :  " + order.customer());
      sheet.getRow(1).getCell(5).setCellValue("CMD N° :  " + order.clientOrderNumber());
      sheet.getRow(1).getCell(10).setCellValue(Date.valueOf(order.proformaDate()));

      Map<String, Integer> dimensionColumns = findDimensions(sheet);
      for (int row = 9; row <= 12; row++) {
        for (int column = 1; column <= 12; column++) {
          cell(sheet, row, column).setBlank();
        }
      }
      for (OrderLine line : order.lines()) {
        if (!line.collection().equalsIgnoreCase("ZEYNA")) {
          throw new AppException(
              "PICKING_UNSUPPORTED_ITEM",
              "The selected picking template supports ZEYNA items only.");
        }
        Integer row = COLOR_ROWS.get(line.color().toUpperCase(Locale.ROOT));
        Integer column = dimensionColumns.get(normalizeDimension(line.format()));
        if (row == null || column == null) {
          throw new AppException(
              "PICKING_UNSUPPORTED_ITEM",
              "The picking template does not support " + line.color() + " " + line.format() + ".");
        }
        Cell quantityCell = cell(sheet, row, column);
        double existingQuantity =
            quantityCell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                ? quantityCell.getNumericCellValue()
                : 0;
        quantityCell.setCellValue(existingQuantity + line.quantity());
      }
      workbook.setForceFormulaRecalculation(true);
      try (OutputStream output = Files.newOutputStream(outputPath)) {
        workbook.write(output);
      }
    } catch (AppException exception) {
      throw exception;
    } catch (IOException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AppException(
          "PICKING_TEMPLATE_INVALID",
          "The picking template could not be read or its expected cells are missing.",
          exception);
    }
  }

  private void validateTemplate(Sheet sheet) {
    String title = cell(sheet, 0, 0).getStringCellValue();
    if (!title.contains("FICHE DE RAMASSAGE")
        || !cell(sheet, 1, 0).getStringCellValue().contains("CLIENT")
        || !cell(sheet, 1, 5).getStringCellValue().contains("CMD")
        || !cell(sheet, 9, 0).getStringCellValue().contains("BLANCO")
        || !cell(sheet, 10, 0).getStringCellValue().contains("NEGRO")
        || !cell(sheet, 11, 0).getStringCellValue().contains("MARRON")
        || !cell(sheet, 12, 0).getStringCellValue().contains("MADERA")) {
      throw new AppException(
          "PICKING_TEMPLATE_INVALID", "The selected file is not the expected picking template.");
    }
    findDimensions(sheet);
  }

  private Map<String, Integer> findDimensions(Sheet sheet) {
    Map<String, Integer> columns = new HashMap<>();
    for (int column = 1; column <= 12; column++) {
      String dimension = normalizeDimension(cell(sheet, 3, column).getStringCellValue());
      if (columns.put(dimension, column) != null) {
        throw new AppException(
            "PICKING_TEMPLATE_INVALID", "The picking template has duplicate dimensions.");
      }
    }
    return columns;
  }

  private Cell cell(Sheet sheet, int rowIndex, int columnIndex) {
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      row = sheet.createRow(rowIndex);
    }
    return row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
  }

  private String normalizeDimension(String dimension) {
    return dimension
        .replace("×", "x")
        .replace(',', '.')
        .replaceAll("\\s+", "")
        .toLowerCase(Locale.ROOT);
  }

  private void deleteTemporaryFile(Path temporaryPath, Throwable failure) {
    if (temporaryPath == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporaryPath);
    } catch (IOException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }
}
