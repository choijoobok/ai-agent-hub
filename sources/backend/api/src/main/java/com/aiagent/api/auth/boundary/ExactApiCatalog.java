// @feat FEAT-301
package com.aiagent.api.auth.boundary;

import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import com.aiagent.core.security.SessionAdmissions;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** method/path는 서버가 등록한다. 넓은 public 예외·브라우저의 영역/액션 입력은 없다. */
public final class ExactApiCatalog {
    public record Operation(String id, String method, String path, AuthDomain domain, Set<AuthLevel> levels,
                            String requiredAction, boolean selfScope, Capability capability,
                            SessionAdmissions.Producer producer) {
        public Operation {
            if (id == null || id.isBlank() || domain == null || levels == null || levels.isEmpty()
                    || method == null || !Set.of("GET","POST","PUT","PATCH","DELETE").contains(method)
                    || path == null || !path.matches("/api/[A-Za-z0-9/_-]+") || path.contains("//") || path.endsWith("/") || path.split("/").length<5
                    || selfScope == (requiredAction != null) || !selfScope && requiredAction.isBlank() || capability == null)
                throw new IllegalArgumentException("정확 API의 영역·인증 수준·액션/본인 범위가 필요하다");
            levels = Set.copyOf(levels);
            String namespace=path.split("/")[3];
            if(!Set.of("public","common","search","service").contains(path.split("/")[2]))
                throw new IllegalArgumentException("등록되지 않은 API 계층이다");
            if (!(domain==AuthDomain.ADMIN ? Set.of("system","admin","sample") : Set.of("portal","agent")).contains(namespace))
                throw new IllegalArgumentException("API 소유 모듈과 인증 영역이 다르다");
            if (capability == Capability.PREPARE_CSRF && (!"GET".equals(method) || !path.endsWith("/getCsrf") || !selfScope))
                throw new IllegalArgumentException("익명 생성은 정확 CSRF 준비 API에만 허용한다");
            if (Set.of(Capability.LOGIN,Capability.RECOVERY,Capability.PASSWORD_CHANGE,Capability.RESET).contains(capability)
                    && (producer == null || producer.domain() != domain))
                throw new IllegalArgumentException("자격 검증 생산자가 등록되지 않았다");
        }
        public boolean anonymous() { return levels.contains(AuthLevel.ANONYMOUS); }
        public boolean prepareCsrf() { return capability == Capability.PREPARE_CSRF; }
    }
    private final Map<String,Operation> operations;
    public ExactApiCatalog(Collection<Operation> entries) {
        var map = new HashMap<String,Operation>();
        var ids = new java.util.HashSet<String>();
        for (Operation operation : entries)
            if (map.putIfAbsent(operation.method()+" "+operation.path(),operation) != null || !ids.add(operation.id()))
                throw new IllegalArgumentException("정확 API 등록이 중복됐다");
        operations = Map.copyOf(map);
    }
    public Operation find(String method, String path) { return operations.get(method+" "+path); }
    public Collection<Operation> entries() { return operations.values(); }
}
