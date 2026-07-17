package smally.server.domain.component.service;

import java.util.List;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.entity.ComponentType;

public interface ComponentTypeService {
    void createComponentType(ComponentTypeCreateRequest request);
    List<String> getAllComponentTypes();
    ComponentType getComponentByName(String name);
}
