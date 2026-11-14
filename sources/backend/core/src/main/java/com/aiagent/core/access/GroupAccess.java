// @feat FEAT-303
package com.aiagent.core.access;

import com.aiagent.core.security.*;
import java.time.Instant;
import java.util.*;

/** 순수 현재 그룹 판정. 이 결과 자체는 HTTP 인가 토큰이나 새 실행 허가가 아니다. */
public final class GroupAccess {
    public enum Purpose { SEARCH, MODEL_INPUT, QUERY, EXECUTE, RESULT }
    public enum Status { ALLOW, DENY, UNAVAILABLE }
    public enum Mode { RAW, MASKED, DENIED }
    public enum Control { QUOTA, IDENTITY, ROW_SCOPE, CONSENT, EXECUTION_APPROVAL }
    public enum Check { PASSED, DENIED, UNAVAILABLE }
    public enum ValueType { STRING, NUMBER, BOOLEAN }
    public record Target(String kind,String id,String contentRevision) {
        public Target {text(kind);text(id);text(contentRevision);}
    }
    public record Request(PrincipalRef principal,Purpose purpose,Target target,Set<String> fields,String traceId) {
        public Request {Objects.requireNonNull(principal);Objects.requireNonNull(purpose);Objects.requireNonNull(target);fields=Set.copyOf(fields);fields.forEach(GroupAccess::text);text(traceId);}
    }
    public record MaskRef(String type,String version,String level) {
        public MaskRef {text(type);text(version);text(level);}
    }
    public record MaskDomain(String type,String version) {
        public MaskDomain {text(type);text(version);}
        public boolean contains(MaskRef ref){return type.equals(ref.type())&&version.equals(ref.version());}
    }
    public record Grant(Mode mode,MaskRef mask) {
        public Grant {Objects.requireNonNull(mode);if((mode==Mode.MASKED)!=(mask!=null))throw invalid();}
        public static Grant raw(){return new Grant(Mode.RAW,null);}
        public static Grant denied(){return new Grant(Mode.DENIED,null);}
        public static Grant masked(MaskRef mask){return new Grant(Mode.MASKED,mask);}
    }
    /** gates는 모두 필수다. 데이터소스 적용 가능 그룹 어휘 목록을 gate로 넣지 않는다. */
    public record Gate(String key,Target target,String policyRevision,Set<String> groups) {
        public Gate {text(key);Objects.requireNonNull(target);text(policyRevision);groups=Set.copyOf(groups);}
    }
    /** defaultGrant는 등록 카탈로그 RAW 또는 검수된 API 기본 마스킹/미전송 정책이다. */
    public record Field(String parentGate,ValueType type,MaskDomain maskDomain,Grant defaultGrant,Map<String,Grant> exceptions) {
        public Field {text(parentGate);Objects.requireNonNull(type);Objects.requireNonNull(defaultGrant);exceptions=Map.copyOf(exceptions);}
    }
    public record Requirements(boolean quota,String identityKind,String identityTarget,Set<Control> controls) {
        public Requirements {controls=Set.copyOf(controls);if((identityKind==null)!=(identityTarget==null))throw invalid();}
        public Set<Control> all(){var all=new HashSet<>(controls);if(quota)all.add(Control.QUOTA);if(identityKind!=null)all.add(Control.IDENTITY);return Set.copyOf(all);}
    }
    /** requiredFields는 지식/인증 예제의 전부 통과해야 하는 참조 컬럼이다. */
    public record Policy(Target target,boolean complete,List<Gate> gates,Map<String,Field> fields,Set<String> requiredFields,Requirements requirements) {
        public Policy {Objects.requireNonNull(target);gates=List.copyOf(gates);fields=Map.copyOf(fields);requiredFields=Set.copyOf(requiredFields);Objects.requireNonNull(requirements);}
    }
    public record Snapshot(PrincipalRef principal,boolean eligible,String accountRevision,Instant checkedAt,Set<String> groups,Policy policy,Map<Control,Check> checks) {
        public Snapshot {Objects.requireNonNull(principal);text(accountRevision);Objects.requireNonNull(checkedAt);groups=Set.copyOf(groups);Objects.requireNonNull(policy);checks=Map.copyOf(checks);}
    }
    public record Binding(Request request,Map<String,String> policyRevisions,String accountRevision) {
        public Binding {Objects.requireNonNull(request);policyRevisions=Map.copyOf(policyRevisions);text(accountRevision);}
    }
    public record Decision(Status status,String reason,Binding binding,Instant checkedAt,Map<String,Grant> fields,Map<String,ValueType> types) {
        public Decision {Objects.requireNonNull(status);text(reason);Objects.requireNonNull(binding);Objects.requireNonNull(checkedAt);fields=Map.copyOf(fields);types=Map.copyOf(types);}
    }
    private final MaskingRegistry masks;
    public GroupAccess(MaskingRegistry masks){this.masks=Objects.requireNonNull(masks);}
    /** 원천 조립자 전용. 보호 아래 새로 읽은 스냅샷을 다시 판정한다. 보관한 Decision을 수락하지 않는다. */
    public <T> CurrentDataAccess.Outcome<T> withSnapshot(Request request,Snapshot snapshot,java.util.function.Function<CurrentDataAccess.Use,T> acceptance) {
        var decision=evaluate(request,snapshot);
        if(decision.status()!=Status.ALLOW)return new CurrentDataAccess.Outcome<>(decision.status()==Status.DENY?CurrentDataAccess.OutcomeStatus.DENIED:CurrentDataAccess.OutcomeStatus.UNAVAILABLE,decision.reason(),null);
        var scope=new ProjectionScope(decision,masks);
        try{return new CurrentDataAccess.Outcome<>(CurrentDataAccess.OutcomeStatus.COMPLETED,"CURRENT",acceptance.apply(scope));}
        finally{scope.active=false;}
    }
    public Decision evaluate(Request request,Snapshot snapshot) {
        var revisions=new LinkedHashMap<String,String>();var gates=new LinkedHashMap<String,Gate>();var policy=snapshot.policy();
        boolean valid=policy.complete()&&policy.target().equals(request.target())&&!policy.gates().isEmpty();
        boolean ownsTarget=false;
        for(var gate:policy.gates()) {
            if(gates.putIfAbsent(gate.key(),gate)!=null)valid=false;
            var ref=gate.target().kind()+":"+gate.target().id();
            var prior=revisions.putIfAbsent(ref,gate.policyRevision());if(prior!=null&&!prior.equals(gate.policyRevision()))valid=false;
            if(gate.target().equals(policy.target()))ownsTarget=true;
        }
        var binding=new Binding(request,revisions,snapshot.accountRevision());
        if(request.principal().domain()!=AuthDomain.PORTAL||!request.principal().equals(snapshot.principal())||!snapshot.eligible())return rejected(Status.DENY,"ACCOUNT_DENIED",binding,snapshot.checkedAt());
        if(!valid||!ownsTarget)return rejected(Status.UNAVAILABLE,"POLICY_UNAVAILABLE",binding,snapshot.checkedAt());
        if(snapshot.groups().isEmpty()||gates.values().stream().anyMatch(g->Collections.disjoint(g.groups(),snapshot.groups())))return rejected(Status.DENY,"GROUP_DENIED",binding,snapshot.checkedAt());
        for(var control:policy.requirements().all()) {
            var check=snapshot.checks().getOrDefault(control,Check.UNAVAILABLE);
            if(check!=Check.PASSED)return rejected(check==Check.DENIED?Status.DENY:Status.UNAVAILABLE,"CONTROL_"+control.name(),binding,snapshot.checkedAt());
        }
        var selected=new HashSet<>(request.fields());selected.addAll(policy.requiredFields());
        var grants=new LinkedHashMap<String,Grant>();var types=new LinkedHashMap<String,ValueType>();
        for(String path:selected) {
            var field=policy.fields().get(path);
            if(field==null)return rejected(Status.DENY,"FIELD_UNREGISTERED",binding,snapshot.checkedAt());
            var parent=gates.get(field.parentGate());
            if(parent==null||!parent.groups().containsAll(field.exceptions().keySet()))return rejected(Status.UNAVAILABLE,"FIELD_POLICY_INVALID",binding,snapshot.checkedAt());
            var candidates=new ArrayList<Grant>();
            for(String group:snapshot.groups())if(parent.groups().contains(group))candidates.add(field.exceptions().getOrDefault(group,field.defaultGrant()));
            if(candidates.stream().anyMatch(g->g.mode()==Mode.MASKED&&(field.maskDomain()==null||!field.maskDomain().contains(g.mask()))))return rejected(Status.UNAVAILABLE,"MASK_TYPE_MISMATCH",binding,snapshot.checkedAt());
            Grant grant=masks.combine(candidates,field.type());
            if(grant==null)return rejected(Status.UNAVAILABLE,"MASK_UNSUPPORTED",binding,snapshot.checkedAt());
            if(policy.requiredFields().contains(path)&&grant.mode()==Mode.DENIED)return rejected(Status.DENY,"REFERENCE_DENIED",binding,snapshot.checkedAt());
            if(request.fields().contains(path)){grants.put(path,grant);types.put(path,field.type());}
        }
        return new Decision(Status.ALLOW,"CURRENT",binding,snapshot.checkedAt(),grants,types);
    }
    private static Decision rejected(Status status,String reason,Binding binding,Instant at){return new Decision(status,reason,binding,at,Map.of(),Map.of());}
    static void text(String value){if(value==null||value.isBlank()||value.length()>512)throw invalid();}
    static IllegalArgumentException invalid(){return new IllegalArgumentException("접근 정책 계약이 올바르지 않다");}
}
