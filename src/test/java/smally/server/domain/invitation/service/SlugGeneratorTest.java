package smally.server.domain.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.invitation.repository.InvitationRepository;

class SlugGeneratorTest {

    private final InvitationRepository repository = mock(InvitationRepository.class);
    private final SlugGenerator generator = new SlugGenerator(repository);

    @Test
    void slug는_22자_URL_안전_문자열이다() {
        when(repository.existsBySlug(anyString())).thenReturn(false);

        String slug = generator.generate();

        assertThat(slug).hasSize(22).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void 매번_다른_값을_만든다() {
        when(repository.existsBySlug(anyString())).thenReturn(false);

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void 이미_쓰이는_slug면_다시_만든다() {
        when(repository.existsBySlug(anyString()))
                .thenReturn(true)
                .thenReturn(false);

        assertThat(generator.generate()).isNotNull();
    }

    @Test
    void 재시도_한도를_넘으면_예외를_던진다() {
        when(repository.existsBySlug(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate)
                .isInstanceOf(InvitationException.class);
    }
}
