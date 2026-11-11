// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import com.aiagent.core.error.ErrorCode;
import com.aiagent.core.error.ErrorType;
import com.aiagent.core.message.MsgKey;

/** 인증 조립의 판정 장애는 정상 거부/세션 만료와 구분한다. */
public enum BoundaryErrorCode implements ErrorCode {
    UNAVAILABLE;
    @Override public String code() { return MsgKey.ERR_AUTH_CONTEXT_UNAVAILABLE; }
    @Override public ErrorType type() { return ErrorType.SERVICE_UNAVAILABLE; }
    @Override public String defaultMessage() { return "인증 상태를 확인할 수 없습니다. 잠시 후 다시 접속해 주세요"; }
}
