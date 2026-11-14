// @feat FEAT-005
package com.aiagent.admin.portal;

import com.aiagent.admin.managed.*;
import java.time.*;
import java.util.*;

/** 화면 소유 입력만 정규화한다. 자격 비밀/감사/현재 관측값은 관리 명령에 들어오지 않는다. */
public final class PortalDefinition {
    private PortalDefinition() {}
    public static Map<String,Object> normalize(ManagedCommand command) {
        var input=command.values();var out=new LinkedHashMap<String,Object>();
        switch(command.scope()) {
            case "profile" -> {
                keys(input,Set.of("loginId","userNm","organizationNm","useStartDt","useEndDt","activeYn","externalSource","externalSubject"));
                boolean external=command.targetId()==null&&input.containsKey("externalSource");
                out.put("loginId",text(input,"loginId",100,!external));out.put("userNm",text(input,"userNm",100,!external));
                out.put("organizationNm",text(input,"organizationNm",200,false));
                LocalDate start=date(input.get("useStartDt")),end=input.get("useEndDt")==null||"".equals(input.get("useEndDt"))?null:date(input.get("useEndDt"));
                if(end!=null&&end.isBefore(start))bad();out.put("useStartDt",start.toString());out.put("useEndDt",end==null?null:end.toString());
                out.put("activeYn",yn(input,"activeYn"));
                if(input.containsKey("externalSource")||input.containsKey("externalSubject")) {
                    out.put("externalSource",text(input,"externalSource",100,true));out.put("externalSubject",text(input,"externalSubject",255,true));
                }
            }
            case "memberships" -> {
                keys(input,Set.of("memberships","deactivateUser"));
                if(!(input.get("deactivateUser") instanceof Boolean))bad();
                var seen=new HashSet<String>();var rows=new ArrayList<Map<String,Object>>();
                for(var row:rows(input,"memberships")) {
                    keys(row,Set.of("groupId","activeYn","memo"));String id=id(row,"groupId");if(!seen.add(id))bad();
                    rows.add(Map.of("groupId",id,"activeYn",yn(row,"activeYn"),"memo",text(row,"memo",2000,true)));
                }
                out.put("memberships",rows);out.put("deactivateUser",input.get("deactivateUser"));
            }
            case "grades" -> {
                keys(input,Set.of("grades"));var seen=new HashSet<String>();var originals=new HashSet<String>();var rows=new ArrayList<Map<String,Object>>();
                for(var row:rows(input,"grades")) {
                    keys(row,Set.of("effectiveMonth","originalMonth","gradeId","activeYn"));
                    String month=month(row.get("effectiveMonth"));if(!seen.add(month))bad();
                    var value=new LinkedHashMap<String,Object>();value.put("effectiveMonth",month);value.put("gradeId",id(row,"gradeId"));value.put("activeYn",yn(row,"activeYn"));
                    if(row.get("originalMonth")!=null)value.put("originalMonth",month(row.get("originalMonth")));
                    if(!originals.add((String)value.getOrDefault("originalMonth",month)))bad();rows.add(value);
                }
                out.put("grades",rows);
            }
            case "identitySystem","identityDataSource" -> {
                keys(input,Set.of("identities"));var seen=new HashSet<String>();var rows=new ArrayList<Map<String,Object>>();
                for(var row:rows(input,"identities")) {
                    keys(row,Set.of("targetId","externalUserId","mappingActiveYn","identityEvidence"));String id=id(row,"targetId");if(!seen.add(id))bad();
                    String active=yn(row,"mappingActiveYn");boolean required="Y".equals(active);
                    rows.add(Map.of("targetId",id,"mappingActiveYn",active,"externalUserId",text(row,"externalUserId",200,required),"identityEvidence",text(row,"identityEvidence",20000,required)));
                }
                out.put("identities",rows);
            }
            case "resetCredential","unlockCredential" -> {if(!input.isEmpty()||command.targetId()==null)bad();}
            default -> bad();
        }
        return Collections.unmodifiableMap(out);
    }
    static void keys(Map<String,Object> value,Set<String> allowed){if(!allowed.containsAll(value.keySet()))bad();}
    static String text(Map<String,Object> values,String key,int limit,boolean required) {
        Object raw=values.get(key);if(raw==null&&!required)return "";
        if(!(raw instanceof String))throw ManagedErrorCode.VALIDATION.failure();String value=((String)raw).strip();
        if(required&&value.isEmpty()||value.codePointCount(0,value.length())>limit)bad();return value;
    }
    static String id(Map<String,Object> row,String key){String value=text(row,key,19,true);ManagedCommand.positive(value);return value;}
    static String yn(Map<String,Object> row,String key){String value=text(row,key,1,true);if(!Set.of("Y","N").contains(value))bad();return value;}
    static LocalDate date(Object value){try{return LocalDate.parse((String)value);}catch(DateTimeException|ClassCastException|NullPointerException error){throw ManagedErrorCode.VALIDATION.failure();}}
    static String month(Object value){if(!(value instanceof String text)||!text.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))throw ManagedErrorCode.VALIDATION.failure();return YearMonth.parse(text).toString();}
    static List<Map<String,Object>> rows(Map<String,Object> values,String key) {
        if(!(values.get(key) instanceof List<?> input)||input.isEmpty()||input.size()>10000)throw ManagedErrorCode.VALIDATION.failure();
        var rows=new ArrayList<Map<String,Object>>();
        for(Object raw:input) {
            if(!(raw instanceof Map<?,?> value))throw ManagedErrorCode.VALIDATION.failure();var row=new LinkedHashMap<String,Object>();
            for(var entry:value.entrySet()){if(!(entry.getKey() instanceof String))bad();row.put((String)entry.getKey(),entry.getValue());}rows.add(row);
        }
        return rows;
    }
    static void bad(){throw ManagedErrorCode.VALIDATION.failure();}
}
