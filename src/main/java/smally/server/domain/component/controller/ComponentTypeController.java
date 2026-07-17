package smally.server.domain.component.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.component.dto.ComponentTypeCreateRequest;
import smally.server.domain.component.service.ComponentTypeService;

@RestController
@RequestMapping("/api/component-type")
@RequiredArgsConstructor
public class ComponentTypeController {

    private final ComponentTypeService componentTypeService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createComponentType(
            @RequestBody @Valid ComponentTypeCreateRequest request
    ) {
        componentTypeService.createComponentType(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<String>>> getAllComponentTypes() {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, componentTypeService.getAllComponentTypes()));
    }
}
