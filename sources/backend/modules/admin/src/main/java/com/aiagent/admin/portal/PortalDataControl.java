// @feat FEAT-303
package com.aiagent.admin.portal;

import com.aiagent.core.access.GroupAccess.*;
import javax.sql.DataSource;

/** 행 스코프/동의/실행 승인 소유 검사. 외부 I/O 없이 현재 요청 근거를 확인한다. 미설치는 허용하지 않는다. */
public interface PortalDataControl {
    Control control();
    DataSource dataSource();
    Check check(Request request,Policy policy,PortalAccessService.Access account);
}
