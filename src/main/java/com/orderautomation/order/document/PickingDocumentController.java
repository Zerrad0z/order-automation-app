package com.orderautomation.order.document;

import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/{id}/documents")
public class PickingDocumentController {
  private static final MediaType XLSX_MEDIA_TYPE =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final PickingDocumentService pickingDocumentService;

  public PickingDocumentController(PickingDocumentService pickingDocumentService) {
    this.pickingDocumentService = pickingDocumentService;
  }

  @PostMapping("/picking")
  public ResponseEntity<Resource> generatePicking(@PathVariable long id) {
    Path path = pickingDocumentService.generate(id);
    return ResponseEntity.ok()
        .contentType(XLSX_MEDIA_TYPE)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
        .body(new FileSystemResource(path));
  }
}
