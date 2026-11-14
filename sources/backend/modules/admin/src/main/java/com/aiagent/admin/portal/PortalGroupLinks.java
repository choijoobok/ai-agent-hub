// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.group.UserGroupLinkSource;
import com.aiagent.domain.authz.ActionType;
import com.aiagent.domain.authz.service.*;
import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class PortalGroupLinks implements UserGroupLinkSource {
    private final JdbcTemplate jdbc;
    private final EffectiveAuthorityService authorities;
    private final HttpServletRequest request;
    private final PortalPolicy policy;
    public PortalGroupLinks(DataSource source,EffectiveAuthorityService authorities,HttpServletRequest request,PortalPolicy policy){jdbc=new JdbcTemplate(source);this.authorities=authorities;this.request=request;this.policy=policy;}
    public Source source(){return Source.membership;}
    public Projection read(long group) {
        var actor=request.getUserPrincipal();
        if(actor==null||AdminAuthorityProtection.currentToken()==null||!authorities.calculate(actor.getName()).allows("HUB_PORTAL_USER",ActionType.READ))return new Projection(State.FORBIDDEN,List.of());
        policy.protect(false);
        return new Projection(State.READY,jdbc.queryForList("SELECT CAST(U.PORTAL_USER_ID AS varchar) AS \"portalUserId\",U.LOGIN_ID AS \"loginId\",U.USER_NM AS \"userNm\",CAST(U.REVISION AS varchar) AS \"revision\",U.ACTIVE_YN AS \"userActiveYn\",M.ACTIVE_YN AS \"activeYn\",M.MEMO AS \"memo\",M.REG_ID AS \"regId\",M.REG_DTM AS \"regDtm\",M.MOD_ID AS \"modId\",M.MOD_DTM AS \"modDtm\" FROM AI_ADMIN_USER_MEMBERSHIP M JOIN AI_ADMIN_PORTAL_USER U ON U.PORTAL_USER_ID=M.PORTAL_USER_ID WHERE M.GROUP_ID=? ORDER BY U.LOGIN_ID",group));
    }
    public Reference references(long group){return jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_MEMBERSHIP WHERE GROUP_ID=?",Long.class,group)>0?Reference.PRESENT:Reference.ABSENT;}
}
