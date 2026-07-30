package smally.server.domain.invitation.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.aop.ExecutionTimeLog;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.core.exception.exceptions.UserException;
import smally.server.domain.image.service.InvitationImageService;
import smally.server.domain.invitation.dto.InvitationCreateRequest;
import smally.server.domain.invitation.dto.InvitationResponse;
import smally.server.domain.invitation.dto.InvitationSummaryResponse;
import smally.server.domain.invitation.dto.InvitationUpdateRequest;
import smally.server.domain.invitation.entity.Invitation;
import smally.server.domain.invitation.enums.InvitationStatus;
import smally.server.domain.invitation.repository.InvitationRepository;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.entity.Template;
import smally.server.domain.template.service.TemplateService;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.repository.UserRepository;
import smally.server.domain.user.service.InternalUserService;

@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final InternalUserService userService;
    private final TemplateService templateService;
    private final InvitationValidator validator;
    private final InvitationImageService invitationImageService;
    private final SlugGenerator slugGenerator;

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 생성")
    public InvitationResponse create(Long userId, InvitationCreateRequest request) {
        User user = userService.getUserIfExist(userId);
        TemplateResponse template = templateService.getTemplate(request.templateUid());

        validator.validateForDraft(template, request.sectionValues(), request.selectedOptions());

        Invitation invitation = Invitation.builder()
                .user(user)
                .template(templateService.getTemplateEntity(request.templateUid()))
                .sectionValues(request.sectionValues())
                .selectedOptions(request.selectedOptions())
                .build();
        invitationRepository.save(invitation);

        invitationImageService.link(invitation, request.sectionValues());

        return toResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public InvitationResponse get(Long userId, String invitationUid) {
        return toResponse(getOwned(userId, invitationUid));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationSummaryResponse> getMine(Long userId) {
        return invitationRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(InvitationSummaryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 저장")
    public InvitationResponse update(Long userId, String invitationUid,
                                     InvitationUpdateRequest request) {
        Invitation invitation = getOwned(userId, invitationUid);
        TemplateResponse template = currentTemplate(invitation);

        // 발행된 청첩장은 하객에게 항상 완결된 상태로 보여야 한다(ADR-009 결정 2).
        // 임시저장 검증만 거치면 컴포넌트 스키마가 비어도 통과해 하객에게 깨진 화면이 노출된다.
        if (invitation.getStatus() == InvitationStatus.PUBLISHED) {
            validator.validateForPublish(template, request.sectionValues(), request.selectedOptions());
        } else {
            validator.validateForDraft(template, request.sectionValues(), request.selectedOptions());
        }
        invitationImageService.link(invitation, request.sectionValues());
        invitation.updateContent(request.sectionValues(), request.selectedOptions());

        return toResponse(invitation);
    }

    @Override
    @Transactional
    @ExecutionTimeLog("청첩장 발행")
    public InvitationResponse publish(Long userId, String invitationUid) {
        Invitation invitation = getOwned(userId, invitationUid);
        if (invitation.getStatus() == InvitationStatus.PUBLISHED) {
            throw new InvitationException(ErrorCode.INVITATION_ALREADY_PUBLISHED);
        }

        validator.validateForPublish(
                currentTemplate(invitation),
                invitation.getSectionValues(),
                invitation.getSelectedOptions());

        // 재발행이면 기존 slug를 그대로 쓴다 — 공유된 링크가 살아 있어야 한다.
        String slug = invitation.getSlug() != null ? invitation.getSlug() : slugGenerator.generate();
        invitation.publish(slug);

        return toResponse(invitation);
    }

    @Override
    @Transactional
    public InvitationResponse unpublish(Long userId, String invitationUid) {
        Invitation invitation = getOwned(userId, invitationUid);
        // publish()가 INVITATION_ALREADY_PUBLISHED로 막는 것과 대칭 — 발행 안 된 청첩장은 취소할 게 없다.
        if (invitation.getStatus() != InvitationStatus.PUBLISHED) {
            throw new InvitationException(ErrorCode.INVITATION_NOT_PUBLISHED);
        }
        invitation.unpublish();
        return toResponse(invitation);
    }

    @Override
    @Transactional
    public void delete(Long userId, String invitationUid) {
        Invitation invitation = getOwned(userId, invitationUid);
        // 삭제 전 이미지 연결을 끊는다 — 안 그러면 image_uploads.invitation_id FK 위반으로 500이 난다.
        invitationImageService.unlinkAll(invitation);
        invitationRepository.delete(invitation);
    }

    private Invitation getOwned(Long userId, String invitationUid) {
        Invitation invitation = invitationRepository.findByInvitationUid(parseUid(invitationUid))
                .orElseThrow(() -> new InvitationException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.isOwnedBy(userId)) {
            throw new InvitationException(ErrorCode.INVITATION_ACCESS_DENIED);
        }
        return invitation;
    }

    private UUID parseUid(String invitationUid) {
        try {
            return UUID.fromString(invitationUid);
        } catch (IllegalArgumentException e) {
            throw new InvitationException(ErrorCode.INVITATION_NOT_FOUND);
        }
    }

    private TemplateResponse currentTemplate(Invitation invitation) {
        return templateService.getTemplate(invitation.getTemplate().getTemplateUid().toString());
    }

    private InvitationResponse toResponse(Invitation invitation) {
        Map<String, Object> presigned =
                invitationImageService.withPresignedUrls(invitation.getSectionValues());
        return InvitationResponse.of(invitation, presigned);
    }
}
