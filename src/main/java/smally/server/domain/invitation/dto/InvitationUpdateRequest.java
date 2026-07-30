package smally.server.domain.invitation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** 부분 병합이 아닌 전체 교체다 — "지운 것"과 "안 보낸 것"을 구분할 수 없기 때문(스펙 §4.2). */
public record InvitationUpdateRequest(
        @NotNull(message = "섹션 값은 필수입니다.")
        Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions
) {
}
