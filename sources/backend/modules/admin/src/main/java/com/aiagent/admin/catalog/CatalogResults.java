// @feat FEAT-003
package com.aiagent.admin.catalog;
import com.aiagent.admin.managed.ManagedResult;
import java.util.*;
public final class CatalogResults {
 private CatalogResults(){}
 public static Map<String,Object> management(ManagedResult r,String base,List<Map<String,Object>> mappings){var target=new LinkedHashMap<String,Object>();target.put("targetKind","CATALOG");target.put("targetId",r.targetId());target.put("baseRevision",base);target.put("appliedRevision",r.revision());target.put("historyNo",r.historyNo());var result=new LinkedHashMap<String,Object>();result.put("commandKey",r.commandKey());result.put("status",r.outcome());result.put("targets",List.of(target));result.put("memberMappings",mappings);result.put("appliedAt",r.appliedAt());return result;}
}
