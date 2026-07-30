package smally.server.domain.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record InvitationCreateRequest(
        @NotBlank(message = "템플릿 식별자는 필수입니다.")
        String templateUid,
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions
) {
}
