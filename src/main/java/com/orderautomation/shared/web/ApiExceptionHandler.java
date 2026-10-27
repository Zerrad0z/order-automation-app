package com.orderautomation.shared.web;

import com.orderautomation.shared.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ProblemDetail> handleAppException(AppException exception) {
    HttpStatus status =
        switch (exception.getCode()) {
          case "UPLOAD_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
          case "ORDER_ALREADY_EXISTS", "ORDER_NOT_DRAFT", "ORDER_NOT_ARCHIVED" ->
              HttpStatus.CONFLICT;
          case "ORDER_NOT_FOUND", "STOCK_ITEM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "PROFORMA_INVALID_FILE", "PROFORMA_NO_TEXT", "PROFORMA_HEADER_MISSING" ->
              HttpStatus.BAD_REQUEST;
          default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setProperty("code", exception.getCode());
    return ResponseEntity.status(status).body(problem);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ProblemDetail> handleMaxUploadSizeExceeded(
      MaxUploadSizeExceededException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE, "The PDF must be 10 MB or smaller.");
    problem.setProperty("code", "UPLOAD_TOO_LARGE");
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
  }
}
