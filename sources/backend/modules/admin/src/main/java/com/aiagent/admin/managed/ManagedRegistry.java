// @feat FEAT-040
package com.aiagent.admin.managed;

import java.util.*;

/** 소유 빈이 명시 설치한 단위만 허용한다. 미구현 원장을 예외 삼킴으로 숨기지 않는다. */
public final class ManagedRegistry {
    public record Operation(String id,String path,String handler,boolean delete,ManagedAssetAdapter owner,ManagedJournal journal) {}
    private final Map<String,Operation> operations;
    private final Map<String,ManagedAssetAdapter> owners;
    private final List<ManagedJournal> journals;
    public ManagedRegistry(javax.sql.DataSource source,List<Operation> declarations,List<ManagedJournal> journals) {
        var ops=new LinkedHashMap<String,Operation>(); var assets=new LinkedHashMap<String,ManagedAssetAdapter>();
        var stores=new HashMap<String,ManagedJournal>();
        for(var store:journals)if(store.dataSource()!=source||stores.putIfAbsent(store.name(),store)!=null)throw new IllegalStateException("명령 원장 DB/이름이 일치하지 않는다");
        var paths=new HashSet<String>();
        for(var op:declarations) {
            if(op.owner().dataSource()!=source||op.owner().history().dataSource()!=source||!op.owner().kind().equals(op.owner().history().kind()))
                throw new IllegalStateException("현재값·이력·명령 결과는 같은 DB 소유자로 설치해야 한다");
            if(!op.path().matches("/api/admin/service/[A-Za-z0-9]+/[A-Za-z0-9]+")||op.id().equals("API-082")
                    ||ops.putIfAbsent(op.id(),op)!=null||!paths.add(op.path())||stores.get(op.journal().name())!=op.journal())
                throw new IllegalStateException("관리 저장 경로/명령 원장 설치가 일치하지 않는다");
            var old=assets.putIfAbsent(op.owner().kind(),op.owner());
            if(old!=null&&old!=op.owner())throw new IllegalStateException("같은 자산 종류의 소유자가 중복됐다");
        }
        operations=Map.copyOf(ops); owners=Map.copyOf(assets); this.journals=List.copyOf(journals);
    }
    public Collection<Operation> operations() { return operations.values(); }
    public Operation operation(String id) { var op=operations.get(id); if(op==null)throw ManagedErrorCode.VALIDATION.failure();return op; }
    public Optional<Operation> path(String path) { return operations.values().stream().filter(op->op.path().equals(path)).findFirst(); }
    public ManagedAssetAdapter owner(String kind) { var owner=owners.get(kind);if(owner==null)throw ManagedErrorCode.HISTORY_UNAVAILABLE.failure();return owner; }
    public List<ManagedJournal> journals() { return journals; }
    public Optional<ManagedJournal.Entry> find(String key) {
        var found=journals.stream().map(j->j.find(key)).flatMap(Optional::stream).toList();
        if(found.size()>1)throw ManagedErrorCode.UNAVAILABLE.failure();
        return found.stream().findFirst();
    }
    public List<ManagedAssetAdapter> commonOwners(String feature) {
        return operations.values().stream().filter(o->o.journal().name().equals("ENT-099")&&o.owner().feature().equals(feature))
                .map(Operation::owner).distinct().sorted(Comparator.comparing(ManagedAssetAdapter::kind)).toList();
    }
}
