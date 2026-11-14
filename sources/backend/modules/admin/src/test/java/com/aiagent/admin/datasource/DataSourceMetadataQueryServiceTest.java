// @feat FEAT-002
package com.aiagent.admin.datasource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
class DataSourceMetadataQueryServiceTest {
    @Test @org.junit.jupiter.api.DisplayName("TC-0024 TC-00220 TC-0067 선택 카탈로그·DS 중복 제거·현재 범위 및 SQL 사용 차단") @SuppressWarnings("unchecked") void selectedCatalogsDeduplicateGuidesAndRequireCurrentScopeAndSqlSupport(){
        var query=mock(DataSourceQueryService.class);var owner=mock(DataSourceService.class);var scope=mock(DataSourceCatalogScope.class);ObjectProvider<DataSourceCatalogScope> providers=mock(ObjectProvider.class);when(providers.getIfUnique()).thenReturn(scope);when(providers.getObject()).thenReturn(scope);
        var root=new DataSourceRecord();var row=mock(DataSourceQueryService.Row.class);when(query.root("7")).thenReturn(root);when(query.row(root)).thenReturn(row);
        when(row.configurationMismatch()).thenReturn(false);when(row.connectionStatus()).thenReturn("AVAILABLE");when(row.managementVersion()).thenReturn("3");when(row.dbmsCode()).thenReturn("POSTGRESQL");when(row.dbmsVersion()).thenReturn("17");
        var values=DataSourceDefinitionTest.values();values.put("queryGuides",List.of(Map.of("guideId","19","title","코드 확인","description","권한을 완화하지 않는 참고")));when(owner.values("7")).thenReturn(values);
        when(scope.authorizedDataSources(Set.of("11","12"))).thenReturn(Map.of("11","7","12","7"));var service=new DataSourceMetadataQueryService(query,owner,providers);
        var result=service.readSelected(Set.of("11","12"));assertThat(result).hasSize(1);assertThat(result.getFirst().guides()).hasSize(1);assertThat(result.getFirst().guides().getFirst().guideId()).isEqualTo("19");
        when(scope.authorizedDataSources(Set.of("11"))).thenReturn(Map.of("11","7"));service.requireSqlUsable("11");verify(scope).requireSqlSupport("7","POSTGRESQL","17");
        verify(query,atLeastOnce()).requireRevision("7","3");
        doThrow(new IllegalStateException("조회 중 판 변경")).when(query).requireRevision("7","3");assertThatThrownBy(()->service.readSelected(Set.of("11"))).isInstanceOf(RuntimeException.class);doNothing().when(query).requireRevision("7","3");
        when(row.configurationMismatch()).thenReturn(null);assertThatThrownBy(()->service.requireSqlUsable("11")).isInstanceOf(RuntimeException.class);
        when(scope.authorizedDataSources(Set.of("13"))).thenReturn(Map.of("99","7"));assertThatThrownBy(()->service.readSelected(Set.of("13"))).isInstanceOf(RuntimeException.class);
        when(providers.getIfUnique()).thenReturn(null);assertThatThrownBy(()->service.readSelected(Set.of("11"))).isInstanceOf(RuntimeException.class);
    }
}
