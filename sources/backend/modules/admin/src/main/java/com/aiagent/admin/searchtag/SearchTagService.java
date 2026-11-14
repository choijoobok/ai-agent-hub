// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
public class SearchTagService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/searchTag";
    static final String ROOT="AI_ADMIN_SEARCH_TAG",CHILD="AI_ADMIN_TAG_SYNONYM";
    private final JdbcTemplate jdbc;
    private final ManagedIds ids;
    private final ManagedLocks locks;
    private final SearchTagReferenceQueryService references;
    private final ManagedHistoryStore history;
    public SearchTagService(DataSource source,ManagedIds ids,ManagedLocks locks,SearchTagReferenceQueryService references) {
        jdbc=new JdbcTemplate(source);this.ids=ids;this.locks=locks;this.references=references;
        history=new TypedHistoryStore(source,ids,kind(),"REVISION",List.of(
            new TypedHistoryStore.Table(ROOT,"TAG_ID",List.of("TAG_ID","SEARCH_TEXT","NORMALIZED_TEXT","USE_YN","REVISION","ACCEPTED_DTM","CHANGE_REASON","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
            new TypedHistoryStore.Table(CHILD,"TAG_ID",List.of("SYNONYM_ID","TAG_ID","SEARCH_TEXT","NORMALIZED_TEXT","DESCRIPTION"))),rows->true,rows->List.of());
    }
    public DataSource dataSource(){return jdbc.getDataSource();}
    public String feature(){return "FEAT-024";}
    public String kind(){return "SEARCH_TAG";}
    public String readPath(){return BASE+"/getList";}
    public String writePath(){return BASE+"/save";}
    public Set<String> scopes(){return Set.of("definition");}
    static ManagedLocks.Key boundary(){return new ManagedLocks.Key(1,"SEARCH_TAG_NAMESPACE","all");}
    public List<ManagedLocks.Key> locks(ManagedCommand command){return List.of(boundary());}
    public Map<String,Object> normalize(ManagedCommand command){return command.targetId()!=null&&command.values().isEmpty()?Map.of():SearchTagDefinition.parse(command.values()).values();}
    public Current current(ManagedCommand command) {
        if(command.targetId()==null)return null;
        var rows=jdbc.query("SELECT TAG_ID,REVISION FROM "+ROOT+" WHERE TAG_ID=?",(r,n)->new Current(r.getString(1),r.getString(2)),ManagedCommand.positive(command.targetId()));
        return rows.isEmpty()?null:rows.getFirst();
    }
    public void validate(ManagedCommand command,Current before) {
        if(command.values().isEmpty())return;
        var value=SearchTagDefinition.parse(command.values());
        long target=before==null?0:Long.parseLong(before.targetId());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+ROOT+" WHERE NORMALIZED_TEXT=? AND TAG_ID<>?",Long.class,value.normalizedText(),target)!=0)throw SearchTagErrorCode.INVALID.failure();
        var existing=new HashSet<>(jdbc.queryForList("SELECT SYNONYM_ID FROM "+CHILD+" WHERE TAG_ID=?",String.class,target));
        for(var row:value.synonyms())if(row.synonymId()!=null&&!existing.contains(row.synonymId()))throw SearchTagErrorCode.INVALID.failure();
    }
    public boolean changed(ManagedCommand command,Current before) {
        var value=SearchTagDefinition.parse(command.values());long id=Long.parseLong(before.targetId());
        var old=jdbc.queryForMap("SELECT SEARCH_TEXT,USE_YN FROM "+ROOT+" WHERE TAG_ID=?",id);
        var rows=jdbc.query("SELECT SYNONYM_ID,SEARCH_TEXT,NORMALIZED_TEXT,DESCRIPTION FROM "+CHILD+" WHERE TAG_ID=?",(r,n)->new SearchTagDefinition.Synonym(r.getString(1),null,r.getString(2),r.getString(3),r.getString(4)),id);
        return !value.searchText().equals(old.get("SEARCH_TEXT"))||!value.useYn().equals(old.get("USE_YN"))||!new HashSet<>(rows).equals(new HashSet<>(value.synonyms()));
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public String apply(ManagedCommand command,Current before,Stamp stamp) {
        var value=SearchTagDefinition.parse(command.values());long id=before==null?ids.next(ROOT):Long.parseLong(before.targetId());var at=Timestamp.from(stamp.at());
        if(before==null)jdbc.update("INSERT INTO "+ROOT+"(TAG_ID,SEARCH_TEXT,NORMALIZED_TEXT,USE_YN,REVISION,ACCEPTED_DTM,CHANGE_REASON,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?)",id,value.searchText(),value.normalizedText(),value.useYn(),Long.parseLong(stamp.revision()),at,stamp.reason(),stamp.actor(),at,stamp.actor(),at);
        else jdbc.update("UPDATE "+ROOT+" SET SEARCH_TEXT=?,NORMALIZED_TEXT=?,USE_YN=?,REVISION=?,ACCEPTED_DTM=?,CHANGE_REASON=?,MOD_ID=?,MOD_DTM=? WHERE TAG_ID=?",value.searchText(),value.normalizedText(),value.useYn(),Long.parseLong(stamp.revision()),at,stamp.reason(),stamp.actor(),at,id);
        jdbc.update("DELETE FROM "+CHILD+" WHERE TAG_ID=?",id);
        for(var row:value.synonyms())jdbc.update("INSERT INTO "+CHILD+"(SYNONYM_ID,TAG_ID,SEARCH_TEXT,NORMALIZED_TEXT,DESCRIPTION) VALUES (?,?,?,?,?)",row.synonymId()==null?ids.next(CHILD):Long.parseLong(row.synonymId()),id,row.searchText(),row.normalizedText(),row.description());
        return Long.toString(id);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void delete(ManagedCommand command,Current before) {
        if(!command.values().isEmpty())throw SearchTagErrorCode.INVALID.failure();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+CHILD+" WHERE TAG_ID=?",Long.class,Long.parseLong(before.targetId()))!=0)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        references.requireUnreferenced(before.targetId());jdbc.update("DELETE FROM "+ROOT+" WHERE TAG_ID=?",Long.parseLong(before.targetId()));
    }
    public ManagedHistoryStore history(){return history;}
    /** 지식 매핑 저장의 소유 잠금보다 먼저 호출한다. 같은 트랜잭션에서 참조 삽입까지 유지한다. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void requireExisting(Collection<String> tagIds) {
        locks.ordered(List.of(boundary()));
        locks.ordered(tagIds.stream().map(id->{ManagedCommand.positive(id);return new ManagedLocks.Key(3,kind(),id);}).toList());
        for(String id:tagIds)if(jdbc.queryForObject("SELECT COUNT(*) FROM "+ROOT+" WHERE TAG_ID=?",Long.class,Long.parseLong(id))!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
    }
    public record Saved(String commandKey,String targetId,String revision,String historyNo,Instant appliedAt,String action,String outcome,Map<String,String> createdIds) {}
    /** 공통 명령 확정과 같은 인증된 트랜잭션에서 정확 이력으로 신규 자식 매핑을 재현한다. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Saved result(ManagedResult result,ManagedCommand command) {
        var created=new TreeMap<String,String>();
        if(result.historyNo()!=null&&!command.values().isEmpty()) {
            var event=history.event(Long.parseLong(result.historyNo()));
            for(var row:SearchTagDefinition.parse(command.values()).synonyms())if(row.clientKey()!=null) {
                var found=event.rows().get(CHILD).stream().filter(item->row.normalizedText().equals(item.get("NORMALIZED_TEXT"))).findFirst().orElseThrow(()->ManagedErrorCode.HISTORY_UNAVAILABLE.failure());
                created.put(row.clientKey(),found.get("SYNONYM_ID").toString());
            }
        }
        return new Saved(result.commandKey(),result.targetId(),result.revision(),result.historyNo(),result.appliedAt(),result.action(),result.outcome(),Map.copyOf(created));
    }
}
