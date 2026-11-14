// @feat FEAT-503
package com.aiagent.system.api.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.v2.SessionAuthority.Context;
import com.aiagent.domain.authz.*;
import com.aiagent.domain.authz.service.*;
import com.aiagent.domain.user.entity.SysUser;
import com.aiagent.domain.user.service.UserAccountService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.AuthLoginProps;
import com.aiagent.support.time.BusinessCalendar;
import com.aiagent.system.api.auth.*;
import com.aiagent.system.domain.auth.AllowedIpService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;

/** 캐시/dirty 대신 현재 원천과 동일 트랜잭션의 업무 효과를 검증한다. PG 경합은 systemAuthTest. */
public class AuthorizationInterceptorTest {
    @Test @DisplayName("TC-3054 public 이름·위조 속성은 원천 검증을 대체하지 않는다")
    void publicSourcePathCannotBypassBoundaryWithInventedAttribute() {
        var request=new MockHttpServletRequest("POST","/api/integration/public/identity/receiveChange");
        request.setAttribute(com.aiagent.api.auth.v2.SessionV2Filter.class.getName()+".sourceAcceptance",Boolean.TRUE);
        assertThatThrownBy(()->new AuthorizationInterceptor().preHandle(request,new org.springframework.mock.web.MockHttpServletResponse(),new Object())).isInstanceOf(IllegalStateException.class);
    }
    public static int testLock(int first, int second) { return 1; }
    private final UserAccountService users = mock(UserAccountService.class);
    private final EffectiveAuthorityService authorities = mock(EffectiveAuthorityService.class);
    private final ApiAuthorityRegistry registry = mock(ApiAuthorityRegistry.class);
    private final AllowedIpService ips = mock(AllowedIpService.class);
    private final ConfigLookup configs = mock(ConfigLookup.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final AdminApiCatalog catalog = mock(AdminApiCatalog.class);
    private final SysUser user = mock(SysUser.class);
    private final Instant now = Instant.parse("2026-11-14T00:00:00Z");
    private final EffectiveAuthority allowed = new EffectiveAuthority(Set.of(new MenuAction("USER", ActionType.SAVE)), LocalDate.of(2026,11,14));
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AdminAuthorityGuard guard;
    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source); tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.execute("CREATE ALIAS pg_advisory_xact_lock FOR 'com.aiagent.system.api.authz.AuthorizationInterceptorTest.testLock'");
        jdbc.execute("CREATE ALIAS pg_advisory_xact_lock_shared FOR 'com.aiagent.system.api.authz.AuthorizationInterceptorTest.testLock'");
        jdbc.execute("CREATE TABLE EFFECT(V INT)");
        guard = new AdminAuthorityGuard(source, entities, users, authorities, registry, ips, configs,
                new BusinessCalendar("Asia/Seoul", Clock.fixed(now, ZoneOffset.UTC)), catalog);
        when(users.find("kim")).thenReturn(Optional.of(user));
        when(user.getUserId()).thenReturn("kim"); when(user.getUseYn()).thenReturn(true);
        when(user.isWithinValidPeriod(any())).thenReturn(true);
        when(ips.isAllowed(eq("kim"), any())).thenReturn(true);
        when(configs.get(AuthLoginProps.class)).thenReturn(new AuthLoginProps(5,30,true));
        when(authorities.calculate("kim")).thenReturn(allowed);
        when(catalog.entry("save")).thenReturn(new AdminApiCatalog.Entry("save","POST","/api/system/service/user/save","handler","SAVE"));
        when(registry.find("/api/system/service/user/save")).thenReturn(Optional.of(new ApiAuthorityFact("/api/system/service/user/save",ActionType.SAVE,Set.of("USER"))));
        AdminRequestContext.bind(new MockHttpServletRequest());
    }
    @AfterEach void close() { AdminRequestContext.clear(); jdbc.execute("SHUTDOWN"); }
    private Context context(boolean self) {
        var principal = new PrincipalRef(AuthDomain.ADMIN,"kim");
        return new Context(AuthDomain.ADMIN,principal,null,new Identity(principal,AuthLevel.AUTHENTICATED,"LOCAL",null,now,"김","ko"),self?"me":"save",self?null:"SAVE",self,Capability.READ,"test");
    }
    private void execute(boolean self) { tx.executeWithoutResult(status -> guard.protect(context(self), false, () -> jdbc.update("INSERT INTO EFFECT VALUES(1)"))); }
    private void noEffect() { assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM EFFECT",Integer.class)).isZero(); }
    @Test @DisplayName("TC-50110 dirty 유실·당일 캐시와 무관하게 삭제 계정을 거부한다")
    void disabledAccountIsRejectedEvenWhenCachedAuthorityIsFresh() {
        when(users.find("kim")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> execute(true)).isInstanceOf(com.aiagent.core.security.v2.SessionAuthority.Unusable.class); noEffect();
    }
    @ParameterizedTest @ValueSource(strings={"disabled","expired","locked","dormant","ip"})
    void currentUnusableAccountNeverReachesBusinessWrite(String condition) {
        switch(condition) {
            case "disabled" -> when(user.getUseYn()).thenReturn(false);
            case "expired" -> when(user.isWithinValidPeriod(any())).thenReturn(false);
            case "locked" -> when(user.isLocked(any(),anyInt())).thenReturn(true);
            case "dormant" -> when(user.getDormantYn()).thenReturn(true);
            case "ip" -> when(ips.isAllowed(any(),any())).thenReturn(false);
            default -> throw new AssertionError();
        }
        assertThatThrownBy(() -> execute(false)).isInstanceOf(com.aiagent.core.security.v2.SessionAuthority.Unusable.class); noEffect();
    }
    @Test @DisplayName("TC-5027 TC-5031 현재 허용은 실행하고 다음 요청은 dirty 없이 회수된 권한으로 거부한다")
    void recalculatesEveryRequestAndRejectsRevokedPermission() {
        execute(false);
        when(authorities.calculate("kim")).thenReturn(new EffectiveAuthority(Set.of(), LocalDate.of(2026,11,14)));
        assertThatThrownBy(() -> execute(false)).isInstanceOfSatisfying(Rejected.class,e -> assertThat(e.status()).isEqualTo(403));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM EFFECT",Integer.class)).isEqualTo(1);
        verify(authorities,times(2)).calculate("kim");
    }
    @Test @DisplayName("TC-5033 미등록·메뉴 매핑 부재는 업무 효과를 남기지 않는다")
    void absentMenuMappingCannotBecomeAllowed() {
        when(registry.find(any())).thenReturn(Optional.of(new ApiAuthorityFact("path",ActionType.SAVE,Set.of())));
        assertThatThrownBy(() -> execute(false)).isInstanceOf(Rejected.class); noEffect();
    }
    @Test @DisplayName("TC-5034 URL·역할 이름 대신 정확 액션을 확인한다")
    void actionMismatchCannotUseDifferentPermission() {
        when(registry.find(any())).thenReturn(Optional.of(new ApiAuthorityFact("path",ActionType.READ,Set.of("USER"))));
        assertThatThrownBy(() -> execute(false)).isInstanceOf(Rejected.class); noEffect();
    }
    @Test @DisplayName("TC-5028 자격 검증 뒤 최종 발급 보호에서 현재 계정 불가를 다시 확인한다")
    void accountRevokedBeforeAdmissionCannotPublish() {
        when(user.getUseYn()).thenReturn(false);
        var prior=context(true);
        var login=new Context(prior.domain(),prior.principal(),null,prior.identity(),"API-501",null,true,Capability.LOGIN,"test");
        assertThatThrownBy(()->tx.executeWithoutResult(s->guard.protect(login,true,()->jdbc.update("INSERT INTO EFFECT VALUES(1)")))).isInstanceOf(com.aiagent.core.security.v2.SessionAuthority.Unusable.class);
        noEffect();
    }
    @ParameterizedTest(name="{0}: 현재 메뉴 권한 없이 {1}의 업무 효과를 남길 수 없다")
    @org.junit.jupiter.params.provider.CsvSource({"TC-5044,API-551", "TC-5054,API-556", "TC-5064,API-560", "TC-5074,API-563", "TC-5084,API-572", "TC-5084,API-575"})
    void servicePermissionsRemainSeparated(String testcase,String operation) throws Exception {
        var environment=new org.springframework.mock.env.MockEnvironment();environment.setActiveProfiles("local");
        var entry=new AdminApiCatalog(environment,mock(com.aiagent.domain.progress.JobProgressStore.class)).entry(operation);
        when(catalog.entry(operation)).thenReturn(entry);
        when(registry.find(entry.path())).thenReturn(Optional.of(new ApiAuthorityFact(entry.path(),ActionType.valueOf(entry.action()),Set.of("PROTECTED"))));
        when(authorities.calculate("kim")).thenReturn(new EffectiveAuthority(Set.of(new MenuAction("READ_ONLY",ActionType.READ)),LocalDate.of(2026,11,14)));
        var prior=context(false);
        var command=new Context(prior.domain(),prior.principal(),null,prior.identity(),operation,entry.action(),false,Capability.READ,"test");
        assertThatThrownBy(()->tx.executeWithoutResult(s->guard.protect(command,false,()->jdbc.update("INSERT INTO EFFECT VALUES(1)")))).isInstanceOf(Rejected.class);
        noEffect();
    }
    @Test void refreshedEntityAndCurrentAuthorityRemainInsideBusinessTransaction() {
        when(entities.contains(user)).thenReturn(true);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> guard.protect(context(false), false, () -> {
            assertThat(AdminRequestContext.authority()).isEqualTo(allowed);
            jdbc.update("INSERT INTO EFFECT VALUES(1)"); throw new IllegalArgumentException("시험 롤백");
        }))).isInstanceOf(IllegalArgumentException.class);
        verify(entities).refresh(user); noEffect();
    }
    @Test void sharedCannotUpgradeAndNoTransactionCannotWrite() {
        assertThatThrownBy(() -> guard.protect(context(true),false,() -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> guard.protect(context(true),false,
                () -> guard.protect(context(true),true,() -> jdbc.update("INSERT INTO EFFECT VALUES(1)"))))).isInstanceOf(IllegalStateException.class);
        noEffect();
    }
}
