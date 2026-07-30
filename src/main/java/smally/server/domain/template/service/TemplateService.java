package smally.server.domain.template.service;

import org.springframework.web.multipart.MultipartFile;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;

public interface TemplateService {
    TemplateResponse createTemplate(TemplateCreateRequest request, MultipartFile thumbnail);

    TemplateResponse getTemplate(String templateUId);

    /** 청첩장이 @ManyToOne 연관을 맺으려면 캐시된 DTO가 아닌 영속 엔티티가 필요하다. */
    Template getTemplateEntity(String templateUid);
}
