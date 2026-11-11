// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.v2.SessionAdmissionLedger.Settings;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 수락을 외부에서 닫고 배출한 운영자가 사용하는 expected-epoch 전환 도구. HTTP API가 아니다. */
public final class SessionProviderTransition {
    private final JdbcTemplate jdbc;
    private final SessionTransactions transactions;
    public SessionProviderTransition(DataSource source,PlatformTransactionManager manager){jdbc=new JdbcTemplate(source);transactions=new SessionTransactions(source,manager);}
    private void lock(AuthDomain domain,Settings expected,String state){
        var rows=jdbc.query("SELECT PROVIDER_TYPE,PROVIDER_EPOCH,STORE_INSTANCE,STATE_CD FROM AI_AUTH_DOMAIN_STATE WHERE AUTH_DOMAIN=? FOR UPDATE",(row,index)->List.of(row.getString(1),row.getString(2),row.getString(3),row.getString(4)),domain.name());
        if(rows.size()!=1||!rows.getFirst().equals(List.of(expected.provider().name(),expected.epoch().toString(),expected.storeInstance(),state)))throw new IllegalStateException("배출 상태 또는 기대 저장소 판이 일치하지 않는다");
    }
    public void drain(AuthDomain domain,Settings expected){transactions.required(()->{lock(domain,expected,"ACTIVE");jdbc.update("UPDATE AI_AUTH_DOMAIN_STATE SET STATE_CD='DRAINED',CHANGED_AT=CURRENT_TIMESTAMP WHERE AUTH_DOMAIN=?",domain.name());return null;});}
    public int switchDrained(AuthDomain domain,Settings expected,Settings replacement){
        if(replacement==null||replacement.epoch()==null||replacement.epoch().equals(expected.epoch())||replacement.storeInstance()==null||replacement.storeInstance().isBlank())throw new IllegalArgumentException("새 전환epoch와 저장소 설치 식별자가 필요하다");
        return transactions.required(()->{
            lock(domain,expected,"DRAINED");int revoked=jdbc.update("UPDATE AI_AUTH_SESSION_GRANT SET STATE_CD='REVOKED',TERMINAL_AT=CURRENT_TIMESTAMP WHERE AUTH_DOMAIN=? AND STATE_CD<>'REVOKED'",domain.name());
            jdbc.update("UPDATE AI_AUTH_DOMAIN_STATE SET PROVIDER_TYPE=?,PROVIDER_EPOCH=?,STORE_INSTANCE=?,STATE_CD='ACTIVE',CHANGED_AT=CURRENT_TIMESTAMP WHERE AUTH_DOMAIN=?",replacement.provider().name(),replacement.epoch(),replacement.storeInstance(),domain.name());return revoked;
        });
    }
}
