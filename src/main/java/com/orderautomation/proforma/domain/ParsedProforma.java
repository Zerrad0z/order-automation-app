package com.orderautomation.proforma.domain;

import java.time.LocalDate;
import java.util.List;

public record ParsedProforma(
    String proformaNumber,
    String clientOrderNumber,
    LocalDate date,
    String incoterm,
    String customerName,
    List<ProformaLine> lines,
    List<IgnoredProformaLine> ignoredLines,
    Integer declaredSubtotal,
    List<String> warnings) {
  public ParsedProforma {
    lines = List.copyOf(lines);
    ignoredLines = List.copyOf(ignoredLines);
    warnings = List.copyOf(warnings);
  }
}
