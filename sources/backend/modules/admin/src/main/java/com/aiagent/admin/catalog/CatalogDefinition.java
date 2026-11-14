// @feat FEAT-003
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.*;
import java.util.*;
/** 버튼별 입력 소유권. 누락 영역과 명시 빈 목록을 구분하며 서버 소유 식별/검수시각은 받지 않는다. */
public final class CatalogDefinition {
 private CatalogDefinition(){}
 public static final Set<String> SCOPES=Set.of("MASTER","COLUMNS","ACCESS_GROUPS","COLUMN_GROUP_EXCEPTIONS","INDEXES");
 public static final List<String> MASTER=List.of("dataSourceId","serverName","databaseName","schemaName","objectName","objectType","businessName","description","useYn","definitionEvidence","identityEvidence","reviewStatus","reviewEvidence","pkName","pkReview","pkEvidence");
 public static final List<String> COLUMN=List.of("columnId","clientKey","columnName","ordinalPosition","sourceTypeCode","sourceType","nullable","columnBusinessName","primaryKeySummary","pkPosition","privacyFlag","privacyType","subjectIdentifier","privacyReview","maskingRef","semanticType","columnReviewStatus","groupCode","detailDescription","valueFormat","columnTimeZoneId","unitCode","valueReviewStatus","columnReviewEvidence","valueReviewEvidence","columnIdentityEvidence","sourceDimensions");
 public static final List<String> INDEX=List.of("indexId","clientKey","name","definitionKind","unique","columns","includedColumns","filter","expression","rawDefinition","definitionEvidence","supportStatus");
 public static Map<String,Object> normalize(String scope,Map<String,Object> v){
  if(scope==null||!SCOPES.contains(scope))throw bad();
  if(scope.equals("MASTER")){known(v,MASTER);var n=new LinkedHashMap<String,Object>();for(String f:MASTER)n.put(f,v.get(f));id(n.get("dataSourceId"));
   for(String f:List.of("databaseName","schemaName","objectName"))required(n.get(f),500);choice(n.get("objectType"),Set.of("TABLE","VIEW"));bool(n.get("useYn"));
   for(String f:List.of("businessName","pkName","serverName"))text(n.get(f),500);
   for(String f:List.of("description","definitionEvidence","identityEvidence","reviewEvidence","pkEvidence"))text(n.get(f),Integer.MAX_VALUE);
   defaults(n,"reviewStatus","UNKNOWN");defaults(n,"pkReview","UNKNOWN");review(n.get("reviewStatus"));review(n.get("pkReview"));return n;
  }
  String key=switch(scope){case "COLUMNS"->"columns";case "ACCESS_GROUPS"->"groups";case "COLUMN_GROUP_EXCEPTIONS"->"exceptions";default->"indexes";};
  if(!v.keySet().equals(Set.of(key)))throw bad();var rows=list(v.get(key));var result=new ArrayList<Map<String,Object>>();var ids=new HashSet<Object>();
  for(var raw:rows){var row=map(raw);var n=new LinkedHashMap<String,Object>(row);
   if(key.equals("columns")){known(row,COLUMN);member(row,"columnId");for(String f:COLUMN)n.putIfAbsent(f,null);if(n.get("columnName")!=null)required(n.get("columnName"),500);
    text(n.get("sourceType"),200);text(n.get("sourceTypeCode"),30);integer(n.get("ordinalPosition"),1,Integer.MAX_VALUE);integer(n.get("pkPosition"),1,Integer.MAX_VALUE);
    for(String f:List.of("nullable","primaryKeySummary","privacyFlag","subjectIdentifier")){defaults(n,f,"UNKNOWN");choice(n.get(f),f.equals("primaryKeySummary")?Set.of("YES","NO","UNKNOWN","NA"):Set.of("YES","NO","UNKNOWN"));}
    for(String f:List.of("privacyReview","columnReviewStatus","valueReviewStatus")){defaults(n,f,"UNKNOWN");review(n.get(f));}
    defaults(n,"semanticType","UNKNOWN");choice(n.get("semanticType"),Set.of("UNKNOWN","DATE","DATETIME","AMOUNT","QUANTITY","FLAG","CODE","IDENTIFIER","TEXT"));
    if(n.get("columnTimeZoneId")!=null&&!java.time.ZoneId.getAvailableZoneIds().contains(n.get("columnTimeZoneId")))throw bad();
    for(String f:COLUMN)if(n.get(f) instanceof String)text(n.get(f),f.endsWith("Evidence")||f.equals("detailDescription")||f.equals("groupCode")?Integer.MAX_VALUE:500);
    if(n.get("sourceDimensions")!=null)dimensions(map(n.get("sourceDimensions")));
   }else if(key.equals("groups")){known(row,List.of("groupId","mappingActive"));id(row.get("groupId"));bool(row.get("mappingActive"));}
   else if(key.equals("exceptions")){known(row,List.of("columnId","groupId","mode","reason"));id(row.get("columnId"));id(row.get("groupId"));choice(row.get("mode"),Set.of("HIDE","MASK"));required(row.get("reason"),2000);}
   else {known(row,INDEX);member(row,"indexId");text(row.get("name"),500);choice(row.get("definitionKind"),Set.of("INDEX","UNIQUE_KEY"));choice(row.get("unique"),Set.of("YES","NO","UNKNOWN"));if("UNIQUE_KEY".equals(row.get("definitionKind"))&&!"YES".equals(row.get("unique")))throw bad();
    var parts=list(row.get("columns"));if(parts.isEmpty())throw bad();var partIds=new HashSet<Object>();for(var part:parts){var p=map(part);known(p,List.of("columnId","direction"));id(p.get("columnId"));choice(p.get("direction"),Set.of("ASC","DESC","UNKNOWN"));if(!partIds.add(p.get("columnId")))throw bad();}
    if(row.get("includedColumns")!=null)for(Object value:list(row.get("includedColumns")))id(value);
    for(String f:List.of("filter","expression","rawDefinition","definitionEvidence"))text(row.get(f),Integer.MAX_VALUE);
    defaults(n,"supportStatus","UNKNOWN");choice(n.get("supportStatus"),Set.of("UNKNOWN","SUPPORTED","UNSUPPORTED"));
   }
   Object stable=key.equals("columns")?n.get("columnId"):key.equals("indexes")?n.get("indexId"):key.equals("groups")?n.get("groupId"):n.get("columnId")+":"+n.get("groupId");
   if(stable==null)stable="new:"+n.get("clientKey");if(!ids.add(stable))throw bad();result.add(n);
  }return Map.of(key,result);
 }
 private static void dimensions(Map<String,Object> d){known(d,List.of("state","characterLength","octetLength","numericPrecision","numericScale","lengthUnit","lengthStatus","precisionStatus","scaleStatus","raw"));for(String f:List.of("state","lengthStatus","precisionStatus","scaleStatus"))if(d.get(f)!=null)choice(d.get(f),Set.of("KNOWN","UNKNOWN","NA","UNSUPPORTED"));if(d.get("lengthUnit")!=null)choice(d.get("lengthUnit"),Set.of("CHAR","BYTE"));for(String f:List.of("characterLength","octetLength","numericPrecision"))integer(d.get(f),0,Integer.MAX_VALUE);integer(d.get("numericScale"),-1000000,1000000);text(d.get("raw"),500);}
 private static void member(Map<String,Object> row,String key){boolean existing=row.get(key)!=null;if(existing==(row.get("clientKey")!=null))throw bad();if(existing)id(row.get(key));else required(row.get("clientKey"),100);}
 private static void defaults(Map<String,Object> n,String key,Object value){if(n.get(key)==null)n.put(key,value);}
 public static void known(Map<String,Object> m,Collection<String> allowed){if(!allowed.containsAll(m.keySet()))throw bad();}
 public static Map<String,Object> map(Object v){if(!(v instanceof Map<?,?> m)||m.keySet().stream().anyMatch(k->!(k instanceof String)))throw bad();var result=new LinkedHashMap<String,Object>();m.forEach((k,val)->result.put((String)k,val));return result;}
 public static List<?> list(Object v){if(!(v instanceof List<?> l)||l.size()>1000)throw bad();return l;}
 private static void choice(Object v,Set<String> values){if(!(v instanceof String)||!values.contains(v))throw bad();}
 private static void bool(Object v){if(!(v instanceof Boolean))throw bad();}
 public static void id(Object v){if(!(v instanceof String s))throw bad();ManagedCommand.positive(s);}
 private static void text(Object v,int max){if(v!=null&&(!(v instanceof String s)||s.codePointCount(0,s.length())>max))throw bad();}
 private static void required(Object v,int max){text(v,max);if(v==null||((String)v).isBlank())throw bad();}
 private static void integer(Object v,long min,long max){if(v!=null&&(!(v instanceof Number n)||n.doubleValue()!=n.longValue()||n.longValue()<min||n.longValue()>max))throw bad();}
 private static void review(Object v){choice(v,Set.of("UNKNOWN","VERIFIED","INVALID"));}
 private static RuntimeException bad(){return ManagedErrorCode.VALIDATION.failure();}
}
