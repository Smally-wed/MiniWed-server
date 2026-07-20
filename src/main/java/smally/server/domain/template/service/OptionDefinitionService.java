package smally.server.domain.template.service;

import java.util.List;
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.dto.OptionDefinitionResponse;

public interface OptionDefinitionService {
    void createOptionDefinition(OptionDefinitionCreateRequest request);
    OptionDefinitionResponse getOptionDefinition(String key);
    List<OptionDefinitionResponse> getAllOptionDefinitions();
}
