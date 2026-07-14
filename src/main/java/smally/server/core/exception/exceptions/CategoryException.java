package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class CategoryException extends BusinessException {
    public CategoryException(ErrorCode errorCode) {
        super(errorCode);
    }
}
