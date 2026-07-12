package smally.server.core.exception.exceptions;

import lombok.Getter;
import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

@Getter
public class AuthException extends BusinessException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
}
