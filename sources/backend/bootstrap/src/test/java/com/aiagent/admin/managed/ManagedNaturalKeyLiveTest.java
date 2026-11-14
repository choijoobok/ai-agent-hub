// @feat FEAT-040 FEAT-010
package com.aiagent.admin.managed;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("managedStorageLive") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ManagedNaturalKeyLiveTest {
    private ManagedLiveFixture f;
    private TypedHistoryStore history;
    private TransactionTemplate tx;
    @BeforeAll void start() throws Exception {
        f=new ManagedLiveFixture(ManagedNaturalKeyConfiguration.class);tx=new TransactionTemplate(f.bean(PlatformTransactionManager.class));
        history=store(ManagedTargetKeys.fixed(Set.of("USAGE_HELP","CHAT_START","INPUT_NOTICE")));
    }
    private TypedHistoryStore store(ManagedTargetKeys keys) {
        return new TypedHistoryStore(f.bean(DataSource.class),f.bean(ManagedIds.class),"FIXED_HELP","REVISION",
            List.of(new TypedHistoryStore.Table("FX_FIXED","HELP_CD",List.of("HELP_CD","REVISION","BODY"))),
            rows->true,rows->List.of(),keys);
    }
    @AfterAll void stop(){if(f!=null)f.close();}
    @Test void realCommandFilterAndJournalAcceptOnlyInstalledCodes() throws Exception {
        f.database().update("INSERT INTO fx_fixed VALUES ('CHAT_START',1,'초기')");
        var command=new ManagedCommand(UUID.randomUUID().toString(),"CHAT_START","1","content",Map.of("body","실제 저장"),"변경 사유");
        var result=f.adminCall("fixedKey","save",command,200);
        assertThat(result.path("targetId").asText()).isEqualTo("CHAT_START");
        assertThat(result.path("revision").asText()).isEqualTo("2");
        assertThat(f.adminCall("fixedKey","save",command,200)).isEqualTo(result);
        assertThat(f.bean(JdbcManagedJournal.class).find(command.commandKey()).orElseThrow().targetId()).isEqualTo("CHAT_START");
        var rejected=new ManagedCommand(UUID.randomUUID().toString(),"OTHER","1","content",Map.of("body","거부"),"사유");
        f.adminCall("fixedKey","save",rejected,400);
        f.adminCall("fixture","save",command,400);
        assertThat(f.bean(JdbcManagedJournal.class).find(rejected.commandKey())).isEmpty();
    }
    @Test void exactVersionsAndCurrentProtectionKeepNaturalKeys() {
        long first=f.bean(ManagedIds.class).next("AI_ADMIN_HISTORY_NO");
        long second=f.bean(ManagedIds.class).next("AI_ADMIN_HISTORY_NO");
        tx.executeWithoutResult(status->{
            f.database().update("INSERT INTO fx_fixed VALUES ('USAGE_HELP',1,'첫 원문')");
            history.capture("USAGE_HELP",stamp(first,"1"));
            f.database().update("UPDATE fx_fixed SET revision=2,body='새 원문' WHERE help_cd='USAGE_HELP'");
            history.capture("USAGE_HELP",stamp(second,"2"));
        });
        var old=history.exact(new ManagedHistoryStore.Ref("FIXED_HELP","USAGE_HELP","1"));
        var current=history.exact(new ManagedHistoryStore.Ref("FIXED_HELP","USAGE_HELP","2"));
        assertThat(old.historyNo()).isEqualTo(first);
        assertThat(old.rows().get("FX_FIXED").getFirst().get("body")).isEqualTo("첫 원문");
        assertThat(current.rows().get("FX_FIXED").getFirst().get("before_history_no")).isEqualTo(first);
        assertThat(history.current(old)).isFalse();assertThat(history.current(current)).isTrue();
        assertThat(history.locks(current.ref()).getFirst().id()).isEqualTo("USAGE_HELP");
        tx.executeWithoutResult(status->history.purge(old));
        assertThatThrownBy(()->history.exact(old.ref()));
        assertThat(history.exact(current.ref()).historyNo()).isEqualTo(second);
    }
    @Test void unregisteredCodesWrongKindsAndMismatchedStorageFail() {
        assertThatThrownBy(()->store(ManagedTargetKeys.DECIMAL)).isInstanceOf(IllegalStateException.class);
        var invalid=new ManagedHistoryStore.Ref("FIXED_HELP","OTHER","1");
        assertThatThrownBy(()->history.exact(invalid));assertThatThrownBy(()->history.locks(invalid));
        assertThatThrownBy(()->history.locks(new ManagedHistoryStore.Ref("OTHER","USAGE_HELP","1")));
        long before=f.database().queryForObject("SELECT COUNT(*) FROM fx_fixed_hist",Long.class);
        assertThatThrownBy(()->tx.executeWithoutResult(status->history.capture("OTHER",stamp(1,"1"))));
        assertThat(f.database().queryForObject("SELECT COUNT(*) FROM fx_fixed_hist",Long.class)).isEqualTo(before);
    }
    private ManagedAssetAdapter.Stamp stamp(long number,String revision) {
        return new ManagedAssetAdapter.Stamp(UUID.randomUUID().toString(),"admin",revision,number,Instant.parse("2026-11-14T00:00:00Z"),"CHANGE","시험 사유");
    }
}
