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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DaFacingDocumentService {
  private static final String TEMPLATE_NAME = "DA SVERRE RESTAER 23-10-2026.xlsx";
  private static final String SUMMARY_SHEET = "RESTAER (2)";
  private static final String LAYOUT_SHEET = "CUTTING_LAYOUTS";
  private static final int FIRST_DATA_ROW = 6;
  private static final int LAST_DATA_ROW = 22;
  private static final String PLATE_DIMENSIONS = "93X64";
  private static final String STOCK_QUERY =
      "SELECT COALESCE(SUM(quantity), 0) FROM stock_item "
          + "WHERE customer = ? AND collection = ? AND color = ? AND format = ?";

  private final OrderService orderService;
  private final PlateLayoutPlanner layoutPlanner;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;
  private final Path templatePath;
  private final Path outputDirectory;

  public DaFacingDocumentService(
      OrderService orderService,
      PlateLayoutPlanner layoutPlanner,
      JdbcTemplate jdbcTemplate,
      Clock clock,
      @Value("${order-automation.template-dir:}") String templateDirectory,
      @Value("${order-automation.output-dir:}") String outputDirectory) {
    this.orderService = orderService;
    this.layoutPlanner = layoutPlanner;
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
      throw new AppException("DA_TEMPLATE_MISSING", "DA template was not found: " + templatePath);
    }

    PlateLayoutPlanner.Plan plan = layoutPlanner.plan(order.lines());
    List<DimensionDemand> demands = dimensionDemands(order);
    if (demands.size() > LAST_DATA_ROW - FIRST_DATA_ROW + 1) {
      throw new AppException(
          "DA_TOO_MANY_DIMENSIONS", "The DA workbook supports up to 17 order dimensions.");
    }
    Path orderDirectory = outputDirectory.resolve(order.clientOrderNumber()).normalize();
    if (!orderDirectory.startsWith(outputDirectory)
        || !orderDirectory.getFileName().toString().equals(order.clientOrderNumber())) {
      throw new AppException("ORDER_INVALID_NUMBER", "The order number is not a safe file name.");
    }

    Path outputPath = orderDirectory.resolve("da_facing.xlsx");
    Path temporaryPath = null;
    try {
      Files.createDirectories(orderDirectory);
      temporaryPath = Files.createTempFile(orderDirectory, "da-facing-", ".xlsx");
      writeWorkbook(order, demands, plan, temporaryPath);
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
          "DA_GENERATION_FAILED", "Could not write the DA facing workbook.", exception);
    }

    jdbcTemplate.update(
        "INSERT INTO generated_document (order_id, type, path, created_at) VALUES (?, ?, ?, ?)",
        order.id(),
        "DA_FACING",
        outputPath.toString(),
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).toString());
    return outputPath;
  }

  private void writeWorkbook(
      CustomerOrder order,
      List<DimensionDemand> demands,
      PlateLayoutPlanner.Plan plan,
      Path outputPath)
      throws IOException {
    try (Workbook workbook = WorkbookFactory.create(templatePath.toFile())) {
      Sheet summary = workbook.getSheet(SUMMARY_SHEET);
      if (summary == null) {
        throw new AppException(
            "DA_TEMPLATE_INVALID", "The DA template has no RESTAER (2) worksheet.");
      }
      validateTemplate(summary);
      prepareSummaryRows(summary);
      Cell title = cell(summary, 0, 3);
      title.setCellValue("DA S/VERRE RESTAER - " + order.clientOrderNumber());

      for (int index = 0; index < demands.size(); index++) {
        writeDemandRow(summary, FIRST_DATA_ROW + index, order, demands.get(index));
      }
      writeLayoutSheet(workbook, order, plan);
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
          "DA_TEMPLATE_INVALID",
          "The DA workbook could not be read or its expected cells are missing.",
          exception);
    }
  }

  private void validateTemplate(Sheet sheet) {
    if (!cell(sheet, 5, 3).getStringCellValue().contains("DESIG")
        || !cell(sheet, 5, 5).getStringCellValue().contains("BESOIN")) {
      throw new AppException(
          "DA_TEMPLATE_INVALID", "The selected file is not the expected DA template.");
    }
  }

  private void prepareSummaryRows(Sheet sheet) {
    for (int index = sheet.getNumMergedRegions() - 1; index >= 0; index--) {
      var region = sheet.getMergedRegion(index);
      if (region.getFirstRow() >= FIRST_DATA_ROW
          && region.getLastRow() <= LAST_DATA_ROW
          && region.getLastRow() > region.getFirstRow()) {
        sheet.removeMergedRegion(index);
      }
    }
    Row firstRow = sheet.getRow(FIRST_DATA_ROW);
    List<CellStyle> styles = new ArrayList<>();
    for (int column = 0; column < 16; column++) {
      Cell sourceCell = firstRow == null ? null : firstRow.getCell(column);
      styles.add(sourceCell == null ? null : sourceCell.getCellStyle());
    }
    for (int rowIndex = FIRST_DATA_ROW; rowIndex <= LAST_DATA_ROW; rowIndex++) {
      Row row = sheet.getRow(rowIndex);
      if (row == null) {
        row = sheet.createRow(rowIndex);
      }
      for (int column = 0; column < 16; column++) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setBlank();
        CellStyle style = styles.get(column);
        if (style != null) {
          cell.setCellStyle(style);
        }
      }
    }
  }

  private void writeDemandRow(
      Sheet sheet, int rowIndex, CustomerOrder order, DimensionDemand demand) {
    int excelRow = rowIndex + 1;
    cell(sheet, rowIndex, 0).setCellValue(Date.valueOf(order.proformaDate()));
    cell(sheet, rowIndex, 1).setCellValue(order.customer());
    cell(sheet, rowIndex, 2).setCellValue(order.clientOrderNumber());
    cell(sheet, rowIndex, 3).setCellValue(formatForWorkbook(demand.format()));
    cell(sheet, rowIndex, 4).setCellValue(stockBeforeOrder(order, demand));
    cell(sheet, rowIndex, 5).setCellValue(demand.quantity());
    cell(sheet, rowIndex, 6).setCellFormula("E" + excelRow + "-F" + excelRow);
    cell(sheet, rowIndex, 7).setCellValue(PLATE_DIMENSIONS);
    cell(sheet, rowIndex, 12)
        .setCellFormula(
            "SUMIF(" + LAYOUT_SHEET + "!$B:$B,D" + excelRow + "," + LAYOUT_SHEET + "!$H:$H)");
    cell(sheet, rowIndex, 14).setCellFormula("E" + excelRow + "+M" + excelRow + "-F" + excelRow);
  }

  private int stockBeforeOrder(CustomerOrder order, DimensionDemand demand) {
    int stock = 0;
    for (String color : demand.colors()) {
      Integer quantity =
          jdbcTemplate.queryForObject(
              STOCK_QUERY,
              Integer.class,
              order.customer(),
              "ZEYNA",
              color,
              demand.format().toLowerCase(Locale.ROOT));
      stock += quantity == null ? 0 : quantity;
    }
    return stock;
  }

  private List<DimensionDemand> dimensionDemands(CustomerOrder order) {
    Map<String, DimensionDemandBuilder> byFormat = new LinkedHashMap<>();
    for (OrderLine line : order.lines()) {
      String key =
          line.format()
              .replace("×", "x")
              .replace(',', '.')
              .replaceAll("\\s+", "")
              .toLowerCase(Locale.ROOT);
      DimensionDemandBuilder demand =
          byFormat.computeIfAbsent(key, unused -> new DimensionDemandBuilder(line.format()));
      demand.add(line.quantity(), line.color());
    }
    return byFormat.values().stream().map(DimensionDemandBuilder::build).toList();
  }

  private String formatForWorkbook(String format) {
    return format.toUpperCase(Locale.ROOT).replace('.', ',');
  }

  private void writeLayoutSheet(
      Workbook workbook, CustomerOrder order, PlateLayoutPlanner.Plan plan) {
    Sheet layout = workbook.createSheet(LAYOUT_SHEET);
    Cell title = layout.createRow(0).createCell(0);
    title.setCellValue("PLAN DE DECOUPE - " + order.clientOrderNumber());
    CellStyle titleStyle = workbook.createCellStyle();
    Font titleFont = workbook.createFont();
    titleFont.setBold(true);
    titleFont.setFontHeightInPoints((short) 14);
    titleStyle.setFont(titleFont);
    title.setCellStyle(titleStyle);
    Row orderRow = layout.createRow(1);
    orderRow.createCell(0).setCellValue("Client");
    orderRow.createCell(1).setCellValue(order.customer());
    orderRow.createCell(2).setCellValue("Commande");
    orderRow.createCell(3).setCellValue(order.clientOrderNumber());
    orderRow.createCell(4).setCellValue("Date");
    orderRow.createCell(5).setCellValue(Date.valueOf(order.proformaDate()));
    orderRow.createCell(6).setCellValue("Plaques");
    orderRow.createCell(7).setCellValue(plan.plates().size());

    Row header = layout.createRow(3);
    String[] headers = {
      "N° PLAQUE",
      "DIMENSION",
      "X (mm)",
      "Y (mm)",
      "LARGEUR (mm)",
      "HAUTEUR (mm)",
      "ROTATION 90°",
      "QTE"
    };
    CellStyle headerStyle = workbook.createCellStyle();
    Font headerFont = workbook.createFont();
    headerFont.setBold(true);
    headerStyle.setFont(headerFont);
    headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    for (int index = 0; index < headers.length; index++) {
      Cell cell = header.createCell(index);
      cell.setCellValue(headers[index]);
      cell.setCellStyle(headerStyle);
    }
    int rowIndex = 4;
    for (PlateLayoutPlanner.Plate plate : plan.plates()) {
      for (PlateLayoutPlanner.PlacedPiece piece : plate.pieces()) {
        Row row = layout.createRow(rowIndex++);
        row.createCell(0).setCellValue(plate.number());
        row.createCell(1).setCellValue(formatForWorkbook(piece.format()));
        row.createCell(2).setCellValue(piece.xMm());
        row.createCell(3).setCellValue(piece.yMm());
        row.createCell(4).setCellValue(piece.widthMm());
        row.createCell(5).setCellValue(piece.heightMm());
        row.createCell(6).setCellValue(piece.rotated() ? "OUI" : "NON");
        row.createCell(7).setCellValue(1);
      }
    }
    layout.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, rowIndex - 1, 0, 7));
    layout.createFreezePane(0, 4);
    int[] widths = {12, 16, 12, 12, 16, 16, 18, 8};
    for (int index = 0; index < widths.length; index++) {
      layout.setColumnWidth(index, widths[index] * 256);
    }
  }

  private Cell cell(Sheet sheet, int rowIndex, int columnIndex) {
    Row row = sheet.getRow(rowIndex);
    if (row == null) {
      row = sheet.createRow(rowIndex);
    }
    return row.getCell(columnIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
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

  private static final class DimensionDemandBuilder {
    private final String format;
    private final Set<String> colors = new LinkedHashSet<>();
    private int quantity;

    private DimensionDemandBuilder(String format) {
      this.format = format;
    }

    private void add(int amount, String color) {
      try {
        quantity = Math.addExact(quantity, amount);
      } catch (ArithmeticException exception) {
        throw new AppException(
            "DA_LAYOUT_QUANTITY_TOO_LARGE",
            "The requested quantity for " + format + " is too large.",
            exception);
      }
      colors.add(color.toUpperCase(Locale.ROOT));
    }

    private DimensionDemand build() {
      return new DimensionDemand(format, quantity, Set.copyOf(colors));
    }
  }

  private record DimensionDemand(String format, int quantity, Set<String> colors) {}
}
