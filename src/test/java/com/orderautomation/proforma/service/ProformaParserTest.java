package com.orderautomation.proforma.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderautomation.proforma.domain.ParsedProforma;
import com.orderautomation.shared.AppException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class ProformaParserTest {
  private final ProformaParser parser = new ProformaParser();

  @Test
  void parse_whenPositionsSeparateTableColumns_groupsAndNormalizesRows() throws IOException {
    byte[] pdf =
        createProforma(
            List.of(
                new ProductLine("9340300", "208118", "10X15CM", "2 892,00"),
                new ProductLine("9340571", "208124", "29.7X42CM", "1 584,00"),
                new ProductLine("8999999", "", "transport", "1,00")));

    ParsedProforma result = parser.parse(pdf);

    assertThat(result.proformaNumber()).isEqualTo("2600087");
    assertThat(result.clientOrderNumber()).isEqualTo("M20261020");
    assertThat(result.date()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(result.incoterm()).isEqualTo("CFR");
    assertThat(result.customerName()).isEqualTo("RESTAER BUSINESS");
    assertThat(result.lines()).as("Parsed proforma: %s", result).hasSize(2);
    assertThat(result.lines())
        .extracting(line -> line.format())
        .containsExactly("10x15", "29.7x42");
    assertThat(result.lines()).extracting(line -> line.quantity()).containsExactly(2892, 1584);
    assertThat(result.ignoredLines())
        .singleElement()
        .extracting(line -> line.itemCode())
        .isEqualTo("8999999");
    assertThat(result.declaredSubtotal()).isEqualTo(4476);
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void parse_whenUnitSharesQuantityColumn_parsesQuantityAndSubtotal() throws IOException {
    byte[] pdf =
        createProforma(List.of(new ProductLine("9340300", "208118", "10X15CM", "2 892,00")), true);

    ParsedProforma result = parser.parse(pdf);

    assertThat(result.lines()).singleElement().extracting(line -> line.quantity()).isEqualTo(2892);
    assertThat(result.declaredSubtotal()).isEqualTo(2892);
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void parse_whenFormatSeparatorsVary_normalizesAllSupportedDimensions() throws IOException {
    byte[] pdf =
        createProforma(
            List.of(
                new ProductLine("9340300", "208118", "10X15CM", "1,00"),
                new ProductLine("9340301", "208119", "40x50 cm", "1,00"),
                new ProductLine("9340302", "208120", "9 *13 cm", "1,00"),
                new ProductLine("9340303", "208121", "29.7X42CM", "1,00"),
                new ProductLine("9340304", "208122", "20 *25 cm", "1,00")));

    ParsedProforma result = parser.parse(pdf);

    assertThat(result.lines())
        .extracting(line -> line.format())
        .containsExactly("10x15", "40x50", "9x13", "29.7x42", "20x25");
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void parse_whenDescriptionContainsUnknownColor_keepsInvalidLineAndWarns() throws IOException {
    byte[] pdf =
        createProforma(List.of(new ProductLine("9340300", "208118", "10X15CM", "1,00", "ROUGE")));

    ParsedProforma result = parser.parse(pdf);

    assertThat(result.lines()).hasSize(1);
    assertThat(result.lines().getFirst().color()).isEqualTo("ROUGE");
    assertThat(result.lines().getFirst().invalidReasons()).contains("Unknown color: ROUGE");
    assertThat(result.warnings()).anyMatch(warning -> warning.contains("Unknown color: ROUGE"));
  }

  @Test
  void parse_whenPdfContainsNoText_returnsClearError() throws IOException {
    byte[] pdf;
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      document.addPage(new PDPage(PDRectangle.A4));
      document.save(output);
      pdf = output.toByteArray();
    }

    assertThatThrownBy(() -> parser.parse(pdf))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Scanned PDFs are not supported.");
  }

  private byte[] createProforma(List<ProductLine> lines) throws IOException {
    return createProforma(lines, false);
  }

  private byte[] createProforma(List<ProductLine> lines, boolean unitInQuantityColumn)
      throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.A4);
      document.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
        writeAt(stream, 20, 800, "N°:2600087");
        writeAt(stream, 20, 780, "N° commande client : M20261020");
        writeAt(stream, 20, 760, "Kenitra , le 22/10/2026");
        writeAt(stream, 20, 740, "Incoterm : CFR");
        writeAt(stream, 20, 720, "RESTAER BUSINESS");
        float y = 600;
        int subtotal = 0;
        for (ProductLine line : lines) {
          writeAt(stream, 19, y, line.itemCode());
          writeAt(stream, 69, y, line.clientRef());
          writeAt(stream, 122, y, "CADRE PHOTO ZEYNA " + line.color() + " " + line.format());
          writeAt(
              stream, 368, y, unitInQuantityColumn ? line.quantity() + " PCS" : line.quantity());
          if (!unitInQuantityColumn) {
            writeAt(stream, 406, y, "PCS");
          }
          if (!line.itemCode().equals("8999999")) {
            subtotal += quantity(line.quantity());
          }
          y -= 18;
        }
        writeAt(stream, 122, y, "Sous-total");
        writeAt(
            stream, 368, y, String.format(Locale.ROOT, "%,d,00", subtotal).replaceFirst(",", " "));
      }
      document.save(output);
      return output.toByteArray();
    }
  }

  private void writeAt(PDPageContentStream stream, float x, float y, String text)
      throws IOException {
    stream.beginText();
    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
    stream.newLineAtOffset(x, y);
    stream.showText(text);
    stream.endText();
  }

  private int quantity(String value) {
    return new java.math.BigDecimal(value.replace(" ", "").replace(',', '.')).intValueExact();
  }

  private record ProductLine(
      String itemCode, String clientRef, String format, String quantity, String color) {
    ProductLine(String itemCode, String clientRef, String format, String quantity) {
      this(itemCode, clientRef, format, quantity, "BLANC");
    }
  }
}
