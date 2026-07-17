package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class OptionDefinitionException extends BusinessException {
    public OptionDefinitionException(ErrorCode errorCode) {
        super(errorCode);
    }
}
