// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import java.util.*;
import javax.sql.DataSource;

/** 자격정보와 암호이력을 제외한 Portal 부모 전체 구성만 복사한다. */
public final class PortalHistory {
    private PortalHistory() {}
    public static ManagedHistoryStore create(DataSource source,ManagedIds ids) {
        return new TypedHistoryStore(source,ids,"PORTAL_USER","REVISION",List.of(
                new TypedHistoryStore.Table("AI_ADMIN_PORTAL_USER","PORTAL_USER_ID",List.of("PORTAL_USER_ID","LOGIN_ID","USER_NM","ORGANIZATION_NM","AUTH_MODE_CD","SOURCE_CD","USE_START_DT","USE_END_DT","ACTIVE_YN","REVISION","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
                new TypedHistoryStore.Table("AI_ADMIN_USER_MEMBERSHIP","PORTAL_USER_ID",List.of("PORTAL_USER_ID","GROUP_ID","ACTIVE_YN","MEMO","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
                new TypedHistoryStore.Table("AI_ADMIN_USER_IDENTITY","PORTAL_USER_ID",List.of("PORTAL_USER_ID","TARGET_KIND","TARGET_ID","EXTERNAL_USER_ID","MAPPING_ACTIVE_YN","IDENTITY_EVIDENCE","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
                new TypedHistoryStore.Table("AI_ADMIN_USER_QUOTA_GRADE","PORTAL_USER_ID",List.of("PORTAL_USER_ID","EFFECTIVE_MONTH","GRADE_ID","ACTIVE_YN","SOURCE_KIND","SOURCE_KEY","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
                new TypedHistoryStore.Table("AI_ADMIN_PORTAL_EXTERNAL_ID","PORTAL_USER_ID",List.of("PORTAL_USER_ID","SOURCE_KEY","SUBJECT_ID","LINKED_DTM","SOURCE_REVISION","BINDING_KEY"))),rows->!rows.get("AI_ADMIN_USER_QUOTA_GRADE").isEmpty(),rows->List.of());
    }
}
