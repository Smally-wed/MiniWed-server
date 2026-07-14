package smally.server.domain.template.dto;

import smally.server.domain.template.entity.Template;

public record TemplateResponse(
        String templateUid
) {
    public static TemplateResponse from(Template template){
        return new TemplateResponse(
                template.getTemplateUid().toString()
        );
    }

}
