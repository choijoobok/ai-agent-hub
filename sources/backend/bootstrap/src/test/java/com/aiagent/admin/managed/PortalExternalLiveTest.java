// @feat FEAT-005
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.portal.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;

@Tag("managedStorageLive")
class PortalExternalLiveTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Directory {
        static final PortalExternalDirectory.Identity VALID=new PortalExternalDirectory.Identity("test-hr","subject-1","admin","원천 이용자","원천 조직","r1");
        @Bean PortalExternalDirectory portalExternalFixture(){return (source,subject)->{if(!source.equals(VALID.sourceKey())||!subject.equals(VALID.subject()))throw ManagedErrorCode.UNAVAILABLE.failure();return VALID;};}
    }
    private ManagedCommand command(String id,String revision,Map<String,Object> values){return new ManagedCommand(UUID.randomUUID().toString(),id,revision,"profile",values,"검증 원천 연결 시험");}
    @Test @DisplayName("TC-0052 외부 검증 원장 연결·동명 ADMIN 분리·원천 실패 원자 거부")
    void verifiedDirectoryRegistrationNeverMergesOrFallsBack() throws Exception {
        try(var f=new ManagedLiveFixture("EXTERNAL",Directory.class)) {
            String month=f.database().queryForObject("SELECT to_char(CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul','YYYY-MM')",String.class);
            f.database().update("INSERT INTO ai_admin_quota_grade VALUES(900005,CURRENT_TIMESTAMP)");
            f.database().update("INSERT INTO ai_admin_quota_grade_set VALUES(900005,?,'시험',1,CURRENT_TIMESTAMP,'시험','admin',CURRENT_TIMESTAMP,'admin',CURRENT_TIMESTAMP)",month);
            f.database().update("INSERT INTO ai_admin_quota_grade_item VALUES(900005,900005,'기본',1,1,NULL,'')");
            var values=new HashMap<String,Object>(Map.of("externalSource","test-hr","externalSubject","unknown","useStartDt",month+"-01","activeYn","N"));
            values.put("useEndDt",null);
            f.adminCall("portalUser","save",command(null,null,values),503);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_portal_user",Integer.class)).isZero();
            values.put("externalSubject","subject-1");var request=command(null,null,values);var result=f.adminCall("portalUser","save",request,200);
            assertThat(f.adminCall("portalUser","save",request,200)).isEqualTo(result);
            String id=result.path("targetId").asText();
            assertThat(f.database().queryForObject("SELECT login_id FROM ai_admin_portal_user WHERE portal_user_id=?",String.class,Long.parseLong(id))).isEqualTo("admin");
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_portal_credential",Integer.class)).isZero();
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id='admin'",Integer.class)).isEqualTo(1);
            f.adminCall("portalUser","save",command(null,null,values),409);
            f.adminCall("portalUser","resetCredential",new ManagedCommand(UUID.randomUUID().toString(),id,"1","resetCredential",Map.of(),"외부 암호 변경 금지"),409);
            var tamper=new HashMap<String,Object>(Map.of("loginId","admin","userNm","관리자 덮어쓰기","organizationNm","원천 조직","useStartDt",month+"-01","activeYn","N"));tamper.put("useEndDt",null);
            f.adminCall("portalUser","save",command(id,"1",tamper),409);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_portal_external_id_hist WHERE portal_user_id=?",Integer.class,Long.parseLong(id))).isEqualTo(1);
        }
    }
}
