package smally.server.domain.template.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.TemplateException;
import smally.server.domain.component.service.ComponentService;
import smally.server.domain.image.service.StorageService;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.repository.TemplateRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    private final static String TEMPLATE_CACHE_KEY = "TPL:";
    private final static Duration TEMPLATE_CACHE_TTL = Duration.ofDays(30);
    private final static String THUMBNAIL_KEY_PREFIX = "templates/thumbnails/";

    private final TemplateRepository templateRepository;
    private final CategoryService categoryService;
    private final ComponentService componentService;
    private final OptionDefinitionService optionDefinitionService;
    private final RedisCacheService redisCacheService;
    private final StorageService storageService;

    @Override
    public TemplateResponse createTemplate(TemplateCreateRequest request, MultipartFile thumbnail) {
        //1. 카테고리 있는지 검증.
        categoryService.validateExists(request.category());
        //2. 컴포넌트가 있는지 검증
        validateRecipe(request.sections());
        //3. 설정 가능한 옵션 리스트가 허용된 값인지 검증
        validateTheme(request.theme());

        // S3 업로드(네트워크)는 트랜잭션 밖에서 수행한다. 아래 save()가 자체 트랜잭션으로 저장.
        String objectKey = storageService.upload(thumbnail, THUMBNAIL_KEY_PREFIX);

        Template template = request.toTemplate(objectKey);
        templateRepository.save(template);

        TemplateResponse response = TemplateResponse.from(template);
        if (response.templateUid() != null) {
            redisCacheService.setCacheData(
                    TEMPLATE_CACHE_KEY + response.templateUid(), response, TEMPLATE_CACHE_TTL);
        }

        return withPresignedThumbnail(response);
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(String templateUId) {
        String templateKey = TEMPLATE_CACHE_KEY + templateUId;
        TemplateResponse cached = redisCacheService.getCacheData(templateKey, TemplateResponse.class);

        if (cached != null) {
            return withPresignedThumbnail(cached);
        }
        log.warn("[Cache-miss] template detail cache miss key : {}", templateUId);
        TemplateResponse response = TemplateResponse.from(getTemplateEntity(templateUId));
        redisCacheService.setCacheData(templateKey, response, TEMPLATE_CACHE_TTL);

        return withPresignedThumbnail(response);
    }

    private TemplateResponse withPresignedThumbnail(TemplateResponse response) {
        String objectKey = response.thumbnail();
        if (objectKey == null || objectKey.isBlank()) {
            return response;
        }
        return response.withThumbnail(storageService.presignedGetUrl(objectKey));
    }

    /* List<Map> 을 받아서 section에 담겨있는 componentUid로 컴포넌트가 있는지 확인. 하나라도 없으면 error*/
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

    /* Map key겂으로 옵션 마스터를 조회 허용한 옵션들이 실제로 사용 가능한 옵션인지 확인.(해당 시점에)*/
    private void validateTheme(Map<String, Object> theme) {
        if (theme == null) {
            return;
        }

        theme.forEach((key, value) -> {
            OptionDefinitionResponse definition = optionDefinitionService.getOptionDefinition(key);
            List<Object> allowed = definition.allowedValues();
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
