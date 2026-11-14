// @feat FEAT-006
package com.aiagent.admin.group;

import static com.aiagent.admin.group.UserGroupLinkSource.Source.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 소유 기능의 전체 미설치만 증명한다. 일부 설치·남은 이력은 검사 포트 없이 추정하지 않는다. */
@Service
public class UserGroupStorageQueryService {
    private static final Map<UserGroupLinkSource.Source,List<String>> STORES=Map.of(
            membership,List.of("AI_ADMIN_PORTAL_USER","AI_ADMIN_USER_MEMBERSHIP"),
            knowledge,List.of("AI_ADMIN_KNOWLEDGE","AI_ADMIN_KNOWLEDGE_GROUP"),
            catalog,List.of("AI_ADMIN_CATALOG","AI_ADMIN_CATALOG_GROUP","AI_ADMIN_COLUMN_ACCESS_POLICY"),
            api,List.of("AI_ADMIN_BUSINESS_API","AI_ADMIN_API_GROUP","AI_ADMIN_API_RESPONSE_POLICY"),
            tool,List.of("AI_ADMIN_BUSINESS_TOOL","AI_ADMIN_TOOL_GROUP","AI_ADMIN_TOOL_RESPONSE_POLICY"),
            datasource,List.of("AI_ADMIN_DATA_SOURCE","AI_ADMIN_DATA_SOURCE_GROUP"));
    private final JdbcTemplate jdbc;
    public UserGroupStorageQueryService(DataSource source){jdbc=new JdbcTemplate(source);}

    /** 선택 그룹의 참조 판정마다 한 번 조사한다. 삭제 요청에서는 캐시 없이 같은 트랜잭션의 DB를 다시 읽는다. */
    public Set<UserGroupLinkSource.Source> uninstalled() {
        return jdbc.execute((ConnectionCallback<Set<UserGroupLinkSource.Source>>) connection->{
            String schema=connection.getSchema();
            if(schema==null||schema.isBlank())return Set.of();
            var tables=new HashSet<String>();
            // 타입 제한 없이 테이블·뷰·외부 테이블도 설치 흔적으로 본다. 이름의 '_' 패턴 오인도 피한다.
            try(var rows=connection.getMetaData().getTables(connection.getCatalog(),schema,null,null)) {
                while(rows.next())tables.add(rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
            }
            if(!tables.contains("AI_ADMIN_USER_GROUP")||!tables.contains("AI_ADMIN_USER_GROUP_HIST"))return Set.of();
            var absent=EnumSet.noneOf(UserGroupLinkSource.Source.class);
            STORES.forEach((owner,names)->{
                if(names.stream().noneMatch(name->tables.contains(name)||tables.contains(name+"_HIST")))absent.add(owner);
            });
            return Set.copyOf(absent);
        });
    }
}
