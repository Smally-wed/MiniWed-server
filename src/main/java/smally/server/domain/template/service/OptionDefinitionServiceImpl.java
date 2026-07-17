package smally.server.domain.template.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.OptionDefinitionException;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;
import smally.server.domain.template.repository.OptionDefinitionRepository;

@Service
@RequiredArgsConstructor
public class OptionDefinitionServiceImpl implements OptionDefinitionService {

    private final OptionDefinitionRepository optionDefinitionRepository;

    @Override
    @Transactional
    public void createOptionDefinition(OptionDefinitionCreateRequest request) {
        if (optionDefinitionRepository.existsByKey(request.key())) {
            throw new OptionDefinitionException(ErrorCode.DUPLICATE_OPTION_DEFINITION);
        }
        if (request.defaultValue() != null
                && request.allowedValues() != null && !request.allowedValues().isEmpty()
                && !request.allowedValues().contains(request.defaultValue())) {
            throw new OptionDefinitionException(ErrorCode.INVALID_OPTION_DEFAULT);
        }
        optionDefinitionRepository.save(request.toEntity());
    }

    @Override
    @Transactional(readOnly = true)
    public OptionDefinition getOptionDefinition(String key) {
        return optionDefinitionRepository.findByKey(key)
                .orElseThrow(() -> new OptionDefinitionException(ErrorCode.OPTION_DEFINITION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OptionDefinition> getAllOptionDefinitions() {
        return optionDefinitionRepository.findAll();
    }
}
