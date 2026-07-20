package smally.server.domain.template.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.cache.RedisCacheService;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class OptionDefinitionServiceImpl implements OptionDefinitionService {

    private final static String OPTION_CACHE_KEY = "OPT:";
    private final static String OPTION_LIST_CACHE_KEY = "OPT:ALL";
    private final static Duration OPTION_CACHE_TTL = Duration.ofDays(30);

    private final OptionDefinitionRepository optionDefinitionRepository;
    private final RedisCacheService redisCacheService;

    @Override
    @Transactional
    @ExecutionTimeLog("옵션 정의 생성")
    public void createOptionDefinition(OptionDefinitionCreateRequest request) {
        if (optionDefinitionRepository.existsByKey(request.key())) {
            throw new OptionDefinitionException(ErrorCode.DUPLICATE_OPTION_DEFINITION);
        }
        if (request.defaultValue() != null
                && request.allowedValues() != null && !request.allowedValues().isEmpty()
                && !request.allowedValues().contains(request.defaultValue())) {
            throw new OptionDefinitionException(ErrorCode.INVALID_OPTION_DEFAULT);
        }
        OptionDefinition definition = request.toEntity();
        optionDefinitionRepository.save(definition);

        redisCacheService.setCacheData(
                OPTION_CACHE_KEY + definition.getKey(),
                OptionDefinitionResponse.from(definition),
                OPTION_CACHE_TTL);
        redisCacheService.deleteCacheData(OPTION_LIST_CACHE_KEY);
    }

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("옵션 정의 단일 조회")
    public OptionDefinitionResponse getOptionDefinition(String key) {
        String cacheKey = OPTION_CACHE_KEY + key;
        OptionDefinitionResponse cached =
                redisCacheService.getCacheData(cacheKey, OptionDefinitionResponse.class);

        if (cached != null) {
            return cached;
        }

        log.warn("[Cache-miss] option definition cache miss key : {}", cacheKey);

        OptionDefinitionResponse response = OptionDefinitionResponse.from(
                optionDefinitionRepository.findByKey(key)
                        .orElseThrow(() -> new OptionDefinitionException(
                                ErrorCode.OPTION_DEFINITION_NOT_FOUND)));

        redisCacheService.setCacheData(cacheKey, response, OPTION_CACHE_TTL);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("옵션 정의 전체 조회")
    public List<OptionDefinitionResponse> getAllOptionDefinitions() {
        @SuppressWarnings("unchecked")
        List<OptionDefinitionResponse> cached =
                (List<OptionDefinitionResponse>) redisCacheService.getCacheData(
                        OPTION_LIST_CACHE_KEY, List.class);

        if (cached != null) {
            return cached;
        }

        log.warn("[Cache-miss] option definition list cache miss key : {}", OPTION_LIST_CACHE_KEY);

        List<OptionDefinitionResponse> responses = optionDefinitionRepository.findAll().stream()
                .map(OptionDefinitionResponse::from)
                .toList();

        redisCacheService.setCacheData(
                OPTION_LIST_CACHE_KEY, new ArrayList<>(responses), OPTION_CACHE_TTL);

        return responses;
    }
}
