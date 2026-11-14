// @feat FEAT-305
package com.aiagent.bootstrap.config;

import com.aiagent.admin.managed.*;
import com.aiagent.portal.change.ChangeStore;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;

/** 제품 모듈 사이에서는 공개 소유 포트만 연결한다. */
@Configuration
public class SourceChangeConfiguration {
    @Bean ManagedJournal sourceChangeJournal(ChangeStore store) {return new ManagedJournal() {
        public DataSource dataSource(){return store.dataSource();}
        public String name(){return "ENT-257";}
        public Optional<Entry> find(String key){var e=store.findCommand(key);return e==null?Optional.empty():Optional.of(new Entry(e.commandKey(),"FEAT-305",null,e.digest(),"change-body/v1","PORTAL_USER",e.portalUserId(),e.revision(),e.historyNo(),e.outcome(),e.appliedAt(),e.receivedAt()));}
        public void insert(Entry entry){throw new IllegalStateException("외부 수신 결과는 소유 수신 포트만 저장한다");}
        public void delete(String key){throw new IllegalStateException("외부 사건 최소키 보호와 소유 보존 검수가 필요하다");}
        public boolean references(long historyNo){return store.references(historyNo);}
    };}
    @Bean ManagedHistoryService.Consumer sourceChangeHistoryConsumer(ChangeStore store) {return new ManagedHistoryService.Consumer() {
        public String module(){return "integration";}
        public String kind(){return "EXTERNAL_USER_CHANGE";}
        public void requireActive(String key,String purpose){var row=store.findReceipt(key);if(!purpose.equals("ORIGIN")||row==null||row.historyNo()==null)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();}
        public void requireReadable(String key,String purpose){throw ManagedErrorCode.FORBIDDEN.failure();}
        public void requireEnded(String key,String purpose){if(!purpose.equals("ORIGIN")||!store.isEnding(key))throw ManagedErrorCode.FORBIDDEN.failure();}
    };}
}
