// @feat FEAT-005
package com.aiagent.admin.portal;

/** 고객 원천의 검증기는306에서 연결한다. 검증된 로컬 원장만 읽으며 보호 잠금 안에서 외부 통신하지 않는다. */
public interface PortalExternalDirectory {
    record Identity(String sourceKey,String subject,String loginId,String userNm,String organizationNm,String sourceRevision) {}
    Identity verified(String sourceKey,String subject);
}
