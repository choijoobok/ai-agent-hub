// @feat FEAT-301
package com.aiagent.portal.auth;

import com.aiagent.api.auth.boundary.AuthBoundaryAssembly.Rejected;
import com.aiagent.api.auth.v2.*;
import com.aiagent.core.security.*;
import com.aiagent.core.security.SessionPersistencePort.*;
import com.aiagent.core.security.portal.PortalAccounts;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 외부 검증 결과와 브라우저 challenge의 단회 수락. 고객 콜백은 별도 소유 어댑터에서 연결한다. */
@Service
public class PortalExternalAuthService {
    public record Challenge(String id,String state,String nonce,String browserBinding,String returnPath,Instant expiresAt) {
        @Override public String toString(){return "PortalChallenge[보호됨]";}
    }
    public static final class VerifiedLogin {
        private final PortalExternalAuthService owner;private final ExternalAuthProvider provider;private final ExternalAuthProvider.Proof proof;
        private VerifiedLogin(PortalExternalAuthService owner,ExternalAuthProvider provider,ExternalAuthProvider.Proof proof){this.owner=owner;this.provider=provider;this.proof=proof;}
        @Override public String toString(){return "VerifiedPortalLogin[보호됨]";}
    }
    private final JdbcTemplate jdbc;private final PortalAccounts accounts;private final PortalAuthModule module;private final PortalAuthService auth;private final PortalAuthSettings settings;private final PortalAuthEventService events;private final Map<String,ExternalAuthProvider> providers;
    private final SecureRandom random=new SecureRandom();
    public PortalExternalAuthService(DataSource source,PortalAccounts accounts,PortalAuthModule module,PortalAuthService auth,PortalAuthSettings settings,PortalAuthEventService events,List<ExternalAuthProvider> installed,Environment environment) {
        jdbc=new JdbcTemplate(source);this.accounts=accounts;this.module=module;this.auth=auth;this.settings=settings;this.events=events;
        var values=new HashMap<String,ExternalAuthProvider>();
        for(var provider:installed) {
            if(provider.sourceKey()==null||!provider.sourceKey().matches("[A-Za-z0-9_-]{1,100}")||provider.issuer()==null||provider.issuer().isBlank()||provider.audience()==null||provider.audience().isBlank()||values.putIfAbsent(provider.sourceKey(),provider)!=null
                ||provider.testOnly()&&!Arrays.asList(environment.getActiveProfiles()).contains("portal-auth-test"))throw new IllegalStateException("외부 인증 검증기 등록이 올바르지 않다");
        }
        providers=Map.copyOf(values);
        if(environment.getProperty("app.auth.session.domains.portal.enabled",Boolean.class,false)&&settings.mode().equals("EXTERNAL")&&providers.isEmpty())throw new IllegalStateException("EXTERNAL 인증은 설치된 검증기가 필요하다");
    }
    private String nonce(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private static String digest(String... values) {
        try {var digest=MessageDigest.getInstance("SHA-256");for(String value:values){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}
        catch(NoSuchAlgorithmException failure){throw new IllegalStateException("SHA-256을 사용할 수 없다",failure);}
    }
    public static String safeReturnPath(String value) {
        if(value==null||value.length()>500||value.contains("%")||value.contains("\\")||value.contains("?")||value.contains("#")||value.contains("//")||value.contains("..")||value.matches(".*[\\r\\n\\t].*"))return "/";
        if(value.equals("/")||value.matches("/portal(?:/[A-Za-z0-9_-]+)*/?")&&!value.matches("/portal/(login|pwd-change|password-reset|session-expire)(/.*)?"))return value;
        return "/";
    }
    private ExternalAuthProvider provider(String source){var provider=providers.get(source);if(!settings.mode().equals("EXTERNAL")||provider==null)throw new Rejected(401);return provider;}
    @Transactional
    public Challenge begin(SessionRuntime.Scope browser,String source,String returnPath) {
        provider(source);var current=browser.current().orElseThrow(()->new Rejected(401));
        if(browser.exchange().domain()!=AuthDomain.PORTAL||current.identity()!=null)throw new Rejected(401);
        Instant now=events.now();if(!now.isBefore(current.expiresAt()))throw new Rejected(401);
        var challenge=new Challenge(UUID.randomUUID().toString(),nonce(),nonce(),nonce(),safeReturnPath(returnPath),now.plus(settings.challengeTimeout()));
        jdbc.update("INSERT INTO AI_PORTAL_LOGIN_CHALLENGE VALUES (?,?,?,?,?,?,?,?,NULL)",challenge.id(),source,digest(challenge.state()),binding(challenge.browserBinding(),current.ref()),digest(challenge.nonce()),challenge.returnPath(),Timestamp.from(now),Timestamp.from(challenge.expiresAt()));return challenge;
    }
    private static String binding(String browser,com.aiagent.core.security.v2.SessionV2.Ref ref){return digest(browser,ref.domain().name(),Long.toString(ref.grantId()),Long.toString(ref.generation()),ref.epoch().toString());}
    public VerifiedLogin verify(String source,Object protocolEvidence) {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("외부 검증은 DB 보호 밖에서 수행해야 한다");
        var provider=provider(source);var proof=provider.verify(protocolEvidence);
        if(proof==null||proof.identity()==null||!source.equals(proof.identity().sourceKey())||!provider.issuer().equals(proof.issuer())||!provider.audience().equals(proof.audience())||proof.assertionId()==null||proof.assertionId().isBlank()||proof.assertionId().length()>4096||proof.nonce()==null||proof.nonce().length()>1024||proof.verifiedAt()==null||proof.expiresAt()==null)throw new Rejected(401);
        Instant now=events.now();if(proof.verifiedAt().isAfter(now)||!now.isBefore(proof.expiresAt()))throw new Rejected(401);
        return new VerifiedLogin(this,provider,proof);
    }
    /** challenge → Portal 정책 → 사용자 → 수락 원장 순서로 확정한다. 호출자는 반환 뒤 finish와 쿠키 전달을 수행한다. */
    @Transactional
    public PortalAuthService.State accept(SessionRuntime runtime,SessionRuntime.Scope browser,String state,String browserBinding,VerifiedLogin verified) {
        if(verified==null||verified.owner!=this||providers.get(verified.provider.sourceKey())!=verified.provider||!settings.mode().equals("EXTERNAL")||state==null||browserBinding==null||state.length()>1024||browserBinding.length()>1024)throw new Rejected(401);
        var current=browser.current().orElseThrow(()->new Rejected(401));if(current.identity()!=null||browser.exchange().domain()!=AuthDomain.PORTAL||browser.exchange().capability()!=RequestPersistenceState.Capability.LOGIN)throw new Rejected(401);
        var rows=jdbc.queryForList("SELECT * FROM AI_PORTAL_LOGIN_CHALLENGE WHERE STATE_DIGEST=? FOR UPDATE",digest(state));if(rows.size()!=1)throw new Rejected(401);
        var challenge=rows.getFirst();var proof=verified.proof;Instant now=events.now();
        if(challenge.get("CONSUMED_DTM")!=null)throw new com.aiagent.core.error.BusinessException(PortalAuthErrorCode.EVIDENCE_USED);
        if(!verified.provider.sourceKey().equals(challenge.get("SOURCE_KEY"))||!binding(browserBinding,current.ref()).equals(challenge.get("BROWSER_BIND_DIGEST"))||!digest(proof.nonce()).equals(challenge.get("NONCE_DIGEST"))||!now.isBefore(((Timestamp)challenge.get("EXPIRES_DTM")).toInstant())||!now.isBefore(proof.expiresAt()))throw new Rejected(401);
        return runtime.execute(browser,()->{
            var account=accounts.resolveExternal(proof.identity());Instant expires=((Timestamp)challenge.get("EXPIRES_DTM")).toInstant();if(proof.expiresAt().isBefore(expires))expires=proof.expiresAt();
            String evidence=digest(proof.identity().sourceKey(),proof.issuer(),proof.assertionId(),proof.nonce());
            int inserted=jdbc.update("INSERT INTO AI_PORTAL_AUTH_EVIDENCE VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",proof.identity().sourceKey(),evidence,challenge.get("CHALLENGE_ID"),Long.valueOf(account.principal().subjectId()),Timestamp.from(now),Timestamp.from(expires));if(inserted!=1)throw new com.aiagent.core.error.BusinessException(PortalAuthErrorCode.EVIDENCE_USED);
            jdbc.update("UPDATE AI_PORTAL_LOGIN_CHALLENGE SET CONSUMED_DTM=? WHERE CHALLENGE_ID=?",Timestamp.from(now),challenge.get("CHALLENGE_ID"));
            events.record("LOGIN_SUCCESS",account.principal(),"EXTERNAL_ACCEPTED",now);
            var identity=new Identity(account.principal(),AuthLevel.AUTHENTICATED,"EXTERNAL",null,now,account.displayName(),"ko");
            browser.publish(module.producer().login(browser.exchange(),current.ref(),identity,expires),auth.slots());return auth.state(browser);
        });
    }
}
