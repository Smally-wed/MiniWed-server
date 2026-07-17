package smally.server.domain.template.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    private final static String TEMPLATE_CACHE_KEY = "TPL:";
    private final static Duration TEMPLATE_CACHE_TTL = Duration.ofDays(30);

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ComponentService componentService;
    private final OptionDefinitionService optionDefinitionService;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public TemplateResponse createTemplate(TemplateCreateRequest request) {
        categoryService.validateExists(request.category());
        validateRecipe(request.sections());
        validateTheme(request.theme());

        Template template = request.toTemplate();
        templateRepository.save(template);

        TemplateResponse response = TemplateResponse.from(template);
        if (response.templateUid() != null) {
            redisCacheService.setCacheData(
                    TEMPLATE_CACHE_KEY + response.templateUid(), response, TEMPLATE_CACHE_TTL);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(String templateUId) {
        String templateKey = TEMPLATE_CACHE_KEY + templateUId;
        TemplateResponse cached = redisCacheService.getCacheData(templateKey, TemplateResponse.class);

        if (cached != null) {
            return cached;
        }
        log.warn("[Cache-miss] template detail cache miss key : {}", templateUId);
        TemplateResponse response = TemplateResponse.from(getTemplateEntity(templateUId));
        redisCacheService.setCacheData(templateKey, response, TEMPLATE_CACHE_TTL);

        return response;
    }

    private void validateRecipe(List<Map<String, Object>> sections) {
        if (sections == null || sections.isEmpty()) {
            throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
        }

        for (Map<String, Object> section : sections) {
            Object rawUid = section.get("componentUId");
            if (!(rawUid instanceof String componentUid) || componentUid.isBlank()) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_RECIPE);
            }
            componentService.getComponent(componentUid);
        }
    }

    private void validateTheme(Map<String, Object> theme) {
        if (theme == null) {
            return;
        }

        theme.forEach((key, value) -> {
            OptionDefinition definition = optionDefinitionService.getOptionDefinition(key);
            List<Object> allowed = definition.getAllowedValues();
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(value)) {
                throw new TemplateException(ErrorCode.INVALID_TEMPLATE_THEME);
            }
        });
    }

    private Template getTemplateEntity(String templateUId) {
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
