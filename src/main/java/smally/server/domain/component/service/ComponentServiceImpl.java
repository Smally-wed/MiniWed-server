package smally.server.domain.component.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentException;
import smally.server.core.validation.SchemaValidator;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.entity.Component;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService, InternalComponentService {

    private final static String COMPONENT_CACHE_kEY= "COM:";
    private final static String COMPONENT_LIST_CACHE_kEY= "COM:LIST";
    private final static Duration COMPONENT_CACHE_TTL = Duration.ofDays(30);

    private final ComponentRepository componentRepository;
    private final ComponentTypeService componentTypeService;
    private final SchemaValidator schemaValidator;

    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    @ExecutionTimeLog("컴포넌트 생성")
    public void createComponent(ComponentCreateRequest request) {
        ComponentType componentType = componentTypeService.getComponentByName(request.componentTypeName());

        validateSchemaOrThrow(request.dataSchema());
        if (request.optionSchema() != null) {
            validateSchemaOrThrow(request.optionSchema());
        }

        Component component = request.toComponent();
        component.setComponentType(componentType);

        componentRepository.save(component);

        ComponentResponse cacheData = ComponentResponse.from(component);
        redisCacheService.setCacheData(COMPONENT_CACHE_kEY + component.getComponentUId(), cacheData, COMPONENT_CACHE_TTL);
        redisCacheService.deleteCacheData(COMPONENT_LIST_CACHE_kEY);
    }

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("컴포넌트 단일 조회")
    public ComponentResponse getComponent(String componentUid) {
        String componentKey = COMPONENT_CACHE_kEY + componentUid;
        ComponentResponse cached = redisCacheService.getCacheData(componentKey, ComponentResponse.class);

        if (cached != null) {
            return cached;
        }
        log.warn("[Cache-miss] component detail cache miss key : {}", componentUid);
        Component component = componentRepository.findByComponentUId(componentUid)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));

        ComponentResponse response = ComponentResponse.from(component);
        redisCacheService.setCacheData(componentKey, response, COMPONENT_CACHE_TTL);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("컴포넌트 전체 조회")
    public List<ComponentResponse> getAllComponents() {
        @SuppressWarnings("unchecked")
        List<ComponentResponse> cached =
                (List<ComponentResponse>) redisCacheService.getCacheData(COMPONENT_LIST_CACHE_kEY, List.class);

        if (cached != null) {
            return cached;
        }

        log.warn("[Cache-miss] component List cache miss key : {}", COMPONENT_LIST_CACHE_kEY);
        List<ComponentResponse> responses = componentRepository.findAll().stream()
                .map(ComponentResponse::from)
                .toList();

        redisCacheService.setCacheData(
                COMPONENT_LIST_CACHE_kEY, new ArrayList<>(responses), COMPONENT_CACHE_TTL);

        return responses;
    }

    private void validateSchemaOrThrow(Map<String, Object> schema) {
        if (!schemaValidator.validateSchema(schema).isEmpty()) {
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_SCHEMA);
        }
    }

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("컴포넌트 Json Data 검증")
    public void validateComponentJsontData(String componentUid, Map<String, Object> data,Map<String, Object> optionData ) {
        Component component = componentRepository.findByComponentUId(componentUid)
                .orElseThrow(() -> new ComponentException(ErrorCode.COMPONENT_NOT_FOUND));

        if(!schemaValidator.validateData(component.getDataSchema(),data).isEmpty()){
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_DATA);
        }

        if(!schemaValidator.validateData(component.getOptionSchema(),optionData).isEmpty()){
            throw new ComponentException(ErrorCode.INVALID_COMPONENT_OPTION_DATA);
        }
    }
}
