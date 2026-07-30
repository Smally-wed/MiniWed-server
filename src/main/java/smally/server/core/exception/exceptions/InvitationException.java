package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class InvitationException extends BusinessException {
    public InvitationException(ErrorCode errorCode) {
        super(errorCode);
    }
}
