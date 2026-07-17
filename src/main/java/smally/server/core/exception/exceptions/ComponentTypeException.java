package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ComponentTypeException extends BusinessException {
    public ComponentTypeException(ErrorCode errorCode) {
        super(errorCode);
    }
}
