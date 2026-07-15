package smally.server.domain.template.dto;

import smally.server.domain.template.entity.Template;

import java.util.Map;

public record VariantResponse(
        Map<String, Object> variant,
        Map<String, Object> optionsSchema
) {
    public static VariantResponse from(Template template){
        return new VariantResponse(template.getVariants(), template.getOptionsSchema());
    }
}
