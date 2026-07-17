package smally.server.domain.component.service;

import java.util.List;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.entity.Component;

public interface ComponentService {
    void createComponent(ComponentCreateRequest request);
    ComponentResponse getComponent(String componentUid);
    List<ComponentResponse> getAllComponents();
}
