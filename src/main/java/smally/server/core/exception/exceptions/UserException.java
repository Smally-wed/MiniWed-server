package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class UserException extends BusinessException {
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
