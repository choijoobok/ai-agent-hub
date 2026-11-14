// @feat FEAT-024
package com.aiagent.admin.searchtag;
import com.aiagent.admin.managed.ManagedCommand;
import java.util.*;
public record SearchTagDefinition(String searchText,String normalizedText,String useYn,List<Synonym> synonyms) {
    public record Synonym(String synonymId,String clientKey,String searchText,String normalizedText,String description) {}
    public static SearchTagDefinition parse(Map<String,Object> values) {
        if(!values.keySet().equals(Set.of("searchText","useYn","synonyms")))bad();
        String text=text(values.get("searchText")),normalized=SearchTagText.normalize(text);
        if(!Set.of("Y","N").contains(values.get("useYn")))bad();
        if(!(values.get("synonyms") instanceof List<?>))bad();
        var rows=new ArrayList<Synonym>();var keys=new HashSet<String>();
        for(Object item:(List<?>)values.get("synonyms")) {
            if(!(item instanceof Map<?,?>))bad();var row=(Map<?,?>)item;
            if(!Set.of("synonymId","clientKey","searchText","description").containsAll(row.keySet()))bad();
            String id=row.get("synonymId")==null?null:text(row.get("synonymId"));
            String client=row.get("clientKey")==null?null:text(row.get("clientKey"));
            if((id==null)==(client==null))bad();
            if(id!=null)ManagedCommand.positive(id);
            if(client!=null&&(client.isBlank()||client.length()>100))bad();
            if(!keys.add(id==null?"new:"+client:id))bad();
            String expression=text(row.get("searchText"));
            String description=row.get("description")==null?null:text(row.get("description"));
            rows.add(new Synonym(id,client,expression,SearchTagText.normalize(expression),description));
        }
        SearchTagText.synonyms(text,rows.stream().map(Synonym::searchText).toList());
        rows.sort(Comparator.comparing(row->row.synonymId()==null?"new:"+row.clientKey():row.synonymId()));
        return new SearchTagDefinition(text,normalized,(String)values.get("useYn"),List.copyOf(rows));
    }
    private static String text(Object value) {
        if(!(value instanceof String))bad();String text=(String)value;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);if(c==0)bad();
            if(Character.isHighSurrogate(c)){if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(++i)))bad();}
            else if(Character.isLowSurrogate(c))bad();
        }
        return text;
    }
    private static void bad(){throw SearchTagErrorCode.INVALID.failure();}
    public Map<String,Object> values() {
        return Map.of("searchText",searchText,"useYn",useYn,"synonyms",synonyms.stream().map(row->{
            var value=new LinkedHashMap<String,Object>();value.put("synonymId",row.synonymId());value.put("clientKey",row.clientKey());
            value.put("searchText",row.searchText());value.put("description",row.description());return value;
        }).toList());
    }
}
