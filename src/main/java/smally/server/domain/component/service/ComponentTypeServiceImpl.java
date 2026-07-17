package smally.server.domain.component.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.ComponentTypeException;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;
import smally.server.domain.component.repository.ComponentTypeRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentTypeServiceImpl implements ComponentTypeService {

    private final static String COMPONENT_TYPE_LIST_KEY = "COM_TYPE:";
    private final static Duration COMPONENT_TYPE_CACHE_TTL = Duration.ofDays(30);

    private final ComponentTypeRepository componentTypeRepository;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    public void createComponentType(ComponentTypeCreateRequest request) {
        if (componentTypeRepository.existsByName(request.name())) {
            throw new ComponentTypeException(ErrorCode.DUPLICATE_COMPONENT_TYPE);
        }
        componentTypeRepository.save(request.to());
        redisCacheService.deleteCacheData(COMPONENT_TYPE_LIST_KEY);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllComponentTypes() {
        @SuppressWarnings("unchecked")
        List<String> cached = (List<String>) redisCacheService.getCacheData(COMPONENT_TYPE_LIST_KEY, List.class);

        if(cached != null){
            return cached;
        }

        log.warn("[Cache-miss] component type List cache miss key : {}", COMPONENT_TYPE_LIST_KEY);

        List<String> componentList = componentTypeRepository.findAll().stream()
                .map(ComponentType::getName)
                .toList();

        redisCacheService.setCacheData(COMPONENT_TYPE_LIST_KEY, new ArrayList<>(componentList),COMPONENT_TYPE_CACHE_TTL);

        return componentList;
    }

    @Override
    @Transactional(readOnly = true)
    public ComponentType getComponentByName(String name) {
        return componentTypeRepository.findByName(name).orElseThrow(
                () -> new ComponentTypeException(ErrorCode.COMPONENT_TYPE_NOT_FOUND)
        );
    }
}
