package smally.server.core.exception.exceptions;

import smally.server.core.exception.BusinessException;
import smally.server.core.exception.ErrorCode;

public class ImageException extends BusinessException {
    public ImageException(ErrorCode errorCode) {
        super(errorCode);
    }
}
