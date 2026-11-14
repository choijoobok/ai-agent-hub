// @feat FEAT-042
package com.aiagent.admin.quota;

import com.aiagent.admin.managed.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** 전송 입력을 손실 없는 십진 값으로 정규화한다. 순위는 비율이 아니며 합계100 검증은 없다. */
public record QuotaGradeDefinition(String startMonth,String memo,List<Grade> grades,Map<String,String> transitions) {
    public record Grade(String gradeId,String clientKey,String gradeNm,int rankNo,BigDecimal weight,String description) {}
    public static QuotaGradeDefinition parse(Map<String,Object> values) {
        if(!values.keySet().equals(Set.of("startMonth","memo","grades","transitionMappings")))bad();
        String month=text(values.get("startMonth"),7,false);month(month);
        String memo=text(values.get("memo"),20000,true);
        if(!(values.get("grades") instanceof List<?>))bad();
        var input=(List<?>)values.get("grades");if(input.isEmpty()||input.size()>1000)bad();
        var rows=new ArrayList<Grade>();var names=new HashSet<String>();var ranks=new HashSet<Integer>();var ids=new HashSet<String>();
        for(Object item:input) {
            if(!(item instanceof Map<?,?>))bad();var row=(Map<?,?>)item;
            if(!Set.of("gradeId","clientKey","gradeNm","rankNo","weight","description").containsAll(row.keySet()))bad();
            String id=row.get("gradeId")==null?null:text(row.get("gradeId"),19,false);
            if(id!=null)ManagedCommand.positive(id);
            String client=id==null?text(row.get("clientKey"),100,false):null;
            String name=text(row.get("gradeNm"),100,false);String description=text(row.get("description"),20000,true);
            if(!(row.get("rankNo") instanceof Number))bad();
            int rank;
            try {rank=new BigDecimal(row.get("rankNo").toString()).intValueExact();}catch(ArithmeticException|NumberFormatException e){throw ManagedErrorCode.VALIDATION.failure();}
            String raw=text(row.get("weight"),32,false);if(!raw.matches("[0-9]+(?:[.][0-9]{1,2})?"))bad();
            BigDecimal weight=new BigDecimal(raw).setScale(2);
            if(rank<=0||weight.signum()<=0||weight.precision()>12||!names.add(name)||!ranks.add(rank)||!ids.add(id==null?"new:"+client:id))bad();
            rows.add(new Grade(id,client,name,rank,weight,description));
        }
        rows.sort(Comparator.comparingInt(Grade::rankNo));
        for(int i=1;i<rows.size();i++)if(rows.get(i).weight().compareTo(rows.get(i-1).weight())<0)bad();
        if(!(values.get("transitionMappings") instanceof List<?>))bad();
        var transitions=new TreeMap<String,String>();
        for(Object item:(List<?>)values.get("transitionMappings")) {
            if(!(item instanceof Map<?,?>))bad();var map=(Map<?,?>)item;
            boolean fresh=map.containsKey("toClientKey");
            if(!map.keySet().equals(Set.of("fromGradeId",fresh?"toClientKey":"toGradeId")))bad();
            String from=text(map.get("fromGradeId"),19,false),to=fresh?"new:"+text(map.get("toClientKey"),100,false):text(map.get("toGradeId"),19,false);
            ManagedCommand.positive(from);if(!fresh)ManagedCommand.positive(to);
            if(from.equals(to)||transitions.put(from,to)!=null||!ids.contains(to)||ids.contains(from))bad();
        }
        return new QuotaGradeDefinition(month,memo,List.copyOf(rows),Map.copyOf(transitions));
    }
    public static YearMonth month(String value) {
        if(value==null||!value.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])"))throw ManagedErrorCode.VALIDATION.failure();
        return YearMonth.parse(value);
    }
    private static String text(Object value,int max,boolean empty) {
        if(value==null&&empty)return "";
        if(!(value instanceof String))throw ManagedErrorCode.VALIDATION.failure();
        String result=((String)value).strip();if(result.length()>max||(!empty&&result.isEmpty()))bad();return result;
    }
    private static void bad(){throw ManagedErrorCode.VALIDATION.failure();}
    public Map<String,Object> values() {
        var rows=new ArrayList<Map<String,Object>>();
        for(var grade:grades) {
            var row=new LinkedHashMap<String,Object>();row.put("gradeId",grade.gradeId());row.put("clientKey",grade.clientKey());
            row.put("gradeNm",grade.gradeNm());row.put("rankNo",grade.rankNo());row.put("weight",grade.weight().toPlainString());row.put("description",grade.description());rows.add(row);
        }
        return Map.of("startMonth",startMonth,"memo",memo,"grades",rows,"transitionMappings",transitions.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getValue().startsWith("new:")?Map.of("fromGradeId",e.getKey(),"toClientKey",e.getValue().substring(4)):Map.of("fromGradeId",e.getKey(),"toGradeId",e.getValue())).toList());
    }
}
