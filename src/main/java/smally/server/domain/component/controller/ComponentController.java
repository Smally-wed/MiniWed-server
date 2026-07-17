package smally.server.domain.component.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.component.dto.ComponentCreateRequest;
import smally.server.domain.component.dto.ComponentResponse;
import smally.server.domain.component.service.ComponentService;

@RestController
@RequestMapping("/api/component")
@RequiredArgsConstructor
public class ComponentController {

    private final ComponentService componentService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createComponent(
            @RequestBody @Valid ComponentCreateRequest request
    ) {
        componentService.createComponent(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<ComponentResponse>>> getAllComponents() {
        List<ComponentResponse> body = componentService.getAllComponents();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{componentUid}")
    public ResponseEntity<ApiResponse<ComponentResponse>> getComponent(
            @PathVariable String componentUid
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(HttpStatus.OK, componentService.getComponent(componentUid))
        );
    }
}
