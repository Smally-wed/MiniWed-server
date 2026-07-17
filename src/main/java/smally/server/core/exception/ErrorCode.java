package smally.server.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 코드. HTTP 상태 + API 명세서(§0.3)의 code/message 를 함께 정의한다.
 */
@Getter
public enum ErrorCode {

    DATABASE_TRANSACTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "DB transaction 오류"),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "CONFLICT", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "유효하지 않거나 만료된 refresh 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "소셜 로그인 제공자와 통신 중 오류가 발생했습니다."),
    OAUTH_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "소셜 계정의 이메일 제공 동의가 필요합니다."),
    OAUTH_EMAIL_NOT_VERIFIED(HttpStatus.CONFLICT, "CONFLICT",
            "이미 가입된 이메일이며 소셜 계정의 이메일이 검증되지 않아 자동 연결할 수 없습니다."),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND,"NOT_FOUND", "템플릿 정보를 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "존재하지 않는 카테고리입니다."),
    DUPLICATE_CATEGORY(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 카테고리입니다."),
    INVALID_SECTION_VALUES(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "청첩장 입력값이 템플릿 스키마와 맞지 않습니다."),
    DUPLICATE_COMPONENT_TYPE(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 컴포넌트 종류입니다."),
    COMPONENT_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "존재하지 않는 컴포넌트 종류입니다."),
    COMPONENT_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "컴포넌트를 찾을 수 없습니다."),
    INVALID_COMPONENT_SCHEMA(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "컴포넌트 스키마가 유효한 JSON Schema가 아닙니다."),
    DUPLICATE_OPTION_DEFINITION(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 옵션 키입니다."),
    OPTION_DEFINITION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "옵션 정의를 찾을 수 없습니다."),
    INVALID_OPTION_DEFAULT(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "기본값이 허용값 목록에 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
