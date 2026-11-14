// @feat FEAT-006
package com.aiagent.admin.group;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.aiagent.admin.group.UserGroupLinkSource.Source.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class UserGroupStorageQueryServiceTest {
    private DataSource source(String... tables) throws Exception {
        var source=mock(DataSource.class);var connection=mock(Connection.class);var metadata=mock(DatabaseMetaData.class);var result=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getSchema()).thenReturn("fixture");when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(null,"fixture",null,null)).thenReturn(result);
        var names=new ArrayList<>(List.of("ai_admin_user_group","ai_admin_user_group_hist"));names.addAll(List.of(tables));
        var index=new java.util.concurrent.atomic.AtomicInteger(-1);
        when(result.next()).thenAnswer(call->index.incrementAndGet()<names.size());
        when(result.getString("TABLE_NAME")).thenAnswer(call->names.get(index.get()));
        return source;
    }
    @Test void onlyFullyUninstalledOwnerIsAbsentIncludingHiddenPolicyAndHistory() throws Exception {
        assertThat(new UserGroupStorageQueryService(source()).uninstalled()).containsExactlyInAnyOrder(values());
        var absent=new UserGroupStorageQueryService(source("ai_admin_column_access_policy","ai_admin_portal_user_hist","ai_admin_business_tool")).uninstalled();
        assertThat(absent).containsExactlyInAnyOrder(knowledge,api,datasource);
    }
    @Test void metadataFailureIsNotAnEmptyInstallation() throws Exception {
        var failed=mock(DataSource.class);when(failed.getConnection()).thenThrow(new SQLException("시험용 메타데이터 접근 실패"));
        assertThatThrownBy(()->new UserGroupStorageQueryService(failed).uninstalled()).isInstanceOf(DataAccessException.class);
    }
}
