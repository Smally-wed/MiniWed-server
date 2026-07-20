package smally.server.domain.template.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import smally.server.core.dto.ApiResponse;
import smally.server.domain.template.dto.TemplateCreateRequest;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.TemplateService;

@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping(value = "/v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @RequestPart("thumbnail") MultipartFile thumbnail,
            @RequestPart("request") @Valid TemplateCreateRequest templateCreateRequest
    ) {
        return ResponseEntity.ok(
                ApiResponse.of(
                        HttpStatus.CREATED,
                        templateService.createTemplate(templateCreateRequest, thumbnail)
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
