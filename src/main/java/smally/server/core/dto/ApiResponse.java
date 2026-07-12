package smally.server.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

/**
 * 공통 API 성공 응답 래퍼. 에러 응답은 {@link smally.server.core.exception.ErrorResponse} 를 사용한다.
 *
 * @param success 성공 여부 (항상 true)
 * @param status  HTTP 상태 코드 값
 * @param data    응답 본문 데이터 (없으면 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        T data
) {

    /** 지정한 상태 코드와 데이터로 성공 응답을 만든다. */
    public static <T> ApiResponse<T> of(HttpStatus status, T data) {
        return new ApiResponse<>(true, status.value(), data);
    }

    /** 200 OK 성공 응답을 만든다. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, HttpStatus.OK.value(), data);
    }

    /** 201 Created 성공 응답을 만든다. */
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, HttpStatus.CREATED.value(), data);
    }

    /** 204 No Content 성공 응답을 만든다. */
    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(true, HttpStatus.NO_CONTENT.value(), null);
    }
}
