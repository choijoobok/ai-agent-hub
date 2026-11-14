// @feat FEAT-001 FEAT-002
package com.aiagent.admin.shared.configuration;

import com.aiagent.admin.managed.*;
import com.aiagent.core.search.Condition;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** 일괄 정리는 공통 명령 봉투의 cleanup scope로 처리한다. */
public final class ConfigurationCleanup {
    private ConfigurationCleanup() {}
    private static final JsonMapper JSON=JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public record Query(String scope,String parentId,List<Condition> conditions){
        public Query {
            if(!Set.of("SYSTEM","CONNECTION","DATASOURCE").contains(scope))throw ManagedErrorCode.VALIDATION.failure();
            if(scope.equals("CONNECTION"))ManagedCommand.positive(parentId);else if(parentId!=null)throw ManagedErrorCode.VALIDATION.failure();
            conditions=conditions==null?List.of():List.copyOf(conditions);
        }
    }
    public record Confirmation(String id,String kind,String parentId,String expectedVersion) {
        public Confirmation {ManagedCommand.positive(id);ManagedCommand.positive(expectedVersion);if(!Set.of("SYSTEM","CONNECTION","DATASOURCE").contains(kind))throw ManagedErrorCode.VALIDATION.failure();if(kind.equals("CONNECTION"))ManagedCommand.positive(parentId);else if(parentId!=null)throw ManagedErrorCode.VALIDATION.failure();}
    }
    public record Values(String previewToken,Query query,List<Confirmation> confirmedTargets){
        public Values {
            ManagedCommand.requireKey(previewToken);Objects.requireNonNull(query);
            if(confirmedTargets==null||confirmedTargets.isEmpty()||confirmedTargets.size()>10_000)throw ManagedErrorCode.VALIDATION.failure();
            confirmedTargets=confirmedTargets.stream().sorted(Comparator.comparing(Confirmation::kind).thenComparing(c->ManagedCommand.positive(c.id()))).toList();
            if(confirmedTargets.stream().map(c->c.kind()+"/"+c.id()).distinct().count()!=confirmedTargets.size())throw ManagedErrorCode.VALIDATION.failure();
        }
    }
    public record Candidate(Confirmation target,String name,String connectionId,String diagnosisId) {}
    public record PreviewTarget(Confirmation target,String name,String reason) {}
    public record Preview(String previewToken,Query query,String appliedSettingsId,List<PreviewTarget> targets,boolean executable,Instant expiresAt) {}
    public record Applied(String id,String kind,String parentId,String expectedVersion,String resultVersion,String historyNo) {}
    public record Result(String commandKey,String action,Instant appliedAt,int deletedCount,List<Applied> targets) {}
    public static Values parse(ManagedCommand command){
        if(!command.scope().equals("cleanup")||command.targetId()!=null||command.expectedRevision()!=null)throw ManagedErrorCode.VALIDATION.failure();
        try {return JSON.convertValue(command.values(),Values.class);}catch(IllegalArgumentException failure){throw ManagedErrorCode.VALIDATION.failure();}
    }
    public static Map<String,Object> normalize(ManagedCommand command){
        @SuppressWarnings("unchecked") Map<String,Object> value=JSON.convertValue(parse(command),Map.class);return value;
    }
    public static Map<String,Object> normalizeFor(ManagedCommand command,Set<String> allowed){var parsed=parse(command);if(!allowed.contains(parsed.query().scope())||parsed.confirmedTargets().stream().anyMatch(t->!t.kind().equals(parsed.query().scope())))throw ManagedErrorCode.VALIDATION.failure();return normalize(command);}
    public static String digest(Query query,String bootId,String configId,long control){return ManagedDigest.hash(JSON.writeValueAsString(List.of(query,bootId,configId,control)));}
}
