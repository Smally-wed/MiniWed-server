package smally.server.domain.template.controller;

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
import smally.server.domain.template.dto.OptionDefinitionCreateRequest;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.service.OptionDefinitionService;

@RestController
@RequestMapping("/api/option-definition")
@RequiredArgsConstructor
public class OptionDefinitionController {

    private final OptionDefinitionService optionDefinitionService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<Void>> createOptionDefinition(
            @RequestBody @Valid OptionDefinitionCreateRequest request
    ) {
        optionDefinitionService.createOptionDefinition(request);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.CREATED, null));
    }

    @GetMapping("/v1")
    public ResponseEntity<ApiResponse<List<OptionDefinitionResponse>>> getAllOptionDefinitions() {
        List<OptionDefinitionResponse> body = optionDefinitionService.getAllOptionDefinitions().stream()
                .map(OptionDefinitionResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK, body));
    }

    @GetMapping("/v1/{key}")
    public ResponseEntity<ApiResponse<OptionDefinitionResponse>> getOptionDefinition(
            @PathVariable String key
    ) {
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK,
                OptionDefinitionResponse.from(optionDefinitionService.getOptionDefinition(key))));
    }
}
