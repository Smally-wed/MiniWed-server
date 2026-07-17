package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.TemplateService;

@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping("/v1")
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @RequestBody @Valid TemplateCreateRequest templateCreateRequest
            ){

        return ResponseEntity.ok(
                ApiResponse.of(
                        HttpStatus.CREATED, templateService.createTemplate(templateCreateRequest)
                ));
    }

    @GetMapping("/v1/{templateUID}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(
            @PathVariable String templateUID
    ){
        return ResponseEntity.ok(
                ApiResponse.of(
                        HttpStatus.OK, templateService.getTemplate(templateUID)
                ));
    }

}
