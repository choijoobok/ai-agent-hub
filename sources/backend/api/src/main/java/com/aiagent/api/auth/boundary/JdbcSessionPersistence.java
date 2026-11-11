// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import static com.aiagent.core.security.SessionPersistencePort.*;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.RequestPersistenceState;
import com.aiagent.core.security.SessionAdmissions;
import com.aiagent.core.security.SessionPersistencePort;
import com.aiagent.core.security.SessionRef;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.SaveMode;
import org.springframework.session.Session;
import org.springframework.session.MapSession;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring Session 표준 JDBC 저장소. 부모 행 보호 안에서만 기존 세대·속성을 변경한다. */
public final class JdbcSessionPersistence implements SessionPersistencePort {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";
    private static final String PORTAL_CONTEXT = "PORTAL_SECURITY_CONTEXT";
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EnumMap<AuthDomain, FindByIndexNameSessionRepository<Session>> repositories = new EnumMap<>(AuthDomain.class);
    private final boolean postgres;

    public JdbcSessionPersistence(DataSource dataSource, PlatformTransactionManager manager) {
        this.dataSource = Objects.requireNonNull(dataSource);
        DataSource transactionSource = switch (manager) {
            case DataSourceTransactionManager m -> m.getDataSource();
            case JpaTransactionManager m -> m.getDataSource();
            default -> throw new IllegalArgumentException("같은 DB의 JDBC/JPA 트랜잭션 관리자만 허용한다");
        };
        if (transactionSource != dataSource) throw new IllegalArgumentException("세션과 업무 트랜잭션의 데이터소스가 다르다");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(manager); // REQUIRED: 호출자의 자격·업무 변경과 같은 물리 커밋.
        postgres = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
        for (AuthDomain domain : AuthDomain.values()) repositories.put(domain, repository(domain));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private FindByIndexNameSessionRepository<Session> repository(AuthDomain domain) {
        var repository = new JdbcIndexedSessionRepository(jdbc, transactions);
        repository.setTableName(table(domain));
        repository.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
        repository.setCleanupCron("-");
        repository.setIndexResolver(session -> {
            Identity identity = identity(domain, session);
            return identity == null ? Map.of() : Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, identity.principal().indexKey());
        });
        return (FindByIndexNameSessionRepository) repository;
    }

    static String table(AuthDomain domain) { return domain == AuthDomain.ADMIN ? "SPRING_SESSION" : "AI_PORTAL_SESSION"; }
    static String csrfKey(AuthDomain domain) { return domain == AuthDomain.ADMIN ? "ADMIN_CSRF" : "PORTAL_CSRF"; }
    TransactionTemplate transactions() { return transactions; }
    boolean boundTo(DataSource source,PlatformTransactionManager manager) {
        return dataSource==source && transactions.getTransactionManager()==manager
                && transactions.getPropagationBehavior()==org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRED;
    }
    public <T>T inTransaction(RequestPersistenceState request,Supplier<T> command) {
        return transactions.execute(status->{attachAttempt(request);return command.get();});
    }
    void inRequired(RequestPersistenceState request,AuthBoundaryAssembly.Command command)throws Exception {
        try {
            inTransaction(request,()->{
                try{command.run();}catch(RuntimeException failure){throw failure;}
                catch(Exception failure){throw new CheckedCommandFailure(failure);}
                return null;
            });
        } catch(CheckedCommandFailure failure){throw (Exception)failure.getCause();}
    }
    private static final class CheckedCommandFailure extends RuntimeException {
        CheckedCommandFailure(Exception cause){super("인증 명령 실행에 실패했다",cause);}
    }
    Session rawSession(AuthDomain domain, String id) {
        var rows = jdbc.query("SELECT CREATION_TIME,LAST_ACCESS_TIME,MAX_INACTIVE_INTERVAL,EXPIRY_TIME FROM "
                + table(domain)+" WHERE SESSION_ID=?",(rs,i)-> {
            var session = new DatabaseSession(id,Instant.ofEpochMilli(rs.getLong(4)));
            session.setCreationTime(Instant.ofEpochMilli(rs.getLong(1)));
            session.setLastAccessedTime(Instant.ofEpochMilli(rs.getLong(2)));
            session.setMaxInactiveInterval(Duration.ofSeconds(rs.getInt(3)));
            return session;
        },id);
        if (rows.isEmpty()) return null;
        var session = rows.getFirst();
        jdbc.query("SELECT A.ATTRIBUTE_NAME,A.ATTRIBUTE_BYTES FROM "+table(domain)+"_ATTRIBUTES A JOIN "
                +table(domain)+" S ON S.PRIMARY_ID=A.SESSION_PRIMARY_ID WHERE S.SESSION_ID=?", rs -> {
            session.setAttribute(rs.getString(1),org.springframework.util.SerializationUtils.deserialize(rs.getBytes(2)));
        },id);
        return session;
    }
    private final class DatabaseSession implements Session {
        private final MapSession delegate;
        private final Instant databaseExpiry;
        DatabaseSession(String id, Instant databaseExpiry) { delegate=new MapSession(id);this.databaseExpiry=databaseExpiry; }
        void setCreationTime(Instant time) { delegate.setCreationTime(time); }
        @Override public String getId() { return delegate.getId(); }
        @Override public String changeSessionId() { return delegate.changeSessionId(); }
        @Override public <T>T getAttribute(String name) { return delegate.getAttribute(name); }
        @Override public java.util.Set<String> getAttributeNames() { return delegate.getAttributeNames(); }
        @Override public void setAttribute(String name,Object value) { delegate.setAttribute(name,value); }
        @Override public void removeAttribute(String name) { delegate.removeAttribute(name); }
        @Override public Instant getCreationTime() { return delegate.getCreationTime(); }
        @Override public Instant getLastAccessedTime() { return delegate.getLastAccessedTime(); }
        @Override public void setLastAccessedTime(Instant time) { delegate.setLastAccessedTime(time); }
        @Override public Duration getMaxInactiveInterval() { return delegate.getMaxInactiveInterval(); }
        @Override public void setMaxInactiveInterval(Duration interval) { delegate.setMaxInactiveInterval(interval); }
        @Override public boolean isExpired() { return !now().isBefore(databaseExpiry); }
    }
    boolean csrfMatches(SessionRef ref, String value) {
        if (value==null) return false;
        Boolean valid = transactions.execute(status -> {
            Session session=locked(ref);
            String expected=session==null?null:session.getAttribute(csrfKey(ref.domain()));
            return expected!=null && java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        return Boolean.TRUE.equals(valid);
    }

    Instant databaseNow() { return now(); }
    private Instant now() {
        return jdbc.queryForObject(postgres ? "SELECT clock_timestamp()" : "SELECT CURRENT_TIMESTAMP",
                (rs, row) -> rs.getTimestamp(1).toInstant());
    }
    private record Row(String primaryId, String sessionId, Instant expiry) {}
    private Row row(AuthDomain domain, String id, boolean lock) {
        var rows = jdbc.query("SELECT PRIMARY_ID,SESSION_ID,EXPIRY_TIME FROM " + table(domain)
                        + " WHERE SESSION_ID=?" + (lock ? " FOR UPDATE" : ""),
                (rs, i) -> new Row(rs.getString(1),rs.getString(2),Instant.ofEpochMilli(rs.getLong(3))), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    private Session locked(SessionRef ref) {
        Row row = row(ref.domain(), ref.sessionId(), true);
        if (row == null || !row.primaryId.equals(ref.primaryId()) || !now().isBefore(row.expiry)) return null;
        return rawSession(ref.domain(), ref.sessionId());
    }
    private Result snapshot(AuthDomain domain, Session session, Outcome outcome, RequestPersistenceState request) {
        Row row = row(domain, session.getId(), false);
        if (row == null) return Result.of(Outcome.STALE);
        var ref = new SessionRef(domain,row.primaryId,row.sessionId);
        String csrf = session.getAttribute(csrfKey(domain));
        return new Result(outcome,ref,identity(domain,session),row.expiry,
                request == null || csrf == null ? null : new CsrfIssue(request,session.getId(),csrf));
    }
    @Override public Result load(AuthDomain domain, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Result.of(Outcome.ABSENT);
        try {
            return transactions.execute(status -> {
                Row row = row(domain,sessionId,false);
                if (row == null) return Result.of(Outcome.ABSENT);
                if (!now().isBefore(row.expiry)) return Result.of(Outcome.EXPIRED);
                Session session = rawSession(domain,sessionId);
                return session == null ? Result.of(Outcome.ABSENT) : snapshot(domain,session,Outcome.CURRENT,null);
            });
        } catch (DataAccessException | TransactionException e) { return Result.of(Outcome.UNAVAILABLE); }
        catch (IllegalArgumentException e) { return Result.of(Outcome.REJECTED); }
    }
    void attachAttempt(RequestPersistenceState request) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource))
            throw new IllegalStateException("세션 저장은 같은 DB 트랜잭션에 참여해야 한다");
        if(!request.beginAttempt(TransactionSynchronizationManager.getResource(dataSource)))return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                request.completeAttempt(status == STATUS_COMMITTED ? RequestPersistenceState.Completion.COMMITTED
                        : status == STATUS_ROLLED_BACK ? RequestPersistenceState.Completion.ROLLED_BACK
                        : RequestPersistenceState.Completion.UNKNOWN);
            }
        });
    }
    private void begin(RequestPersistenceState request, boolean close) { request.beginMutation(close); }
    private Result mutate(RequestPersistenceState request, Supplier<Result> action) {
        if (!request.canFlush()) return Result.of(Outcome.REJECTED);
        try { return inTransaction(request,action); }
        catch (DataAccessException | TransactionException e) { return Result.of(Outcome.UNAVAILABLE); }
    }
    private boolean matches(RequestPersistenceState request, SessionRef ref) { return ref != null && request.domain() == ref.domain(); }
    private static String randomSecret() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    @Override public Result prepareCsrf(RequestPersistenceState request, SessionRef ref) {
        if (!matches(request,ref) || request.capability()!=RequestPersistenceState.Capability.PREPARE_CSRF) return Result.of(Outcome.REJECTED);
        return mutate(request,() -> {
            Session session = locked(ref);
            if (session == null) return Result.of(Outcome.STALE);
            begin(request,false);
            if (session.getAttribute(csrfKey(ref.domain())) == null) {
                session.setAttribute(csrfKey(ref.domain()),randomSecret());
                saveExisting(ref,session);
            }
            return snapshot(ref.domain(),session,Outcome.PREPARED,request);
        });
    }
    @Override public Result createAnonymous(RequestPersistenceState request, SessionAdmissions.AnonymousSessionCreation creation) {
        return mutate(request,() -> {
            if (!creation.consume(request,now())) return Result.of(Outcome.REJECTED);
            begin(request,false);
            return create(request,null,creation.sessionExpiresAt(),Outcome.CREATED);
        });
    }
    @Override public Result createAuthorized(RequestPersistenceState request, SessionAdmissions.CredentialAdmission admission, Instant expiresAt) {
        return mutate(request,() -> {
            if (!admission.consume(request,now()) || !now().isBefore(expiresAt)) return Result.of(Outcome.REJECTED);
            if (admission.entry() != null && locked(admission.entry()) == null) return Result.of(Outcome.STALE);
            begin(request,false);
            if (admission.entry() != null) repositories.get(request.domain()).deleteById(admission.entry().sessionId());
            return create(request,admission.identity(),expiresAt,Outcome.CREATED);
        });
    }
    @Override public Result rotate(RequestPersistenceState request, SessionRef ref, SessionAdmissions.RotationAdmission admission, Instant expiresAt) {
        if (!matches(request,ref) || !ref.equals(admission.expected())) return Result.of(Outcome.REJECTED);
        return mutate(request,() -> {
            if (!admission.consume(request,now()) || !now().isBefore(expiresAt)) return Result.of(Outcome.REJECTED);
            Session old = locked(ref);
            if (old == null) return Result.of(Outcome.STALE);
            Identity oldIdentity = identity(ref.domain(),old);
            if (oldIdentity == null || !oldIdentity.principal().equals(admission.identity().principal())) return Result.of(Outcome.REJECTED);
            begin(request,false);
            repositories.get(ref.domain()).deleteById(ref.sessionId());
            return create(request,admission.identity(),expiresAt,Outcome.ROTATED);
        });
    }
    private Result create(RequestPersistenceState request, Identity identity, Instant expiresAt, Outcome outcome) {
        var repository = repositories.get(request.domain());
        Session session = repository.createSession();
        Instant now = now();
        session.setLastAccessedTime(now);
        session.setMaxInactiveInterval(Duration.between(now,expiresAt));
        session.setAttribute(csrfKey(request.domain()),randomSecret());
        if (identity != null) writeIdentity(request.domain(),session,identity);
        if (request.domain() == AuthDomain.PORTAL) session.setAttribute("schemaVersion",1);
        repository.save(session);
        Session persisted=rawSession(request.domain(),session.getId());
        if(persisted==null || !Objects.equals(session.getAttribute(csrfKey(request.domain())),persisted.getAttribute(csrfKey(request.domain())))
                || !Objects.equals(identity,identity(request.domain(),persisted)))
            throw new org.springframework.dao.DataIntegrityViolationException("세션 필수 속성을 저장하지 못했다");
        jdbc.update("UPDATE "+table(request.domain())+" SET CREATION_TIME=?,LAST_ACCESS_TIME=?,EXPIRY_TIME=? WHERE SESSION_ID=?",
                now.toEpochMilli(),now.toEpochMilli(),expiresAt.toEpochMilli(),session.getId());
        return snapshot(request.domain(),session,outcome,request);
    }
    @Override public Result updateExisting(RequestPersistenceState request, SessionRef ref, SessionDelta delta) {
        if (!matches(request,ref)) return Result.of(Outcome.REJECTED);
        return mutate(request,() -> {
            Session session = locked(ref);
            if (session == null) return Result.of(Outcome.STALE);
            begin(request,false);
            session.setLastAccessedTime(now()); // 클라이언트/다른 노드의 시계로 만료를 늘리지 않는다.
            Identity identity = identity(ref.domain(),session);
            if (identity != null) writeIdentity(ref.domain(),session,new Identity(identity.principal(),identity.authLevel(),identity.authMode(),
                    identity.credentialVersion(),identity.authenticatedAt(),delta.displayName(),delta.locale()));
            if (ref.domain() == AuthDomain.ADMIN) {
                session.setAttribute("ADMIN_AUTHORITY_CACHE",new java.util.HashSet<>(delta.authoritySnapshot()));
                session.setAttribute("ADMIN_DIRTY_SEEN",delta.dirtySeen());
            }
            saveExisting(ref,session);
            return snapshot(ref.domain(),session,Outcome.APPLIED,request);
        });
    }
    @Override public Result delete(RequestPersistenceState request, SessionRef ref) {
        if (!matches(request,ref)) return Result.of(Outcome.REJECTED);
        return mutate(request,() -> {
            Session session = locked(ref);
            if (session == null) return Result.of(Outcome.STALE);
            begin(request,true);
            repositories.get(ref.domain()).deleteById(ref.sessionId());
            return new Result(Outcome.DELETED,ref,null,null,new CsrfIssue(request,"",""));
        });
    }
    @Override public List<SessionRef> findByPrincipal(PrincipalRef principal) {
        return transactions.execute(status -> jdbc.query("SELECT PRIMARY_ID,SESSION_ID FROM "+table(principal.domain())
                +" WHERE PRINCIPAL_NAME=? AND EXPIRY_TIME>?",(rs,i)->new SessionRef(principal.domain(),rs.getString(1),rs.getString(2)),
                principal.indexKey(),now().toEpochMilli()));
    }
    private void saveExisting(SessionRef ref, Session session) {
        // 호출자는 부모 행을 잠근 상태다. 갱신0건을 INSERT로 바꾸지 않는다.
        int changed = jdbc.update("UPDATE "+table(ref.domain())+" SET LAST_ACCESS_TIME=?,MAX_INACTIVE_INTERVAL=?,EXPIRY_TIME=?"
                        +" WHERE PRIMARY_ID=? AND SESSION_ID=? AND EXPIRY_TIME>?",session.getLastAccessedTime().toEpochMilli(),
                session.getMaxInactiveInterval().toSeconds(),session.getLastAccessedTime().plus(session.getMaxInactiveInterval()).toEpochMilli(),
                ref.primaryId(),ref.sessionId(),now().toEpochMilli());
        if (changed!=1) throw new IllegalStateException("세션의 현재 세대가 바뀌었다");
        for (String key:session.getAttributeNames()) {
            byte[] bytes=org.springframework.util.SerializationUtils.serialize(session.getAttribute(key));
            int updated=jdbc.update("UPDATE "+table(ref.domain())+"_ATTRIBUTES SET ATTRIBUTE_BYTES=? WHERE SESSION_PRIMARY_ID=? AND ATTRIBUTE_NAME=?",
                    bytes,ref.primaryId(),key);
            if(updated==0)jdbc.update("INSERT INTO "+table(ref.domain())+"_ATTRIBUTES (SESSION_PRIMARY_ID,ATTRIBUTE_NAME,ATTRIBUTE_BYTES) VALUES (?,?,?)",ref.primaryId(),key,bytes);
        }
    }
    static Identity identity(AuthDomain domain, Session session) {
        Object stored = session.getAttribute(domain == AuthDomain.ADMIN ? SECURITY_CONTEXT : PORTAL_CONTEXT);
        if (domain == AuthDomain.PORTAL) return stored instanceof Identity identity ? identity : null;
        return stored instanceof SecurityContext context && context.getAuthentication() != null
                && context.getAuthentication().getDetails() instanceof Identity identity ? identity : null;
    }
    private static void writeIdentity(AuthDomain domain, Session session, Identity identity) {
        if (domain == AuthDomain.PORTAL) {
            session.setAttribute(PORTAL_CONTEXT,identity);
            if (identity.credentialVersion() != null) session.setAttribute("PORTAL_CREDENTIAL_VERSION",identity.credentialVersion());
        } else {
            var authentication = UsernamePasswordAuthenticationToken.authenticated(identity.principal().subjectId(),null,List.of());
            authentication.setDetails(identity);
            session.setAttribute(SECURITY_CONTEXT,new SecurityContextImpl(authentication));
            session.setAttribute("BACKOFFICE_PWD_CHANGE_REQUIRED",identity.authLevel() == AuthLevel.PASSWORD_CHANGE_REQUIRED);
        }
    }
}
