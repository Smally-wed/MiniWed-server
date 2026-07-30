package smally.server.domain.image.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SectionValueImageScannerTest {

    private final SectionValueImageScanner scanner = new SectionValueImageScanner();

    @Test
    void 중첩된_맵과_리스트에서_이미지_키를_모은다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of("photo", "invitations/7/cover.jpg"),
                "gallery-1", Map.of("photos", List.of(
                        "invitations/7/a.jpg", "invitations/7/b.jpg")));

        assertThat(scanner.collectKeys(sectionValues))
                .containsExactlyInAnyOrder(
                        "invitations/7/cover.jpg", "invitations/7/a.jpg", "invitations/7/b.jpg");
    }

    @Test
    void 프리픽스가_다른_문자열과_숫자는_무시한다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of(
                        "groomName", "철수",
                        "guests", 120,
                        "thumbnail", "templates/thumbnails/x.jpg"));

        assertThat(scanner.collectKeys(sectionValues)).isEmpty();
    }

    @Test
    void 같은_키가_여러_번_나와도_한_번만_센다() {
        Map<String, Object> sectionValues = Map.of(
                "gallery-1", Map.of("photos", List.of("invitations/7/a.jpg")),
                "gallery-2", Map.of("photos", List.of("invitations/7/a.jpg")));

        assertThat(scanner.collectKeys(sectionValues)).containsExactly("invitations/7/a.jpg");
    }

    @Test
    void 치환은_원본을_바꾸지_않고_사본을_돌려준다() {
        Map<String, Object> sectionValues = Map.of(
                "cover", Map.of("photo", "invitations/7/cover.jpg"));

        Map<String, Object> replaced = scanner.replaceKeys(sectionValues, key -> "https://cdn/" + key);

        assertThat(nested(replaced)).isEqualTo("https://cdn/invitations/7/cover.jpg");
        assertThat(nested(sectionValues)).isEqualTo("invitations/7/cover.jpg");
    }

    @Test
    void 치환은_리스트_안의_키도_바꾼다() {
        Map<String, Object> sectionValues = Map.of(
                "gallery-1", Map.of("photos", List.of("invitations/7/a.jpg", "설명 텍스트")));

        Map<String, Object> replaced = scanner.replaceKeys(sectionValues, key -> "URL:" + key);

        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) replaced.get("gallery-1");
        // List<?>로 캐스트하면 AssertJ containsExactly가 와일드카드 캡처와 충돌해 컴파일이 안 되므로 List<Object>로 캐스트한다.
        @SuppressWarnings("unchecked")
        List<Object> photos = (List<Object>) section.get("photos");
        assertThat(photos)
                .containsExactly("URL:invitations/7/a.jpg", "설명 텍스트");
    }

    @Test
    void null을_넣어도_터지지_않는다() {
        assertThat(scanner.collectKeys(null)).isEmpty();
        assertThat(scanner.replaceKeys(null, key -> key)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> sectionValues) {
        return ((Map<String, Object>) sectionValues.get("cover")).get("photo");
    }
}
