package smally.server.domain.image.util;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * 청첩장 sectionValues(jsonb)를 재귀 순회한다.
 *
 * <p>이미지 필드의 이름·개수·중첩 구조는 컴포넌트마다 다르고 서버는 그것을 모른다(ADR-009).
 * 그래서 구조 대신 객체 키 프리픽스로 이미지를 알아본다.
 */
@Component
public class SectionValueImageScanner {

    public static final String IMAGE_KEY_PREFIX = "invitations/";

    /** 값 전체에서 이미지 객체 키를 모은다. 중복은 제거된다. */
    public Set<String> collectKeys(Object node) {
        Set<String> keys = new LinkedHashSet<>();
        collect(node, keys);
        return keys;
    }

    /** 이미지 키를 resolver가 준 값으로 바꾼 사본을 만든다. 원본은 건드리지 않는다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> replaceKeys(Map<String, Object> sectionValues,
                                           UnaryOperator<String> resolver) {
        if (sectionValues == null) {
            return Map.of();
        }
        return (Map<String, Object>) replace(sectionValues, resolver);
    }

    private void collect(Object node, Set<String> keys) {
        switch (node) {
            case String value when isImageKey(value) -> keys.add(value);
            case Map<?, ?> map -> map.values().forEach(child -> collect(child, keys));
            case List<?> list -> list.forEach(child -> collect(child, keys));
            case null, default -> { }
        }
    }

    private Object replace(Object node, UnaryOperator<String> resolver) {
        return switch (node) {
            case String value when isImageKey(value) -> resolver.apply(value);
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), replace(value, resolver)));
                yield copy;
            }
            case List<?> list -> list.stream().map(child -> replace(child, resolver)).toList();
            case null, default -> node;
        };
    }

    private boolean isImageKey(String value) {
        return value.startsWith(IMAGE_KEY_PREFIX);
    }
}
