package smally.server.domain.invitation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 하객 렌더러가 API 한 번으로 받는 페이로드.
 * sections는 템플릿 레시피에 사용자가 고른 옵션을 덮어쓴 결과이고,
 * sectionValues의 이미지 키는 presigned URL로 치환되어 있다.
 */
public record PublicInvitationResponse(
        String invitationUid,
        List<Map<String, Object>> sections,
        Map<String, Object> theme,
        Map<String, Object> sectionValues,
        Instant publishedAt
) {
}
