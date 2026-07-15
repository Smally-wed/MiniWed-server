package smally.server.domain.template.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.dto.VariantResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService, InternalTemplateService {

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final SchemaValidator schemaValidator;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest templateCreateRequest) {
        categoryService.validateExists(templateCreateRequest.category());

        if (templateCreateRequest.optionsSchema() != null) {
            List<String> errors = schemaValidator.validateSchema(templateCreateRequest.optionsSchema());
            if (!errors.isEmpty()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_OPTIONS_SCHEMA);
            }
        }

        Template template = templateCreateRequest.toTemplate();

        templateRepository.save(template);
        return TemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantResponse getVariant(String templateUId) {
        Template template = getTemplate(templateUId);

        return VariantResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public void isValidTemplate(String templateUId, Map<String, Object> jsonData) {
        Template template = getTemplate(templateUId);

        List<String> errors = schemaValidator.validateData(template.getSectionSchema(), jsonData);

        if (!errors.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_SECTION_VALUES);
        }
    }

    Template getTemplate(String templateUId) {
        UUID uid;
        try {
            uid = UUID.fromString(templateUId);
        } catch (IllegalArgumentException e) {
            throw new TemplateException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        return templateRepository.findTemplateByTemplateUid(uid)
                .orElseThrow(() -> new TemplateException(ErrorCode.TEMPLATE_NOT_FOUND));
    }
}
