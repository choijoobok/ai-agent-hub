// @feat FEAT-001 FEAT-302
package com.aiagent.support.deployment;

import com.aiagent.core.deployment.BusinessApiInvoker;
import java.util.*;
import org.springframework.stereotype.Component;

/** 명시한 인터페이스 구현 빈만 선택한다. 임의 빈 조회·반사 실행·기본 대체는 없다. */
@Component
public final class BusinessApiInvokers {
    private final Map<String,BusinessApiInvoker> registered;
    public BusinessApiInvokers(Map<String,BusinessApiInvoker> registered){this.registered=Map.copyOf(registered);}
    public BusinessApiInvoker require(String bean,String kind,String authentication) {
        var selected=bean==null?null:registered.get(bean);
        if(selected==null)throw new BusinessApiInvoker.Failure("INVOKER_MISSING");
        if(!selected.kinds().contains(kind)||!selected.authenticationTypes().contains(authentication))throw new BusinessApiInvoker.Failure("INVOKER_UNSUPPORTED");
        return selected;
    }
}
