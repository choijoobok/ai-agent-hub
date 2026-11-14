// @feat FEAT-305
package com.aiagent.portal.change;

import com.aiagent.core.error.*;
import com.aiagent.core.message.MsgKey;
import java.util.Arrays;

/** 안전 상태만 번역한다. 원천 예외/본문은 메시지의 인자가 아니다. */
public record SourceChangeError(ErrorType type) implements ErrorCode {
    public static SourceChangeError of(int status){return new SourceChangeError(Arrays.stream(ErrorType.values()).filter(t->t.status()==status).findFirst().orElse(ErrorType.SERVICE_UNAVAILABLE));}
    public String code(){return MsgKey.ERR_SOURCE_CHANGE;}
    public String defaultMessage(){return "외부 사건을 처리할 수 없습니다. 원천과 처리 결과를 확인해 주세요";}
}
