// @feat FEAT-002
package com.aiagent.admin.datasource;

import com.aiagent.admin.managed.*;
import com.aiagent.support.time.Timezones;
import java.util.*;

/** 전체 소유 입력만 받는다. 후보 조회 실패를 빈 자식 집합으로 바꾸지 않는다. */
public record DataSourceDefinition(String dbmsVersion,String sourceType,String syncMethodNote,String timeZoneId,String description,String dataBasis,List<Group> groups,List<Guide> guides) {
    public record Group(String groupId,boolean mappingActive) {}
    public record Guide(String guideId,String clientKey,String title,String description) {}
    private static final Set<String> FIELDS=Set.of("dbmsVersion","sourceType","syncMethodNote","timeZoneId","description","dataBasis","applicableGroups","queryGuides");
    public static DataSourceDefinition parse(Map<String,Object> input) {
        if(input==null||!input.keySet().equals(FIELDS))throw ManagedErrorCode.VALIDATION.failure();
        String version=optional(input,"dbmsVersion",Integer.MAX_VALUE),type=required(input,"sourceType",2),memo=optional(input,"syncMethodNote",Integer.MAX_VALUE),zone=optional(input,"timeZoneId",100);
        if(version!=null){version=version.trim();if(version.length()>100)throw ManagedErrorCode.VALIDATION.failure();}
        if(!Set.of("DB","DW").contains(type)||zone!=null&&!Timezones.isKnown(zone))throw ManagedErrorCode.VALIDATION.failure();
        if(input.get("timeZoneId")!=null&&(!Objects.equals(zone,input.get("timeZoneId"))||zone==null))throw ManagedErrorCode.VALIDATION.failure();
        var groups=new ArrayList<Group>();var keys=new HashSet<String>();
        for(var row:list(input,"applicableGroups")) {
            if(!row.keySet().equals(Set.of("groupId","mappingActive"))||!(row.get("mappingActive") instanceof Boolean active))throw ManagedErrorCode.VALIDATION.failure();
            String id=required(row,"groupId",30);ManagedCommand.positive(id);if(!keys.add(id))throw ManagedErrorCode.VALIDATION.failure();groups.add(new Group(id,active));
        }
        groups.sort(Comparator.comparing(g->ManagedCommand.positive(g.groupId())));
        var guides=new ArrayList<Guide>();keys.clear();
        for(var row:list(input,"queryGuides")) {
            if(!Set.of("guideId","clientKey","title","description").containsAll(row.keySet()))throw ManagedErrorCode.VALIDATION.failure();
            String id=optional(row,"guideId",30),client=optional(row,"clientKey",100);
            if((id==null)==(client==null))throw ManagedErrorCode.VALIDATION.failure();if(id!=null)ManagedCommand.positive(id);
            if(!keys.add((id==null?"client/":"id/")+(id==null?client:id)))throw ManagedErrorCode.VALIDATION.failure();
            guides.add(new Guide(id,client,required(row,"title",Integer.MAX_VALUE),required(row,"description",Integer.MAX_VALUE)));
        }
        guides.sort(Comparator.comparing(g->g.guideId()==null?"client/"+g.clientKey():"id/"+g.guideId()));
        return new DataSourceDefinition(version,type,memo,zone,optional(input,"description",Integer.MAX_VALUE),optional(input,"dataBasis",Integer.MAX_VALUE),List.copyOf(groups),List.copyOf(guides));
    }
    public Map<String,Object> values() {
        var v=new LinkedHashMap<String,Object>();v.put("dbmsVersion",dbmsVersion);v.put("sourceType",sourceType);v.put("syncMethodNote",syncMethodNote);v.put("timeZoneId",timeZoneId);v.put("description",description);v.put("dataBasis",dataBasis);
        v.put("applicableGroups",groups.stream().map(g->Map.of("groupId",g.groupId(),"mappingActive",g.mappingActive())).toList());
        v.put("queryGuides",guides.stream().map(g->{var row=new LinkedHashMap<String,Object>();row.put(g.guideId()==null?"clientKey":"guideId",g.guideId()==null?g.clientKey():g.guideId());row.put("title",g.title());row.put("description",g.description());return row;}).toList());return v;
    }
    private static String required(Map<String,Object> row,String field,int max){String v=optional(row,field,max);if(v==null)throw ManagedErrorCode.VALIDATION.failure();return v;}
    private static String optional(Map<String,Object> row,String field,int max){Object v=row.get(field);if(v==null)return null;if(!(v instanceof String text)||text.length()>max)throw ManagedErrorCode.VALIDATION.failure();return text.isBlank()?null:text;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> list(Map<String,Object> row,String field){if(!(row.get(field) instanceof List<?> list)||list.size()>1000||list.stream().anyMatch(v->!(v instanceof Map<?,?>)))throw ManagedErrorCode.VALIDATION.failure();return (List<Map<String,Object>>)(List<?>)list;}
}
