package smally.server.domain.template.service;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.dto.VariantResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService, InternalTemplateService{

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest templateCreateRequest) {
        categoryService.validateExists(templateCreateRequest.category());

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

        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

        Schema schema = schemaRegistry.getSchema(
                objectMapper.writeValueAsString(template.getSectionSchema()), InputFormat.JSON);

        List<Error> errors = schema.validate(objectMapper.writeValueAsString(jsonData), InputFormat.JSON);

        if (!errors.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_SECTION_VALUES);
        }
    }


    Template getTemplate(String templateUId){
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
