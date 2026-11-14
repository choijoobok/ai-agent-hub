// @feat FEAT-007
package com.aiagent.admin.catalog;

import com.aiagent.admin.managed.ManagedCommand;
import com.aiagent.admin.portal.PortalDataPolicySource;
import com.aiagent.admin.portal.PortalPolicy;
import com.aiagent.core.access.GroupAccess.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 등록 메타정보 전용 현재 그룹 관문. 업무 값 투영이나 실행 허가로 확장하지 않는다. */
@Component
public final class CatalogMetadataPolicySource implements PortalDataPolicySource {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final PortalPolicy protection;

    public CatalogMetadataPolicySource(DataSource source, PortalPolicy protection) {
        this.source = source;
        this.jdbc = new JdbcTemplate(source);
        this.protection = protection;
    }
    @Override public String assetKind() { return "CATALOG"; }
    @Override public DataSource dataSource() { return source; }
    @Override public Policy read(Request request) {
        protection.protect(false);
        if (!assetKind().equals(request.target().kind()) || request.purpose() != Purpose.MODEL_INPUT || !request.fields().isEmpty())
            return unavailable(request.target());
        long id = ManagedCommand.positive(request.target().id());
        var rows = jdbc.queryForList("SELECT REVISION,USE_YN FROM AI_ADMIN_CATALOG WHERE CATALOG_ID=?", id);
        if (rows.size() != 1 || !"Y".equals(rows.getFirst().get("USE_YN"))) return unavailable(request.target());
        String revision = rows.getFirst().get("REVISION").toString();
        var current = new Target(assetKind(), request.target().id(), revision);
        if (!current.equals(request.target())) return unavailable(current);
        var groups = new HashSet<>(jdbc.queryForList("SELECT GROUP_ID::text FROM AI_ADMIN_CATALOG_GROUP WHERE CATALOG_ID=? AND ACTIVE_YN='Y'", String.class, id));
        return new Policy(current, true, List.of(new Gate("catalog", current, revision, groups)), Map.of(), Set.of(), metadataRequirements());
    }
    private static Policy unavailable(Target target) {
        return new Policy(target, false, List.of(), Map.of(), Set.of(), metadataRequirements());
    }
    private static Requirements metadataRequirements() {
        // CR-056: 계정·그룹은 공통 판정기가 검사한다. 추가 관문은 실제 호출·조회·전송의 소유자 책임이다.
        return new Requirements(false, null, null, Set.of());
    }
}
