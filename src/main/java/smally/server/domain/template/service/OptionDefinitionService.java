package smally.server.domain.template.service;

import java.util.List;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.entity.OptionDefinition;

public interface OptionDefinitionService {
    void createOptionDefinition(OptionDefinitionCreateRequest request);
    OptionDefinition getOptionDefinition(String key);
    List<OptionDefinition> getAllOptionDefinitions();
}
