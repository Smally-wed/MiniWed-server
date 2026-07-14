package smally.server.domain.template.service;

import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.dto.VariantResponse;


public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest templateCreateRequest);

    VariantResponse getVariant(String templateUId);
}
