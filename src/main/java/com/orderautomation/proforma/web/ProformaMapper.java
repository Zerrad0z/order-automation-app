package com.orderautomation.proforma.web;

import com.orderautomation.proforma.domain.ParsedProforma;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProformaMapper {
  ParsedProformaResponse toResponse(ParsedProforma parsedProforma);
}
