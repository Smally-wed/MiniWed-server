package smally.server.domain.template.service;

import java.util.Map;

public interface InternalTemplateService {

    void isValidTemplate(String templateUId, Map<String, Object> jsonData);

}
