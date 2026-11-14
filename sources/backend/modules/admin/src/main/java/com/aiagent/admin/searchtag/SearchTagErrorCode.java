// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.core.error.*;
import com.aiagent.core.message.MsgKey;
public enum SearchTagErrorCode implements ErrorCode {
    INVALID;
    public String code(){return MsgKey.ERR_SEARCH_TAG_INVALID;}
    public ErrorType type(){return ErrorType.UNPROCESSABLE_ENTITY;}
    public String defaultMessage(){return "태그 표현·동의어 입력 또는 중복을 확인해 주세요.";}
    public BusinessException failure(){return new BusinessException(this);}
}
