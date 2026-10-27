package com.orderautomation.proforma.web;

import java.time.LocalDate;
import java.util.List;

public record ParsedProformaResponse(
    String proformaNumber,
    String clientOrderNumber,
    LocalDate date,
    String incoterm,
    String customerName,
    List<ProformaLineResponse> lines,
    List<IgnoredProformaLineResponse> ignoredLines,
    Integer declaredSubtotal,
    List<String> warnings) {}
