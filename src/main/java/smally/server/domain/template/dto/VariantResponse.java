package smally.server.domain.template.dto;

import smally.server.domain.template.entity.Template;

import java.util.Map;

public record VariantResponse(
        Map<String, Object> variant
) {
    public static VariantResponse from(Template template){
        return new VariantResponse(template.getVariants());
    }
}
