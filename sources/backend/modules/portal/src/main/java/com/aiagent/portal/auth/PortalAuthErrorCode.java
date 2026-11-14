// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.core.error.*;
import com.aiagent.core.message.MsgKey;

public enum PortalAuthErrorCode implements ErrorCode {
    EVIDENCE_USED(ErrorType.CONFLICT,MsgKey.ERR_PORTAL_AUTH_EVIDENCE_USED,"이미 사용한 인증 요청입니다. 새로 로그인해 주세요"),
    REJECTED(ErrorType.UNAUTHENTICATED,MsgKey.ERR_LOGIN_FAILED,"로그인할 수 없습니다"),
    MODE(ErrorType.UNPROCESSABLE_ENTITY,MsgKey.ERR_PORTAL_AUTH_MODE,"현재 인증 모드에서 사용할 수 없습니다"),
    POLICY(ErrorType.UNPROCESSABLE_ENTITY,MsgKey.ERR_PORTAL_PASSWORD_POLICY,"비밀번호 정책을 확인해 주세요");
    private final ErrorType type;private final String key,message;
    PortalAuthErrorCode(ErrorType type,String key,String message){this.type=type;this.key=key;this.message=message;}
    public String code(){return key;}public ErrorType type(){return type;}public String defaultMessage(){return message;}
}
