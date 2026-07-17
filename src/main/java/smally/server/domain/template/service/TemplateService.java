package smally.server.domain.template.service;

import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;

public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest request);

    TemplateResponse getTemplate(String templateUId);
}
