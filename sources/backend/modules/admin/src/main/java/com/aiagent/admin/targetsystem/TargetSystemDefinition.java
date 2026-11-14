// @feat FEAT-001
package com.aiagent.admin.targetsystem;
import com.aiagent.admin.managed.ManagedErrorCode;
import java.util.*;

record TargetSystemDefinition(String name,String description,List<Connection> connections) {
    record Connection(String id,String name,String description) {}
    static TargetSystemDefinition parse(Map<String,Object> input) {
        if(!input.keySet().equals(Set.of("systemNameOverride","descriptionOverride","connectionOverrides"))||!(input.get("connectionOverrides") instanceof List<?> list)||list.size()>10000)throw invalid();
        var connections=new ArrayList<Connection>();var keys=new HashSet<String>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> row)||!row.keySet().equals(Set.of("connectionId","nameOverride","descriptionOverride"))||!(row.get("connectionId") instanceof String id)||!id.matches("[a-z][a-z0-9-]{0,99}")||!keys.add(id))throw invalid();
            connections.add(new Connection(id,text(row.get("nameOverride"),true),text(row.get("descriptionOverride"),false)));
        }
        connections.sort(Comparator.comparing(Connection::id));
        return new TargetSystemDefinition(text(input.get("systemNameOverride"),true),text(input.get("descriptionOverride"),false),List.copyOf(connections));
    }
    Map<String,Object> values() {
        var result=new LinkedHashMap<String,Object>();result.put("systemNameOverride",name);result.put("descriptionOverride",description);
        result.put("connectionOverrides",connections.stream().map(c->{var row=new LinkedHashMap<String,Object>();row.put("connectionId",c.id());row.put("nameOverride",c.name());row.put("descriptionOverride",c.description());return row;}).toList());return result;
    }
    private static String text(Object value,boolean name){if(value==null)return null;if(!(value instanceof String text)||text.length()>(name?100:10000)||name&&text.isBlank())throw invalid();return text;}
    private static RuntimeException invalid(){return ManagedErrorCode.VALIDATION.failure();}
}
