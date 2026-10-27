package com.orderautomation.proforma.web;

import com.orderautomation.proforma.domain.ParsedProforma;
import com.orderautomation.proforma.service.ProformaParser;
import com.orderautomation.shared.AppException;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/proformas")
public class ProformaController {
  private static final long MAX_PDF_SIZE = 10L * 1024 * 1024;
  private final ProformaParser proformaParser;
  private final ProformaMapper proformaMapper;

  public ProformaController(ProformaParser proformaParser, ProformaMapper proformaMapper) {
    this.proformaParser = proformaParser;
    this.proformaMapper = proformaMapper;
  }

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ParsedProformaResponse parse(@RequestParam("file") @NotNull MultipartFile file) {
    if (file.isEmpty()) {
      throw invalidFile("Choose a non-empty PDF proforma.");
    }
    if (file.getSize() > MAX_PDF_SIZE) {
      throw new AppException("UPLOAD_TOO_LARGE", "The PDF must be 10 MB or smaller.");
    }
    if (!hasPdfExtension(file.getOriginalFilename())) {
      throw invalidFile("Only PDF proforma files are supported.");
    }
    byte[] content = readContents(file);
    if (content.length < 4
        || content[0] != '%'
        || content[1] != 'P'
        || content[2] != 'D'
        || content[3] != 'F') {
      throw invalidFile("The uploaded file is not a valid PDF.");
    }
    ParsedProforma parsedProforma = proformaParser.parse(content);
    return proformaMapper.toResponse(parsedProforma);
  }

  private boolean hasPdfExtension(String filename) {
    return filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
  }

  private byte[] readContents(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exception) {
      throw new AppException(
          "PROFORMA_READ_FAILED", "The uploaded proforma PDF could not be read.", exception);
    }
  }

  private AppException invalidFile(String message) {
    return new AppException("PROFORMA_INVALID_FILE", message);
  }
}
