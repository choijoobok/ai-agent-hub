// @feat FEAT-301
package com.aiagent.api.auth.v2;

import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** v1과 분리한 module-first 카탈로그. 실제 제품 경로 활성화는 SLICE-520 소유다. */
public final class ModuleApiCatalog {
    private static final String PROGRESS="/api/system/common/progress/getProgress/{jobId}";
    public enum Domain { ADMIN, PORTAL, NONE }
    @FunctionalInterface public interface SourceVerifier { void verify(HttpServletRequest request)throws Exception; }
    @FunctionalInterface public interface JobOwner { boolean owns(PrincipalRef principal,String jobId); }
    public record Operation(String id,String method,String path,Domain domain,Set<AuthLevel> levels,
                            String requiredAction,boolean selfScope,Capability capability,SourceVerifier sourceVerifier,JobOwner jobOwner,boolean exclusive) {
        public Operation {levels=levels==null?null:Set.copyOf(levels);}
        public Operation(String id,String method,String path,Domain domain,Set<AuthLevel> levels,String requiredAction,boolean selfScope,Capability capability,SourceVerifier sourceVerifier,JobOwner jobOwner){
            this(id,method,path,domain,levels,requiredAction,selfScope,capability,sourceVerifier,jobOwner,Set.of(Capability.PREPARE_CSRF,Capability.LOGIN,Capability.RECOVERY,Capability.PASSWORD_CHANGE,Capability.RESET).contains(capability));
        }
    }
    public record Match(Operation operation,String jobId) {
        public boolean ownsJob(PrincipalRef principal){
            return operation.jobOwner()!=null && principal!=null && principal.domain()==com.aiagent.core.security.AuthDomain.ADMIN
                    && jobId!=null && jobId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    && operation.jobOwner().owns(principal,jobId);
        }
    }
    private final Map<String,Operation> exact;
    private final Operation progress;
    public ModuleApiCatalog(Collection<Operation> entries,boolean local){
        var routes=new HashMap<String,Operation>();var ids=new java.util.HashSet<String>();Operation dynamic=null;
        for(var op:entries){
            validate(op,local);
            if(!ids.add(op.id()) || routes.putIfAbsent(op.method()+" "+op.path(),op)!=null)
                throw new IllegalArgumentException("정확 API 등록이 중복됐다");
            if(op.jobOwner()!=null)dynamic=op;
        }
        exact=Map.copyOf(routes);progress=dynamic;
    }
    private static void validate(Operation op,boolean local){
        if(op==null || op.id()==null || op.id().isBlank() || op.domain()==null || op.capability()==null || op.levels()==null
                || !Set.of("GET","POST","PUT","PATCH","DELETE").contains(op.method()) || op.path()==null
                || !op.path().matches("/api/[A-Za-z0-9/{}_-]+") || op.path().contains("//") || op.path().endsWith("/"))
            throw new IllegalArgumentException("정확 API 등록 정보가 올바르지 않다");
        String[] parts=op.path().split("/");
        if(parts.length<5 || !Set.of("public","common","service").contains(parts[3]))throw new IllegalArgumentException("모듈 우선 API 계층이 필요하다");
        String module=parts[2],layer=parts[3];
        Domain expected=switch(module){case "system","admin","sample"->Domain.ADMIN;case "portal","agent"->Domain.PORTAL;case "integration"->Domain.NONE;default->null;};
        if(expected!=op.domain() || module.equals("sample") && (!local || parts.length!=5 || !layer.equals("service"))
                || !module.equals("sample") && parts.length<6 || layer.equals("service") && !op.method().equals("POST"))
            throw new IllegalArgumentException("API 소유 모듈·영역·메서드가 다르다");
        if(op.domain()==Domain.NONE){
            if(!layer.equals("public") || !op.method().equals("POST") || op.sourceVerifier()==null || !op.levels().isEmpty()
                    || op.selfScope() || op.requiredAction()!=null || op.jobOwner()!=null || op.capability()!=Capability.READ)
                throw new IllegalArgumentException("원천 전용 API는 NONE 검증 어댑터가 필요하다");
        }else if(op.levels().isEmpty() || op.sourceVerifier()!=null || op.selfScope()==(op.requiredAction()!=null)
                || !op.selfScope() && op.requiredAction().isBlank() || layer.equals("service") && op.selfScope())
            throw new IllegalArgumentException("인증 수준·액션 또는 본인 범위가 필요하다");
        boolean template=op.path().contains("{")||op.path().contains("}");
        if(op.path().startsWith(PROGRESS.substring(0,PROGRESS.indexOf('{'))) && !op.path().equals(PROGRESS))
            throw new IllegalArgumentException("진행률 템플릿을 literal 경로로 가릴 수 없다");
        if(template && (!op.path().equals(PROGRESS) || !op.method().equals("GET") || op.jobOwner()==null || !op.selfScope()
                || !op.levels().equals(Set.of(AuthLevel.AUTHENTICATED))) || !template && op.jobOwner()!=null)
            throw new IllegalArgumentException("등록된 단일 진행률 jobId 템플릿만 허용한다");
        if(op.capability()==Capability.PREPARE_CSRF && (!op.method().equals("GET") || !Set.of("/api/system/public/auth/getCsrf","/api/portal/public/auth/getCsrf").contains(op.path()) || !op.selfScope()))
            throw new IllegalArgumentException("정확 CSRF 준비 진입부만 익명 문맥을 생성한다");
        if(Set.of(Capability.PREPARE_CSRF,Capability.LOGIN,Capability.RECOVERY,Capability.PASSWORD_CHANGE,Capability.RESET).contains(op.capability())&&!op.exclusive())throw new IllegalArgumentException("인증 변경은 진입부터 배타 보호가 필요하다");
        if(op.domain()!=Domain.NONE&&layer.equals("public")&&op.capability()==Capability.READ&&!op.levels().equals(Set.of(AuthLevel.values())))throw new IllegalArgumentException("공개 읽기는 익명·제한·일반 수준을 모두 명시해야 한다");
    }
    public Match resolve(String method,String path){
        if(path==null || !path.matches("/api/[A-Za-z0-9/_-]+") || path.contains("//") || path.endsWith("/"))return null;
        var found=exact.get(method+" "+path);if(found!=null)return new Match(found,null);
        String prefix=PROGRESS.substring(0,PROGRESS.indexOf('{'));
        if(progress!=null && method.equals("GET") && path.startsWith(prefix)){
            String id=path.substring(prefix.length());if(!id.isEmpty()&&!id.contains("/"))return new Match(progress,id);
        }
        return null;
    }
    public Collection<Operation> entries(){return exact.values();}
    public record Handler(String method,String template,String identity) {}
    public void verifyHandlers(Map<String,String> expectedByOperation,Collection<Handler> observed){
        if(expectedByOperation.size()!=exact.size()||observed.size()!=exact.size())throw new IllegalArgumentException("정확 API와 관측 handler 개수가 다르다");
        Map<String,Handler> registrations=new HashMap<>();
        for(Handler handler:observed)if(handler==null||handler.identity()==null||handler.identity().isBlank()||registrations.putIfAbsent(handler.method()+" "+handler.template(),handler)!=null)throw new IllegalArgumentException("관측 handler 식별 또는 등록이 중복됐다");
        for(Operation op:exact.values()){
            Handler handler=registrations.get(op.method()+" "+op.path());String expected=expectedByOperation.get(op.id());
            if(expected==null||expected.isBlank()||handler==null||!handler.identity().equals(expected))throw new IllegalArgumentException("정확 API의 실제 handler/method/template이 다르다: " + op.id() + " " + op.method() + " " + op.path() + " 관측=" + handler);
        }
    }
}
