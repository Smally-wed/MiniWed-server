package smally.server.domain.invitation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.PublicInvitationResponse;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.TemplateService;

@Service
@RequiredArgsConstructor
public class PublicInvitationServiceImpl implements PublicInvitationService {

    private static final String SECTION_ID = "sectionId";
    private static final String OPTIONS = "options";

    private final InvitationRepository invitationRepository;
    private final TemplateService templateService;
    private final InvitationImageService invitationImageService;

    @Override
    @Transactional(readOnly = true)
    @ExecutionTimeLog("청첩장 공개 조회")
    public PublicInvitationResponse getBySlug(String slug) {
        Invitation invitation = invitationRepository
                .findBySlugAndStatus(slug, InvitationStatus.PUBLISHED)
                .orElseThrow(() -> new InvitationException(ErrorCode.INVITATION_NOT_FOUND));

        TemplateResponse template = templateService.getTemplate(
                invitation.getTemplate().getTemplateUid().toString());

        return new PublicInvitationResponse(
                invitation.getInvitationUid().toString(),
                mergeSelectedOptions(template.sections(), invitation.getSelectedOptions()),
                template.theme(),
                invitationImageService.withPresignedUrls(invitation.getSectionValues()),
                invitation.getPublishedAt());
    }

    /** 템플릿 고정옵션 위에 사용자가 고른 값을 덮은 사본을 만든다. 캐시된 템플릿 DTO는 건드리지 않는다. */
    private List<Map<String, Object>> mergeSelectedOptions(List<Map<String, Object>> sections,
                                                           Map<String, Object> selectedOptions) {
        return sections.stream().map(section -> {
            Map<String, Object> copy = new LinkedHashMap<>(section);
            Map<String, Object> options = new LinkedHashMap<>(asMap(section.get(OPTIONS)));
            options.putAll(asMap(selected(selectedOptions, section.get(SECTION_ID))));
            copy.put(OPTIONS, options);
            return copy;
        }).toList();
    }

    private Object selected(Map<String, Object> selectedOptions, Object sectionId) {
        return selectedOptions == null ? null : selectedOptions.get(String.valueOf(sectionId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
