package smally.server.domain.invitation.service;

import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.invitation.repository.InvitationRepository;

/**
 * 하객 공개 URL에 쓰는 추측 불가 slug를 만든다.
 * 128비트 난수를 패딩 없는 URL-safe Base64로 인코딩해 22자가 된다.
 */
@Component
@RequiredArgsConstructor
public class SlugGenerator {

    private static final int RANDOM_BYTES = 16;
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final InvitationRepository invitationRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String slug = randomSlug();
            if (!invitationRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new InvitationException(ErrorCode.SLUG_GENERATION_FAILED);
    }

    private String randomSlug() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
