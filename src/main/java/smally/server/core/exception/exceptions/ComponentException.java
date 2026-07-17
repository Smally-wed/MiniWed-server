package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ComponentException extends BusinessException {
    public ComponentException(ErrorCode errorCode) {
        super(errorCode);
    }
}
