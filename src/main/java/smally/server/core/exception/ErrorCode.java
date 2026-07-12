package smally.server.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 코드. HTTP 상태 + API 명세서(§0.3)의 code/message 를 함께 정의한다.
 */
@Getter
public enum ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "CONFLICT", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "유효하지 않거나 만료된 refresh 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
