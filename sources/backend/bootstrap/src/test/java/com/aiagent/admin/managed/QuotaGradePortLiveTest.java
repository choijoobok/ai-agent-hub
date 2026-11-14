// @feat FEAT-042
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.quota.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** 후속 생산 원장을 대신하지 않는 계약 fixture. 같은 트랜잭션·발급된 참조 전달만 검증한다. */
@Tag("managedStorageLive")
class QuotaGradePortLiveTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class Config {
        @Bean Usage quotaUsage(DataSource source,jakarta.persistence.EntityManagerFactory ready){return new Usage(source);}
    }
    static class Usage implements QuotaGradeUsage {
        boolean fail=true; final JdbcTemplate jdbc;
        Usage(DataSource source){jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE fx_quota_usage (grade_set_id bigint,grade_id bigint)");}
        public List<ManagedLocks.Key> locks(Change change){return List.of(new ManagedLocks.Key(1,"QUOTA_MONTH",change.startMonth()),new ManagedLocks.Key(2,"PORTAL_USER","1"));}
        public void validate(Change change){assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();}
        public void apply(Change change,ManagedAssetAdapter.Stamp stamp) {
            assertThat(change.targetId()).matches("[1-9][0-9]*");
            for(var grade:change.after().grades())jdbc.update("INSERT INTO fx_quota_usage VALUES (?,?)",Long.parseLong(change.targetId()),Long.parseLong(grade.gradeId()));
            if(fail)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        }
        public References references(Collection<String> ids){return new References("READY",Map.of());}
    }
    @Test @DisplayName("TC-0173 후속 소유 포트 실패의 전체 원자성 및 안정 ID 인계")
    void ownerFailureRollsBackAndResolvedStableReferencesAreHandedOff() throws Exception {
        try(var f=new ManagedLiveFixture(Config.class)) {
            String month=YearMonth.from(f.bean(QuotaGradeGuard.class).now().atZone(ZoneId.of("Asia/Seoul"))).plusMonths(1).toString();
            var command=new ManagedCommand(UUID.randomUUID().toString(),null,null,"definition",Map.of("startMonth",month,"memo","연결 시험","grades",List.of(Map.of("clientKey","new","gradeNm","일반","rankNo",1,"weight","1","description","")),"transitionMappings",List.of()),"연결 검증");
            f.adminCall("quotaGrade","save",command,409);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM fx_quota_usage",Integer.class)).isZero();
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_set",Integer.class)).isZero();
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM ai_admin_quota_grade_set_hist",Integer.class)).isZero();
            f.bean(Usage.class).fail=false;f.adminCall("quotaGrade","save",command,200);
            assertThat(f.database().queryForObject("SELECT COUNT(*) FROM fx_quota_usage u JOIN ai_admin_quota_grade_item g USING(grade_set_id,grade_id)",Integer.class)).isEqualTo(1);
        }
    }
}
