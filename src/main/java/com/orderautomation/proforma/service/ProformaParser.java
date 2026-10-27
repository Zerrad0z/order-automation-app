package com.orderautomation.proforma.service;

import com.orderautomation.proforma.domain.IgnoredProformaLine;
import com.orderautomation.proforma.domain.ParsedProforma;
import com.orderautomation.proforma.domain.ProformaLine;
import com.orderautomation.shared.AppException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

@Service
public class ProformaParser {
  private static final Pattern ARTICLE_CODE = Pattern.compile("^\\d{6,7}$");
  private static final Pattern HEADER_PROFORMA =
      Pattern.compile("(?im)N\\s*[°ºo]?\\s*:\\s*(\\d{6,})");
  private static final Pattern HEADER_ORDER =
      Pattern.compile("(?im)N\\s*[°ºo]?\\s*commande\\s+client\\s*:\\s*([A-Za-z0-9_-]+)");
  private static final Pattern HEADER_DATE =
      Pattern.compile("(?i)K.nitra\\s*,?\\s*le\\s*(\\d{2}/\\d{2}/\\d{4})");
  private static final Pattern HEADER_INCOTERM =
      Pattern.compile("(?i)Incoterm\\s*:\\s*([A-Z]{3,})");
  private static final Pattern DESCRIPTION =
      Pattern.compile("(?i)^CADRE\\s+PHOTO\\s+(\\S+)\\s+(\\S+)\\s+(.+)$");
  private static final Pattern SUBTOTAL_WITH_SPACED_DECIMALS =
      Pattern.compile("^([\\d\\s]+)\\s+(\\d{2})$");
  private static final Pattern QUANTITY_WITH_UNIT = Pattern.compile("(?i)^(.+?)\\s*PCS$");
  private static final Pattern DIMENSIONS =
      Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[xX*]\\s*(\\d+(?:[.,]\\d+)?)");
  private static final Set<String> COLORS = Set.of("BLANC", "NOIR", "BOIS", "MARRON");
  private static final DateTimeFormatter PROFORMA_DATE =
      DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

  public ParsedProforma parse(byte[] content) {
    try (PDDocument document = Loader.loadPDF(content)) {
      PositionedTextStripper stripper = new PositionedTextStripper();
      stripper.setSortByPosition(true);
      stripper.getText(document);

      String documentText = stripper.getTextContent();
      if (documentText.isBlank()) {
        throw new AppException(
            "PROFORMA_NO_TEXT",
            "This PDF contains no readable text. Scanned PDFs are not supported.");
      }

      List<String> warnings = new ArrayList<>();
      List<ProformaLine> lines = new ArrayList<>();
      List<IgnoredProformaLine> ignoredLines = new ArrayList<>();
      Integer declaredSubtotal = null;
      for (PositionedRow row : stripper.rows()) {
        String itemCode = row.value(Column.ITEM_CODE).trim();
        String description = row.value(Column.DESCRIPTION).trim();
        if (description.equalsIgnoreCase("Sous-total")) {
          declaredSubtotal = parseSubtotal(row.value(Column.QUANTITY)).orElse(null);
          continue;
        }
        if (!ARTICLE_CODE.matcher(itemCode).matches()
            || !hasPcsUnit(row)
            || row.value(Column.QUANTITY).isBlank()) {
          continue;
        }
        int quantity = parseQuantity(row.value(Column.QUANTITY)).orElse(0);
        if (itemCode.equals("8999999")) {
          ignoredLines.add(
              new IgnoredProformaLine(itemCode, description, quantity, "Transport supplement"));
          continue;
        }
        lines.add(parseLine(itemCode, row, quantity, warnings));
      }

      addDuplicateWarnings(lines, warnings);
      addSubtotalWarning(lines, declaredSubtotal, warnings);
      return new ParsedProforma(
          requiredHeader(HEADER_PROFORMA, documentText, "proforma number"),
          requiredHeader(HEADER_ORDER, documentText, "customer order number"),
          parseDate(documentText),
          findHeader(HEADER_INCOTERM, documentText).orElse(""),
          documentText.contains("RESTAER BUSINESS") ? "RESTAER BUSINESS" : "",
          lines,
          ignoredLines,
          declaredSubtotal,
          warnings);
    } catch (IOException exception) {
      throw new AppException(
          "PROFORMA_READ_FAILED", "The proforma PDF could not be read.", exception);
    }
  }

  private ProformaLine parseLine(
      String itemCode, PositionedRow row, int quantity, List<String> warnings) {
    List<String> invalidReasons = new ArrayList<>();
    String description = row.value(Column.DESCRIPTION).trim();
    Matcher descriptionMatcher = DESCRIPTION.matcher(description);
    String collection = "";
    String color = "";
    String rawFormat = "";
    String format = "";
    if (descriptionMatcher.matches()) {
      collection = descriptionMatcher.group(1).toUpperCase(Locale.ROOT);
      color = descriptionMatcher.group(2).toUpperCase(Locale.ROOT);
      rawFormat = descriptionMatcher.group(3).trim();
      if (!COLORS.contains(color)) {
        invalidReasons.add("Unknown color: " + color);
      }
      format = normalizeFormat(rawFormat).orElse("");
      if (format.isBlank()) {
        invalidReasons.add("Unrecognized frame format: " + rawFormat);
      }
    } else {
      invalidReasons.add("Unrecognized frame description: " + description);
    }
    if (parseQuantity(row.value(Column.QUANTITY)).isEmpty()) {
      invalidReasons.add("Quantity is not a whole number.");
    }
    if (!invalidReasons.isEmpty()) {
      warnings.add("Item " + itemCode + ": " + String.join("; ", invalidReasons));
    }
    return new ProformaLine(
        itemCode,
        row.value(Column.CLIENT_REFERENCE).trim(),
        collection,
        color,
        format,
        rawFormat,
        quantity,
        invalidReasons);
  }

  private void addDuplicateWarnings(List<ProformaLine> lines, List<String> warnings) {
    Set<String> combinations = new HashSet<>();
    for (ProformaLine line : lines) {
      String key = line.collection() + "|" + line.color() + "|" + line.format();
      if (!combinations.add(key)) {
        warnings.add(
            "Duplicate collection, color, and format combination: "
                + line.collection()
                + " "
                + line.color()
                + " "
                + line.format());
      }
    }
  }

  private void addSubtotalWarning(
      List<ProformaLine> lines, Integer declaredSubtotal, List<String> warnings) {
    if (declaredSubtotal == null) {
      warnings.add("The declared subtotal could not be found.");
      return;
    }
    int parsedSubtotal = lines.stream().mapToInt(ProformaLine::quantity).sum();
    if (parsedSubtotal != declaredSubtotal) {
      warnings.add(
          "Parsed quantities differ from the declared subtotal by "
              + (parsedSubtotal - declaredSubtotal)
              + " pieces.");
    }
  }

  private String requiredHeader(Pattern pattern, String text, String field) {
    return findHeader(pattern, text)
        .orElseThrow(
            () ->
                new AppException(
                    "PROFORMA_HEADER_MISSING", "The proforma is missing its " + field + "."));
  }

  private java.util.Optional<String> findHeader(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    return matcher.find()
        ? java.util.Optional.of(matcher.group(1).trim())
        : java.util.Optional.empty();
  }

  private LocalDate parseDate(String text) {
    Matcher matcher = HEADER_DATE.matcher(text);
    if (!matcher.find()) {
      throw new AppException("PROFORMA_HEADER_MISSING", "The proforma is missing its issue date.");
    }
    return LocalDate.parse(matcher.group(1), PROFORMA_DATE);
  }

  private java.util.Optional<String> normalizeFormat(String rawFormat) {
    Matcher matcher = DIMENSIONS.matcher(rawFormat);
    if (!matcher.find()) {
      return java.util.Optional.empty();
    }
    BigDecimal width = parseDimension(matcher.group(1));
    BigDecimal height = parseDimension(matcher.group(2));
    return java.util.Optional.of(dimensionCode(width) + "x" + dimensionCode(height));
  }

  private BigDecimal parseDimension(String value) {
    return new BigDecimal(value.replace(',', '.')).stripTrailingZeros();
  }

  private String dimensionCode(BigDecimal value) {
    return value.toPlainString();
  }

  private java.util.Optional<Integer> parseInteger(String value) {
    if (value == null || value.isBlank()) {
      return java.util.Optional.empty();
    }
    try {
      return java.util.Optional.of(
          new BigDecimal(value.replaceAll("[\\s\\u00a0\\u202f]", "").replace(',', '.'))
              .intValueExact());
    } catch (NumberFormatException | ArithmeticException exception) {
      return java.util.Optional.empty();
    }
  }

  private java.util.Optional<Integer> parseSubtotal(String value) {
    Matcher matcher = SUBTOTAL_WITH_SPACED_DECIMALS.matcher(value.trim());
    if (matcher.matches()) {
      return parseInteger(matcher.group(1) + "." + matcher.group(2));
    }
    return parseInteger(value);
  }

  private boolean hasPcsUnit(PositionedRow row) {
    return "PCS".equalsIgnoreCase(row.value(Column.UNIT).trim())
        || QUANTITY_WITH_UNIT.matcher(row.value(Column.QUANTITY).trim()).matches();
  }

  private java.util.Optional<Integer> parseQuantity(String value) {
    Matcher matcher = QUANTITY_WITH_UNIT.matcher(value.trim());
    String quantity = matcher.matches() ? matcher.group(1).trim() : value;
    return parseInteger(quantity);
  }

  private enum Column {
    ITEM_CODE,
    CLIENT_REFERENCE,
    DESCRIPTION,
    QUANTITY,
    UNIT,
    UNIT_PRICE,
    AMOUNT;

    static Column forX(float x) {
      if (x < 55) {
        return ITEM_CODE;
      }
      if (x < 110) {
        return CLIENT_REFERENCE;
      }
      if (x < 350) {
        return DESCRIPTION;
      }
      if (x < 405) {
        return QUANTITY;
      }
      if (x < 440) {
        return UNIT;
      }
      if (x < 530) {
        return UNIT_PRICE;
      }
      return AMOUNT;
    }
  }

  private record PositionedGlyph(int page, float x, float width, float y, String text) {}

  private record PositionedWord(float x, String text) {}

  private record PositionedRow(int page, float y, Map<Column, String> values) {
    String value(Column column) {
      return values.getOrDefault(column, "");
    }
  }

  private static final class PositionedTextStripper extends PDFTextStripper {
    private final List<PositionedGlyph> glyphs = new ArrayList<>();
    private String textContent = "";

    PositionedTextStripper() throws IOException {}

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
      super.writeString(text, positions);
      for (TextPosition position : positions) {
        glyphs.add(
            new PositionedGlyph(
                getCurrentPageNo(),
                position.getXDirAdj(),
                position.getWidthDirAdj(),
                position.getYDirAdj(),
                position.getUnicode()));
      }
    }

    String getTextContent() {
      return textContent;
    }

    @Override
    public String getText(PDDocument document) throws IOException {
      textContent = super.getText(document);
      return textContent;
    }

    List<PositionedRow> rows() {
      List<PositionedGlyph> ordered =
          glyphs.stream()
              .filter(glyph -> glyph.text() != null && !glyph.text().isEmpty())
              .sorted(
                  Comparator.comparingInt(PositionedGlyph::page)
                      .thenComparingDouble(PositionedGlyph::y)
                      .thenComparingDouble(PositionedGlyph::x))
              .toList();
      List<PositionedRow> rows = new ArrayList<>();
      int index = 0;
      while (index < ordered.size()) {
        PositionedGlyph first = ordered.get(index);
        List<PositionedGlyph> line = new ArrayList<>();
        line.add(first);
        index++;
        while (index < ordered.size()
            && ordered.get(index).page() == first.page()
            && Math.abs(ordered.get(index).y() - first.y()) <= 2.5f) {
          line.add(ordered.get(index++));
        }
        rows.add(buildRow(first.page(), first.y(), line));
      }
      return rows;
    }

    private PositionedRow buildRow(int page, float y, List<PositionedGlyph> glyphs) {
      Map<Column, StringBuilder> values = new HashMap<>();
      for (PositionedWord word : words(glyphs)) {
        Column column = Column.forX(word.x());
        StringBuilder text = values.computeIfAbsent(column, unused -> new StringBuilder());
        if (!text.isEmpty()) {
          text.append(' ');
        }
        text.append(word.text());
      }
      Map<Column, String> rowValues = new HashMap<>();
      values.forEach((column, text) -> rowValues.put(column, text.toString().trim()));
      return new PositionedRow(page, y, rowValues);
    }

    private List<PositionedWord> words(List<PositionedGlyph> glyphs) {
      List<PositionedWord> words = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      float wordStart = 0;
      float previousEnd = 0;
      for (PositionedGlyph glyph : glyphs) {
        boolean crossedColumn =
            !current.isEmpty() && Column.forX(glyph.x()) != Column.forX(wordStart);
        boolean separated =
            glyph.text().isBlank()
                || crossedColumn
                || (!current.isEmpty() && glyph.x() - previousEnd > 1.5f);
        if (separated && !current.isEmpty()) {
          words.add(new PositionedWord(wordStart, current.toString()));
          current.setLength(0);
        }
        if (!glyph.text().isBlank()) {
          if (current.isEmpty()) {
            wordStart = glyph.x();
          }
          current.append(glyph.text());
        }
        previousEnd = glyph.x() + glyph.width();
      }
      if (!current.isEmpty()) {
        words.add(new PositionedWord(wordStart, current.toString()));
      }
      return words;
    }
  }
}
