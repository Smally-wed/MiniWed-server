package smally.server.domain.invitation.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.InvitationException;
import smally.server.domain.component.service.InternalComponentService;
import smally.server.domain.template.dto.OptionDefinitionResponse;
import smally.server.domain.template.dto.TemplateResponse;
import smally.server.domain.template.service.OptionDefinitionService;

/**
 * 청첩장 값 검증. 저장 단계에 따라 강도가 다르다.
 * - 임시저장(DRAFT): 구조·권한만. 미완성 상태를 허용해야 한다.
 * - 발행(PUBLISHED): 여기에 컴포넌트 dataSchema 완결성 검증을 더한다.
 */
@Component
@RequiredArgsConstructor
public class InvitationValidator {

    private static final String SECTION_ID = "sectionId";
    private static final String COMPONENT_UID = "componentUId";
    private static final String OPTIONS = "options";
    private static final String EDITABLE = "editable";

    private final InternalComponentService internalComponentService;
    private final OptionDefinitionService optionDefinitionService;

    public void validateForDraft(TemplateResponse template,
                                 Map<String, Object> sectionValues,
                                 Map<String, Object> selectedOptions) {
        Map<String, Map<String, Object>> sections = sectionsById(template);

        validateKnownSections(sections.keySet(), sectionValues);
        validateKnownSections(sections.keySet(), selectedOptions);
        validateOptions(sections, selectedOptions);
    }

    public void validateForPublish(TemplateResponse template,
                                   Map<String, Object> sectionValues,
                                   Map<String, Object> selectedOptions) {
        validateForDraft(template, sectionValues, selectedOptions);

        Map<String, Map<String, Object>> sections = sectionsById(template);
        sections.forEach((sectionId, section) -> internalComponentService.validateComponentJsontData(
                (String) section.get(COMPONENT_UID),
                asMap(sectionValues, sectionId),
                mergedOptions(section, selectedOptions)));
    }

    private Map<String, Map<String, Object>> sectionsById(TemplateResponse template) {
        return template.sections().stream()
                .collect(Collectors.toMap(
                        section -> (String) section.get(SECTION_ID),
                        section -> section,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * 템플릿에 없는 sectionId를 허용하면 검증된 적 없는 임의 JSON이 jsonb에 쌓이고,
     * 나중에 템플릿이 그 섹션을 갖게 되면 그대로 살아난다.
     */
    private void validateKnownSections(Set<String> knownSectionIds, Map<String, Object> values) {
        if (values == null) {
            return;
        }
        values.keySet().stream()
                .filter(sectionId -> !knownSectionIds.contains(sectionId))
                .findFirst()
                .ifPresent(unknown -> {
                    throw new InvitationException(ErrorCode.UNKNOWN_SECTION_ID);
                });
    }

    private void validateOptions(Map<String, Map<String, Object>> sections,
                                 Map<String, Object> selectedOptions) {
        if (selectedOptions == null) {
            return;
        }
        selectedOptions.forEach((sectionId, raw) -> {
            Set<String> editable = editableKeys(sections.get(sectionId));
            asSelectedOptionMap(raw).forEach((optionKey, value) -> {
                if (!editable.contains(optionKey)) {
                    throw new InvitationException(ErrorCode.INVALID_INVITATION_OPTIONS);
                }
                validateAllowedValue(optionKey, value);
            });
        });
    }

    /**
     * selectedOptions의 섹션별 값은 반드시 Map이어야 한다. asMapOrEmpty를 그대로 쓰면
     * Map이 아닌 값(문자열, 리스트 등)이 조용히 빈 맵으로 취급되어 forEach가 아무 일도
     * 하지 않고, 그 결과 editable 검사와 허용값 검사를 통째로 우회하게 된다. 이는
     * selectedOptions에만 해당하는 문제이며, sectionValues는 DRAFT에서 스키마 완결성을
     * 보지 않는 설계이므로 여기서 강화하지 않는다.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> asSelectedOptionMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new InvitationException(ErrorCode.INVALID_INVITATION_OPTIONS);
        }
        return (Map<String, Object>) map;
    }

    private void validateAllowedValue(String optionKey, Object value) {
        OptionDefinitionResponse definition = optionDefinitionService.getOptionDefinition(optionKey);
        List<Object> allowed = definition.allowedValues();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(value)) {
            throw new InvitationException(ErrorCode.INVALID_INVITATION_OPTIONS);
        }
    }

    private Set<String> editableKeys(Map<String, Object> section) {
        if (section == null || !(section.get(EDITABLE) instanceof List<?> editable)) {
            return Set.of();
        }
        return editable.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    /** 템플릿 고정옵션 위에 사용자가 고른 값을 덮는다. */
    private Map<String, Object> mergedOptions(Map<String, Object> section,
                                              Map<String, Object> selectedOptions) {
        Map<String, Object> merged = new LinkedHashMap<>(asMapOrEmpty(section.get(OPTIONS)));
        merged.putAll(asMap(selectedOptions, (String) section.get(SECTION_ID)));
        return merged;
    }

    private Map<String, Object> asMap(Map<String, Object> source, String key) {
        return source == null ? Map.of() : asMapOrEmpty(source.get(key));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMapOrEmpty(Object raw) {
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
