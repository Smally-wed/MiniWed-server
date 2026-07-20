package smally.server.domain.template.service;

import org.springframework.web.multipart.MultipartFile;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;

public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest request, MultipartFile thumbnail);

    TemplateResponse getTemplate(String templateUId);
}
