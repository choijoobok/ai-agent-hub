// @feat FEAT-003
package com.aiagent.admin.managed;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.deployment.CatalogSource;
import com.aiagent.support.deployment.PostgresCatalogSource;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
@Tag("managedStorageLive")
class CatalogSourceLiveTest {
    @Test @DisplayName("TC-0032 TC-00316 TC-00318 PostgreSQL 정확 이름·원천 치수·전체 키·뷰 미확인과 원문 인덱스")
    void readsOnlyExactObjectAndPreservesStructuredFacts()throws Exception{
        var url=System.getenv("MANAGED_TEST_JDBC");if(url==null||!url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/managed040_[A-Za-z0-9_]+"))throw new IllegalStateException("격리된 원천 시험 DB가 필요하다");
        var schema="catalog003_"+UUID.randomUUID().toString().replace("-","");var at=Instant.parse("2026-11-14T00:00:00Z");
        try(var c=DriverManager.getConnection(url,System.getenv("MANAGED_TEST_USER"),System.getenv().getOrDefault("MANAGED_TEST_PASSWORD",""))){
            try(var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);s.execute("CREATE TABLE "+schema+".\"Order_%\"(id bigint NOT NULL, tenant integer NOT NULL, amount numeric(12,3), title varchar(21), PRIMARY KEY(tenant,id), UNIQUE(id,title))");s.execute("CREATE INDEX fx_catalog003_expression ON "+schema+".\"Order_%\" ((lower(title))) INCLUDE(amount) WHERE tenant>0");s.execute("CREATE VIEW "+schema+".v_orders AS SELECT id,title FROM "+schema+".\"Order_%\"");}
            String db=c.getCatalog();
            var actual=PostgresCatalogSource.read(c,PostgresCatalogSource.identity(new CatalogSource.Names(null,db,schema,"Order_%")),"settings","boot",at);
            assertThat(c.isReadOnly()).isTrue();assertThat(actual.state()).isEqualTo("COMPLETE");assertThat(actual.productVersion()).isNotBlank();assertThat(actual.columns()).hasSize(4);assertThat(actual.observedAt()).isEqualTo(at);
            var amount=actual.columns().stream().filter(x->x.name().equals("amount")).findFirst().orElseThrow();assertThat(amount.numericPrecision()).isEqualTo(12);assertThat(amount.numericScale()).isEqualTo(3);
            assertThat(actual.columns().stream().filter(x->x.name().equals("title")).findFirst().orElseThrow().characterLength()).isEqualTo(21);
            assertThat(actual.keys().stream().filter(x->x.kind().equals("PRIMARY")).findFirst().orElseThrow().columns()).containsExactly("tenant","id");
            var index=actual.indexes().stream().filter(x->x.name().equals("fx_catalog003_expression")).findFirst().orElseThrow();assertThat(index.keyExpressions().getFirst()).contains("lower");assertThat(index.includedColumns()).containsExactly("amount");assertThat(index.predicate()).contains("tenant");
            var view=PostgresCatalogSource.read(c,PostgresCatalogSource.identity(new CatalogSource.Names(null,db,schema,"v_orders")),"settings","boot",at);assertThat(view.objectType()).isEqualTo("VIEW");assertThat(view.columns()).allMatch(x->x.nullable().equals("UNKNOWN"));assertThat(view.keys()).isEmpty();
            var wrongCase=PostgresCatalogSource.read(c,PostgresCatalogSource.identity(new CatalogSource.Names(null,db,schema,"order_%")),"settings","boot",at);assertThat(wrongCase.state()).isEqualTo("UNAVAILABLE");assertThat(wrongCase.reason()).isEqualTo("ABSENCE_NOT_PROVEN");
            assertThat(PostgresCatalogSource.read(c,PostgresCatalogSource.identity(new CatalogSource.Names(null,"another_db",schema,"Order_%")),"settings","boot",at).reason()).isEqualTo("CROSS_DATABASE");
        }
    }
}
