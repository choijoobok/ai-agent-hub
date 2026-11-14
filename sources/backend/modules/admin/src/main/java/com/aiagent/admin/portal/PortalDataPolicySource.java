// @feat FEAT-303
package com.aiagent.admin.portal;

import com.aiagent.core.access.GroupAccess.*;
import javax.sql.DataSource;

/** 자산 소유 공개 원천. DB 읽기만 수행하고 같은 PortalPolicy 보호 아래 완전한 현재 정책을 반환한다.
 * 자산/참조 정책의 모든 쓰기는 같은 DataSource 트랜잭션에서 PortalPolicy.protect(true)를 먼저 얻는다.
 * 대상 내용판과 현재 정책판을 구분하고 데이터소스 어휘 제약은 저장 시 검사한다. */
public interface PortalDataPolicySource {
    String assetKind();
    DataSource dataSource();
    Policy read(Request request);
}
