package smally.server.domain.component.service;

import java.util.Map;

public interface InternalComponentService {
    void validateComponentJsontData(String TypeName, Map<String, Object> data,Map<String, Object> optionData);
}
