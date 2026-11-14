// @feat FEAT-011
package com.aiagent.admin.catalog;

import com.aiagent.admin.managed.*;
import java.sql.Timestamp;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** 같은 카탈로그의 관계 영역. 외부 정확 판은 불변 HIST에서만 해석한다. */
final class CatalogRelations {
    static final List<String> INPUT = List.of("relationId","clientKey","relationName","leftCatalogId","leftRevision","rightCatalogId","rightRevision","leftRole","rightRole","cardinality","description","reviewStatus","pairs");
    private final JdbcTemplate jdbc;
    private final ManagedIds ids;
    CatalogRelations(DataSource source, ManagedIds ids) { this.jdbc=new JdbcTemplate(source); this.ids=ids; }
    static Map<String,Object> normalize(Map<String,Object> values) {
        if (!values.keySet().equals(Set.of("relations"))) throw bad();
        var rows=new ArrayList<Map<String,Object>>(); var keys=new HashSet<Object>();
        for(var raw:CatalogDefinition.list(values.get("relations"))) {
            var r=CatalogDefinition.map(raw); CatalogDefinition.known(r,INPUT);
            boolean stable=r.get("relationId")!=null;
            if(stable==(r.get("clientKey")!=null))throw bad();
            if(stable)id(r.get("relationId")); else text(r.get("clientKey"),100);
            for(String f:List.of("leftCatalogId","rightCatalogId","leftRevision","rightRevision"))id(r.get(f));
            for(String f:List.of("relationName","leftRole","rightRole"))text(r.get(f),500);
            text(r.get("description"),Integer.MAX_VALUE);
            if(!(r.get("cardinality") instanceof String)||!Set.of("1:1","1:N","N:1").contains(r.get("cardinality")))throw bad();
            r.putIfAbsent("reviewStatus","UNKNOWN");
            if(!Set.of("UNKNOWN","VERIFIED","INVALID").contains(r.get("reviewStatus")))throw bad();
            Object key=stable?r.get("relationId"):"new:"+r.get("clientKey");if(!keys.add(key))throw bad();
            var pairs=new ArrayList<Map<String,Object>>();var positions=new HashSet<Long>();var columns=new HashSet<String>();
            for(var rawPair:CatalogDefinition.list(r.get("pairs"))) {
                var pair=CatalogDefinition.map(rawPair);CatalogDefinition.known(pair,List.of("position","leftColumnId","rightColumnId"));
                if(!(pair.get("position") instanceof Number n)||n.longValue()<1||n.longValue()>Integer.MAX_VALUE||n.doubleValue()!=n.longValue()||!positions.add(n.longValue()))throw bad();
                id(pair.get("leftColumnId"));id(pair.get("rightColumnId"));if(!columns.add(pair.get("leftColumnId")+":"+pair.get("rightColumnId")))throw bad();pairs.add(pair);
            }
            if(pairs.isEmpty())throw bad();pairs.sort(Comparator.comparingLong(p->((Number)p.get("position")).longValue()));r.put("pairs",pairs);rows.add(r);
        }
        rows.sort(Comparator.comparing(r->Objects.toString(r.get("relationId"),"new:"+r.get("clientKey"))));
        return Map.of("relations",rows);
    }
    static Map<String,Object> editable(Map<String,Object> row) {
        var r=new LinkedHashMap<String,Object>(); for(String key:INPUT)if(row.containsKey(key)&&row.get(key)!=null)r.put(key,row.get(key));
        if(r.get("pairs") instanceof List<?> pairs)r.put("pairs",pairs.stream().map(p->{var pair=CatalogDefinition.map(p);var v=new LinkedHashMap<String,Object>();for(String k:List.of("position","leftColumnId","rightColumnId"))v.put(k,pair.get(k));return v;}).toList());
        return r;
    }
    void validate(String owner, List<?> rows) {
        var previous=owned(owner); var ds=current(owner).get("DATA_SOURCE_ID");
        for(var raw:rows) {
            var r=CatalogDefinition.map(raw);String left=r.get("leftCatalogId").toString(),right=r.get("rightCatalogId").toString();
            if(r.get("relationId")==null&&!owner.equals(left))throw conflict();
            if(r.get("relationId")!=null&&previous.stream().noneMatch(p->r.get("relationId").equals(p.get("relationId"))))throw conflict();
            if(!Objects.equals(ds,current(left).get("DATA_SOURCE_ID"))||!Objects.equals(ds,current(right).get("DATA_SOURCE_ID")))throw conflict();
            var l=snapshot(owner,left,r.get("leftRevision").toString());var h=snapshot(owner,right,r.get("rightRevision").toString());
            for(var rawPair:CatalogDefinition.list(r.get("pairs"))) {
                var p=CatalogDefinition.map(rawPair);var a=l.column(p.get("leftColumnId"));var b=h.column(p.get("rightColumnId"));
                if(a.get("SOURCE_TYPE_CD")==null||!Objects.equals(a.get("SOURCE_TYPE_CD"),b.get("SOURCE_TYPE_CD")))throw conflict();
                String code=a.get("SOURCE_TYPE_CD").toString();var supported=jdbc.queryForList("SELECT P.REF_VAL AS PARENT,N.REF_VAL AS NATIVE FROM SYS_CODE C JOIN SYS_CODE_REF P ON P.GRP_CD=C.GRP_CD AND P.DTL_CD=C.DTL_CD AND P.REF_CD='PARENT' JOIN SYS_CODE_REF N ON N.GRP_CD=C.GRP_CD AND N.DTL_CD=C.DTL_CD AND N.REF_CD='NATIVE_TYPE' WHERE C.GRP_CD='DBMS_DATA_TYPE' AND C.DTL_CD=? AND C.USE_YN='Y'",code);
                var dbms=jdbc.queryForObject("SELECT DBMS_CD FROM AI_ADMIN_DATA_SOURCE WHERE DATA_SOURCE_ID=?",String.class,ds);
                if(supported.size()!=1||!Objects.equals(supported.getFirst().get("PARENT"),dbms)||!Objects.equals(supported.getFirst().get("NATIVE"),a.get("SOURCE_TYPE"))||!Objects.equals(a.get("SOURCE_TYPE"),b.get("SOURCE_TYPE")))throw conflict();
            }
        }
    }
    private record Snapshot(List<Map<String,Object>> columns,List<Map<String,Object>> keys,List<Map<String,Object>> parts) {
        Map<String,Object> column(Object id){return columns.stream().filter(c->c.get("COLUMN_ID").toString().equals(id)).findFirst().orElseThrow(CatalogRelations::conflict);}
        boolean unique(Set<String> wanted){return keys.stream().filter(k->Set.of("PRIMARY","UNIQUE").contains(k.get("KEY_KIND"))&&"VERIFIED".equals(k.get("REVIEW_STATUS"))).anyMatch(k->{var found=new HashSet<String>();for(var p:parts)if(Objects.equals(k.get("KEY_NO"),p.get("KEY_NO")))found.add(p.get("COLUMN_ID").toString());return !found.isEmpty()&&found.equals(wanted);});}
    }
    private Snapshot snapshot(String owner,String catalog,String revision) {
        long key=id(catalog), rev=id(revision);
        if(owner.equals(catalog)) {
            if(((Number)current(catalog).get("REVISION")).longValue()!=rev)throw conflict();
            return new Snapshot(jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_COLUMN WHERE CATALOG_ID=?",key),jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_KEY WHERE CATALOG_ID=?",key),jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_KEY_COLUMN WHERE CATALOG_ID=?",key));
        }
        var roots=jdbc.queryForList("SELECT HISTORY_NO FROM AI_ADMIN_CATALOG_HIST WHERE CATALOG_ID=? AND RESULT_REVISION=? AND HIST_ACTION<>'DELETE'",key,rev);
        if(roots.size()!=1)throw conflict();Object history=roots.getFirst().get("HISTORY_NO");
        return new Snapshot(jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_COLUMN_HIST WHERE HISTORY_NO=? AND CATALOG_ID=?",history,key),jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_KEY_HIST WHERE HISTORY_NO=? AND CATALOG_ID=?",history,key),jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_KEY_COLUMN_HIST WHERE HISTORY_NO=? AND CATALOG_ID=?",history,key));
    }
    private Map<String,Object> current(String catalog){var rows=jdbc.queryForList("SELECT DATA_SOURCE_ID,REVISION FROM AI_ADMIN_CATALOG WHERE CATALOG_ID=?",id(catalog));if(rows.size()!=1)throw conflict();return rows.getFirst();}
    void write(String owner, List<?> rows, ManagedAssetAdapter.Stamp stamp) {
        long catalog=id(owner); var previous=owned(owner); var keep=new HashSet<Long>();var at=Timestamp.from(stamp.at());
        for(var raw:rows) {
            var r=CatalogDefinition.map(raw);Map<String,Object> old=r.get("relationId")==null?null:previous.stream().filter(p->r.get("relationId").equals(p.get("relationId"))).findFirst().orElseThrow(CatalogRelations::conflict);
            long relation=old==null?ids.next("AI_ADMIN_CATALOG_MEMBER"):id(r.get("relationId"));keep.add(relation);
            var l=snapshot(owner,r.get("leftCatalogId").toString(),r.get("leftRevision").toString());var h=snapshot(owner,r.get("rightCatalogId").toString(),r.get("rightRevision").toString());
            var left=new HashSet<String>();var right=new HashSet<String>();for(var pair:CatalogDefinition.list(r.get("pairs"))){var p=CatalogDefinition.map(pair);left.add(p.get("leftColumnId").toString());right.add(p.get("rightColumnId").toString());}
            boolean verified="VERIFIED".equals(r.get("reviewStatus"))&&(!Set.of("1:1","1:N").contains(r.get("cardinality"))||l.unique(left))&&(!Set.of("1:1","N:1").contains(r.get("cardinality"))||h.unique(right));
            // 기술 참조 변경 시 기존 검수 완료를 승계하지 않는다.
            if(old!=null&&"VERIFIED".equals(old.get("reviewStatus"))&&!sameTechnical(old,r))verified=false;
            String review=verified?"VERIFIED":"INVALID".equals(r.get("reviewStatus"))?"INVALID":"UNKNOWN";
            boolean unchanged=old!=null&&sameTechnical(old,r)&&Objects.equals(old.get("relationName"),r.get("relationName"))&&Objects.equals(old.get("reviewStatus"),review);
            if(old==null)jdbc.update("INSERT INTO AI_ADMIN_CATALOG_MEMBER(MEMBER_ID,CATALOG_ID,MEMBER_KIND,REG_ID,REG_DTM,CLIENT_KEY,COMMAND_KEY,CREATED_REVISION) VALUES (?,?,'RELATION',?,?,?,?,?)",relation,catalog,stamp.actor(),at,r.get("clientKey"),stamp.commandKey(),Long.valueOf(stamp.revision()));
            var values=new ArrayList<Object>(Arrays.asList(catalog,relation,"RELATION",id(r.get("leftCatalogId")),ownRevision(owner,r.get("leftCatalogId"),r.get("leftRevision"),stamp),r.get("leftRole"),id(r.get("rightCatalogId")),ownRevision(owner,r.get("rightCatalogId"),r.get("rightRevision"),stamp),r.get("rightRole"),r.get("cardinality"),r.get("description"),old!=null&&sameStructure(old,r)?old.get("originKind"):"LOGICAL",old!=null&&sameStructure(old,r)?old.get("originEvidence"):r.get("description"),review,verified?stamp.actor():null,verified?at:null,verified?r.get("description"):old==null?null:old.get("reviewEvidence"),r.get("relationName"),old==null?stamp.actor():old.get("regId"),old==null?at:timestamp(old.get("regDtm")),unchanged?old.get("modId"):stamp.actor(),unchanged?timestamp(old.get("modDtm")):at));
            if(old!=null)jdbc.update("DELETE FROM AI_ADMIN_CATALOG_JOIN_PAIR WHERE CATALOG_ID=? AND RELATION_ID=?",catalog,relation);
            if(old!=null)jdbc.update("DELETE FROM AI_ADMIN_CATALOG_RELATION WHERE CATALOG_ID=? AND RELATION_ID=?",catalog,relation);
            jdbc.update("INSERT INTO AI_ADMIN_CATALOG_RELATION(CATALOG_ID,RELATION_ID,MEMBER_KIND,LEFT_CATALOG_ID,LEFT_REVISION_ID,LEFT_ROLE_NAME,RIGHT_CATALOG_ID,RIGHT_REVISION_ID,RIGHT_ROLE_NAME,CARDINALITY_CD,DESCRIPTION,ORIGIN_KIND,ORIGIN_EVIDENCE,REVIEW_STATUS,REVIEWER_ID,REVIEWED_DTM,REVIEW_EVIDENCE,RELATION_NM,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",values.toArray());
            var oldPairs=old==null?List.of():CatalogDefinition.list(old.get("pairs"));
            for(var rawPair:CatalogDefinition.list(r.get("pairs"))) {
                var pair=CatalogDefinition.map(rawPair);var original=oldPairs.stream().map(CatalogDefinition::map).filter(p->Objects.equals(p.get("leftColumnId"),pair.get("leftColumnId"))&&Objects.equals(p.get("rightColumnId"),pair.get("rightColumnId"))).findFirst().orElse(null);
                jdbc.update("INSERT INTO AI_ADMIN_CATALOG_JOIN_PAIR(CATALOG_ID,RELATION_ID,PAIR_NO,LEFT_REVISION_ID,LEFT_COLUMN_ID,RIGHT_REVISION_ID,RIGHT_COLUMN_ID,REG_ID,REG_DTM,MOD_ID,MOD_DTM) VALUES (?,?,?,?,?,?,?,?,?,?,?)",catalog,relation,pair.get("position"),ownRevision(owner,r.get("leftCatalogId"),r.get("leftRevision"),stamp),id(pair.get("leftColumnId")),ownRevision(owner,r.get("rightCatalogId"),r.get("rightRevision"),stamp),id(pair.get("rightColumnId")),original==null?stamp.actor():original.get("regId"),original==null?at:timestamp(original.get("regDtm")),original!=null&&Objects.equals(original.get("position"),pair.get("position"))?original.get("modId"):stamp.actor(),original!=null&&Objects.equals(original.get("position"),pair.get("position"))?timestamp(original.get("modDtm")):at);
            }
        }
        for(var old:previous){long key=id(old.get("relationId"));if(!keep.contains(key)){jdbc.update("DELETE FROM AI_ADMIN_CATALOG_JOIN_PAIR WHERE CATALOG_ID=? AND RELATION_ID=?",catalog,key);jdbc.update("DELETE FROM AI_ADMIN_CATALOG_RELATION WHERE CATALOG_ID=? AND RELATION_ID=?",catalog,key);}}
    }
    void advanceOwner(String owner,ManagedAssetAdapter.Stamp stamp,String scope) {
        long key=id(owner),revision=Long.parseLong(stamp.revision());
        for(String side:List.of("LEFT","RIGHT")) {
            jdbc.update("UPDATE AI_ADMIN_CATALOG_JOIN_PAIR P SET "+side+"_REVISION_ID=? FROM AI_ADMIN_CATALOG_RELATION R WHERE P.CATALOG_ID=R.CATALOG_ID AND P.RELATION_ID=R.RELATION_ID AND R.CATALOG_ID=? AND R."+side+"_CATALOG_ID=?",revision,key,key);
            jdbc.update("UPDATE AI_ADMIN_CATALOG_RELATION SET "+side+"_REVISION_ID=? WHERE CATALOG_ID=? AND "+side+"_CATALOG_ID=?",revision,key,key);
        }
        if(Set.of("COLUMNS","INDEXES","MASTER").contains(scope))jdbc.update("UPDATE AI_ADMIN_CATALOG_RELATION SET REVIEW_STATUS='UNKNOWN',REVIEWER_ID=NULL,REVIEWED_DTM=NULL WHERE CATALOG_ID=? AND (LEFT_CATALOG_ID=? OR RIGHT_CATALOG_ID=?)",key,key,key);
    }
    Map<String,Object> find(String relation){return project(jdbc.queryForMap("SELECT * FROM AI_ADMIN_CATALOG_RELATION WHERE RELATION_ID=?",id(relation)));}
    List<Map<String,Object>> owned(String owner){return jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_RELATION WHERE CATALOG_ID=? ORDER BY RELATION_ID",id(owner)).stream().map(this::project).toList();}
    Map<String,Object> related(String catalog){id(catalog);var rows=jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_RELATION WHERE LEFT_CATALOG_ID=? OR RIGHT_CATALOG_ID=? ORDER BY RELATION_ID",id(catalog),id(catalog)).stream().map(r->{var row=project(r);var sides=new ArrayList<String>();if(catalog.equals(row.get("leftCatalogId")))sides.add("LEFT");if(catalog.equals(row.get("rightCatalogId")))sides.add("RIGHT");row.put("matchedSides",sides);return row;}).toList();return Map.of("state","READY","rows",rows);}
    Map<String,Object> project(Map<String,Object> r) {
        var row=new LinkedHashMap<String,Object>();String owner=r.get("CATALOG_ID").toString();
        row.put("relationId",r.get("RELATION_ID").toString());row.put("ownerCatalogId",owner);row.put("revision",current(owner).get("REVISION").toString());
        for(var field:Map.of("relationName","RELATION_NM","leftCatalogId","LEFT_CATALOG_ID","leftRevision","LEFT_REVISION_ID","rightCatalogId","RIGHT_CATALOG_ID","rightRevision","RIGHT_REVISION_ID","leftRole","LEFT_ROLE_NAME","rightRole","RIGHT_ROLE_NAME","cardinality","CARDINALITY_CD","description","DESCRIPTION","reviewStatus","REVIEW_STATUS").entrySet())row.put(field.getKey(),r.get(field.getValue())==null?null:r.get(field.getValue()).toString());
        row.put("originKind",r.get("ORIGIN_KIND"));row.put("originEvidence",r.get("ORIGIN_EVIDENCE"));row.put("reviewEvidence",r.get("REVIEW_EVIDENCE"));row.put("reviewerId",r.get("REVIEWER_ID"));row.put("reviewedAt",instant(r.get("REVIEWED_DTM")));audit(row,r);
        var ds=jdbc.queryForMap("SELECT D.DATA_SOURCE_ID,D.DATA_SOURCE_KEY FROM AI_ADMIN_CATALOG C JOIN AI_ADMIN_DATA_SOURCE D ON D.DATA_SOURCE_ID=C.DATA_SOURCE_ID WHERE C.CATALOG_ID=?",id(owner));row.put("dataSourceId",ds.get("DATA_SOURCE_ID").toString());row.put("dataSourceKey",ds.get("DATA_SOURCE_KEY"));
        for(String side:List.of("left","right")){String catalog=row.get(side+"CatalogId").toString();String revision=row.get(side+"Revision").toString();var names=owner.equals(catalog)?jdbc.queryForList("SELECT OBJECT_NAME FROM AI_ADMIN_CATALOG_CONTENT WHERE CATALOG_ID=?",id(catalog)):jdbc.queryForList("SELECT C.OBJECT_NAME FROM AI_ADMIN_CATALOG_CONTENT_HIST C JOIN AI_ADMIN_CATALOG_HIST H ON H.HISTORY_NO=C.HISTORY_NO AND H.CATALOG_ID=C.CATALOG_ID WHERE H.CATALOG_ID=? AND H.RESULT_REVISION=?",id(catalog),id(revision));row.put(side+"ObjectName",names.size()==1?names.getFirst().get("OBJECT_NAME"):null);}
        for(String side:List.of("left","right")){var snapshot=snapshot(owner,row.get(side+"CatalogId").toString(),row.get(side+"Revision").toString());row.put(side+"Columns",snapshot.columns().stream().map(c->{var v=new LinkedHashMap<String,Object>();for(var f:Map.of("columnId","COLUMN_ID","columnName","COLUMN_NAME","columnBusinessName","BUSINESS_NAME","ordinalPosition","ORDINAL_POSITION","sourceTypeCode","SOURCE_TYPE_CD","sourceType","SOURCE_TYPE").entrySet())v.put(f.getKey(),f.getKey().equals("columnId")?c.get(f.getValue()).toString():c.get(f.getValue()));return v;}).toList());}
        row.put("pairs",jdbc.queryForList("SELECT * FROM AI_ADMIN_CATALOG_JOIN_PAIR WHERE CATALOG_ID=? AND RELATION_ID=? ORDER BY PAIR_NO",id(owner),id(row.get("relationId"))).stream().map(p->{var v=new LinkedHashMap<String,Object>();v.put("position",((Number)p.get("PAIR_NO")).intValue());v.put("leftColumnId",p.get("LEFT_COLUMN_ID").toString());v.put("rightColumnId",p.get("RIGHT_COLUMN_ID").toString());audit(v,p);return v;}).toList());return row;
    }
    private static boolean sameStructure(Map<String,Object> a,Map<String,Object> b){return List.of("leftCatalogId","leftRevision","rightCatalogId","rightRevision").stream().allMatch(k->Objects.equals(a.get(k),b.get(k)))&&pairValues(a).equals(pairValues(b));}
    private static List<List<Object>> pairValues(Map<String,Object> value){return CatalogDefinition.list(value.get("pairs")).stream().map(CatalogDefinition::map).map(p->List.of(p.get("position"),p.get("leftColumnId"),p.get("rightColumnId"))).toList();}
    private static boolean sameTechnical(Map<String,Object> a,Map<String,Object> b){return List.of("leftCatalogId","leftRevision","rightCatalogId","rightRevision","leftRole","rightRole","cardinality","description").stream().allMatch(k->Objects.equals(a.get(k),b.get(k)))&&CatalogDefinition.list(a.get("pairs")).stream().map(CatalogDefinition::map).map(p->List.of(p.get("position"),p.get("leftColumnId"),p.get("rightColumnId"))).toList().equals(CatalogDefinition.list(b.get("pairs")).stream().map(CatalogDefinition::map).map(p->List.of(p.get("position"),p.get("leftColumnId"),p.get("rightColumnId"))).toList());}
    private static long ownRevision(String owner,Object endpoint,Object supplied,ManagedAssetAdapter.Stamp stamp){return Long.parseLong(owner.equals(endpoint)?stamp.revision():supplied.toString());}
    private static void audit(Map<String,Object> target,Map<String,Object> row){target.put("regId",row.get("REG_ID"));target.put("regDtm",instant(row.get("REG_DTM")));target.put("modId",row.get("MOD_ID"));target.put("modDtm",instant(row.get("MOD_DTM")));}
    private static Object instant(Object value){return value==null?null:((Timestamp)value).toInstant().toString();}
    private static Timestamp timestamp(Object value){return value==null?null:Timestamp.from(java.time.Instant.parse(value.toString()));}
    private static void text(Object value,int max){if(!(value instanceof String s)||s.isBlank()||s.length()>max)throw bad();}
    private static long id(Object value){return ManagedCommand.positive(value instanceof String s?s:null);}
    private static RuntimeException bad(){return ManagedErrorCode.VALIDATION.failure();}
    private static RuntimeException conflict(){return ManagedErrorCode.REFERENCE_CONFLICT.failure();}
}
