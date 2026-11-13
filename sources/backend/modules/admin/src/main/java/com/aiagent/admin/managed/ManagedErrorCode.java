// @feat FEAT-040
package com.aiagent.admin.managed;

import com.aiagent.core.error.*;
import com.aiagent.core.message.MsgKey;

public enum ManagedErrorCode implements ErrorCode {
    VALIDATION(MsgKey.ERR_MANAGED_VALIDATION, ErrorType.INVALID_REQUEST, "관리 저장 입력이 올바르지 않습니다."),
    FORBIDDEN(MsgKey.ERR_MANAGED_FORBIDDEN, ErrorType.FORBIDDEN, "현재 관리 변경 권한이 없습니다."),
    REVISION_CONFLICT(MsgKey.ERR_MANAGED_REVISION_CONFLICT, ErrorType.CONFLICT, "기준 정보가 변경되었습니다. 다시 조회해 주세요."),
    REFERENCE_CONFLICT(MsgKey.ERR_MANAGED_REFERENCE_CONFLICT, ErrorType.CONFLICT, "현재 참조 때문에 변경할 수 없습니다."),
    COMMAND_CONFLICT(MsgKey.ERR_MANAGED_COMMAND_CONFLICT, ErrorType.CONFLICT, "다른 저장 의도에 사용한 명령 키입니다."),
    COMMAND_EXPIRED(MsgKey.ERR_MANAGED_COMMAND_EXPIRED, ErrorType.CONFLICT, "보존 기간이 종료된 명령입니다."),
    HISTORY_UNAVAILABLE(MsgKey.ERR_MANAGED_HISTORY_UNAVAILABLE, ErrorType.CONFLICT, "정확한 과거 구성을 확인할 수 없습니다."),
    UNAVAILABLE(MsgKey.ERR_MANAGED_UNAVAILABLE, ErrorType.SERVICE_UNAVAILABLE, "관리 저장 원천을 확인할 수 없습니다. 원 명령으로 결과를 확인해 주세요.");

    private final String code;
    private final ErrorType type;
    private final String message;
    ManagedErrorCode(String code, ErrorType type, String message) { this.code=code; this.type=type; this.message=message; }
    public String code() { return code; }
    public ErrorType type() { return type; }
    public String defaultMessage() { return message; }
    public BusinessException failure() { return new BusinessException(this); }
}
