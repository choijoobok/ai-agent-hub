// @feat FEAT-002
package com.aiagent.admin.datasource;
import com.aiagent.admin.managed.*;
import com.aiagent.admin.shared.configuration.ConfigurationDeploymentService;
import com.aiagent.support.deployment.ExternalDataSources;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
public class DataSourceService implements ManagedAssetAdapter {
    public static final String BASE="/api/admin/service/dataSource";
    static final String ROOT="AI_ADMIN_DATA_SOURCE",GROUP="AI_ADMIN_DATA_SOURCE_GROUP",GUIDE="AI_ADMIN_QUERY_GUIDE";
    static final List<String> ROOT_COLUMNS=List.of("DATA_SOURCE_ID","ALIAS_NM","DESCRIPTION","DBMS_CD","DBMS_VERSION","DW_SYNC_MEMO","SOURCE_TYPE_CD","TIME_ZONE_ID","DATA_BASIS","MANAGEMENT_VERSION","REG_ID","REG_DTM","MOD_ID","MOD_DTM","CHANGE_REASON","COMMAND_KEY","DATA_SOURCE_KEY","SYNC_CONFIG_ID","REG_SOURCE_CD","MOD_SOURCE_CD","ACCEPTED_DTM");
    private final JdbcTemplate jdbc;private final ManagedIds ids;private final TypedHistoryStore history;
    private final DataSourceReferenceService references;private final ExternalDataSources loaded;private final ConfigurationDeploymentService deployment;
    public DataSourceService(DataSource source,ManagedIds ids,DataSourceReferenceService refs,ExternalDataSources loaded,ConfigurationDeploymentService deployment){
        jdbc=new JdbcTemplate(source);this.ids=ids;references=refs;this.loaded=loaded;this.deployment=deployment;
        history=new TypedHistoryStore(source,ids,kind(),"MANAGEMENT_VERSION",List.of(new TypedHistoryStore.Table(ROOT,"DATA_SOURCE_ID",ROOT_COLUMNS),
            new TypedHistoryStore.Table(GROUP,"DATA_SOURCE_ID",List.of("DATA_SOURCE_ID","GROUP_ID","ACTIVE_YN","REG_ID","REG_DTM","MOD_ID","MOD_DTM")),
            new TypedHistoryStore.Table(GUIDE,"DATA_SOURCE_ID",List.of("GUIDE_ID","DATA_SOURCE_ID","TITLE","DETAIL_TEXT","REG_ID","REG_DTM","MOD_ID","MOD_DTM"))),rows->true,rows->List.of());
    }
    public DataSource dataSource(){return jdbc.getDataSource();}public String feature(){return "FEAT-002";}public String kind(){return "DATA_SOURCE";}
    public String readPath(){return BASE+"/getList";}public String writePath(){return BASE+"/save";}public Set<String> scopes(){return Set.of("main","cleanup");}
    public Map<String,Object> normalize(ManagedCommand c){if(c.scope().equals("cleanup"))return com.aiagent.admin.shared.configuration.ConfigurationCleanup.normalizeFor(c,Set.of("DATASOURCE"));if(c.targetId()==null)throw ManagedErrorCode.VALIDATION.failure();return DataSourceDefinition.parse(c.values()).values();}
    public List<ManagedLocks.Key> locks(ManagedCommand c){var keys=new ArrayList<ManagedLocks.Key>();keys.add(ConfigurationDeploymentService.boundary(loaded.environment()));if(c.scope().equals("main")){
        // 그룹 정의 삭제와 부모/자식 변경은 같은 전역 그룹 경계를 공유한다.
        for(var g:DataSourceDefinition.parse(c.values()).groups())keys.add(new ManagedLocks.Key(3,"USER_GROUP",g.groupId()));
    }return keys;}
    public Current current(ManagedCommand c){return jdbc.query("SELECT DATA_SOURCE_ID,MANAGEMENT_VERSION FROM "+ROOT+" WHERE DATA_SOURCE_ID=?",(r,n)->new Current(r.getString(1),r.getString(2)),ManagedCommand.positive(c.targetId())).stream().findFirst().orElse(null);}
    public void validate(ManagedCommand c,Current before){
        deployment.requireReady(loaded);var v=DataSourceDefinition.parse(c.values());long id=Long.parseLong(before.targetId());
        var existing=jdbc.queryForList("SELECT GROUP_ID,ACTIVE_YN FROM "+GROUP+" WHERE DATA_SOURCE_ID=?",id);
        for(var g:v.groups())if(jdbc.queryForObject("SELECT COUNT(*) FROM AI_ADMIN_USER_GROUP WHERE GROUP_ID=?",Long.class,Long.valueOf(g.groupId()))!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
        for(var g:existing){String group=g.get("GROUP_ID").toString();var next=v.groups().stream().filter(x->x.groupId().equals(group)).findFirst();
            boolean remove=next.isEmpty(),off=!remove&&"Y".equals(g.get("ACTIVE_YN"))&&!next.get().mappingActive();
            if(remove||off){var ref=references.groups(before.targetId(),group,!remove);if(ref!=DataSourceReferencePort.Reference.ABSENT)throw (ref==DataSourceReferencePort.Reference.PRESENT?ManagedErrorCode.REFERENCE_CONFLICT:ManagedErrorCode.UNAVAILABLE).failure();}
        }
        for(var guide:v.guides())if(guide.guideId()!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM "+GUIDE+" WHERE GUIDE_ID=? AND DATA_SOURCE_ID=?",Long.class,Long.valueOf(guide.guideId()),id)!=1)throw ManagedErrorCode.REFERENCE_CONFLICT.failure();
    }
    public Map<String,Object> values(String id){
        var r=jdbc.queryForMap("SELECT * FROM "+ROOT+" WHERE DATA_SOURCE_ID=?",ManagedCommand.positive(id));var v=new LinkedHashMap<String,Object>();
        v.put("dbmsVersion",r.get("DBMS_VERSION"));v.put("sourceType",r.get("SOURCE_TYPE_CD"));v.put("syncMethodNote",r.get("DW_SYNC_MEMO"));v.put("timeZoneId",r.get("TIME_ZONE_ID"));v.put("description",r.get("DESCRIPTION"));v.put("dataBasis",r.get("DATA_BASIS"));
        v.put("applicableGroups",jdbc.query("SELECT GROUP_ID,ACTIVE_YN FROM "+GROUP+" WHERE DATA_SOURCE_ID=? ORDER BY GROUP_ID",(rs,n)->Map.of("groupId",rs.getString(1),"mappingActive","Y".equals(rs.getString(2))),Long.valueOf(id)));
        v.put("queryGuides",jdbc.query("SELECT GUIDE_ID,TITLE,DETAIL_TEXT FROM "+GUIDE+" WHERE DATA_SOURCE_ID=? ORDER BY GUIDE_ID",(rs,n)->Map.of("guideId",rs.getString(1),"title",rs.getString(2),"description",rs.getString(3)),Long.valueOf(id)));return v;
    }
    public boolean changed(ManagedCommand c,Current before){return !DataSourceDefinition.parse(c.values()).equals(DataSourceDefinition.parse(values(before.targetId())));}
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public String apply(ManagedCommand c,Current before,Stamp s){
        long id=Long.parseLong(before.targetId());var v=DataSourceDefinition.parse(c.values());var at=Timestamp.from(s.at());
        jdbc.update("UPDATE "+ROOT+" SET DBMS_VERSION=?,SOURCE_TYPE_CD=?,DW_SYNC_MEMO=?,TIME_ZONE_ID=?,DESCRIPTION=?,DATA_BASIS=?,MANAGEMENT_VERSION=?,MOD_ID=?,MOD_DTM=?,MOD_SOURCE_CD='ADMIN',CHANGE_REASON=?,COMMAND_KEY=?,ACCEPTED_DTM=? WHERE DATA_SOURCE_ID=?",v.dbmsVersion(),v.sourceType(),v.syncMethodNote(),v.timeZoneId(),v.description(),v.dataBasis(),Long.valueOf(s.revision()),s.actor(),at,s.reason(),s.commandKey(),at,id);
        var kept=new HashSet<String>();
        for(var g:v.groups()){kept.add(g.groupId());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM "+GROUP+" WHERE DATA_SOURCE_ID=? AND GROUP_ID=?",Long.class,id,Long.valueOf(g.groupId()))==0)jdbc.update("INSERT INTO "+GROUP+" VALUES (?,?,?,?,?,?,?)",id,Long.valueOf(g.groupId()),g.mappingActive()?"Y":"N",s.actor(),at,s.actor(),at);
            else jdbc.update("UPDATE "+GROUP+" SET ACTIVE_YN=?,MOD_ID=?,MOD_DTM=? WHERE DATA_SOURCE_ID=? AND GROUP_ID=? AND ACTIVE_YN<>?",g.mappingActive()?"Y":"N",s.actor(),at,id,Long.valueOf(g.groupId()),g.mappingActive()?"Y":"N");
        }
        for(String old:jdbc.queryForList("SELECT GROUP_ID FROM "+GROUP+" WHERE DATA_SOURCE_ID=?",String.class,id))if(!kept.contains(old))jdbc.update("DELETE FROM "+GROUP+" WHERE DATA_SOURCE_ID=? AND GROUP_ID=?",id,Long.valueOf(old));
        kept.clear();
        for(var g:v.guides()){
            long gid=g.guideId()==null?ids.next("AI_ADMIN_QUERY_GUIDE_ID"):Long.parseLong(g.guideId());kept.add(Long.toString(gid));
            if(g.guideId()==null)jdbc.update("INSERT INTO "+GUIDE+" VALUES (?,?,?,?,?,?,?,?)",gid,id,g.title(),g.description(),s.actor(),at,s.actor(),at);
            else jdbc.update("UPDATE "+GUIDE+" SET TITLE=?,DETAIL_TEXT=?,MOD_ID=?,MOD_DTM=? WHERE GUIDE_ID=? AND DATA_SOURCE_ID=? AND (TITLE IS DISTINCT FROM ? OR DETAIL_TEXT IS DISTINCT FROM ?)",g.title(),g.description(),s.actor(),at,gid,id,g.title(),g.description());
        }
        for(String old:jdbc.queryForList("SELECT GUIDE_ID FROM "+GUIDE+" WHERE DATA_SOURCE_ID=?",String.class,id))if(!kept.contains(old))jdbc.update("DELETE FROM "+GUIDE+" WHERE GUIDE_ID=? AND DATA_SOURCE_ID=?",Long.valueOf(old),id);
        return before.targetId();
    }
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void delete(ManagedCommand c,Current before){throw ManagedErrorCode.VALIDATION.failure();}public TypedHistoryStore history(){return history;}
}
