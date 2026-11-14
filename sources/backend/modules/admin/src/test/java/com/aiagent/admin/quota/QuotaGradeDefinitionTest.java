// @feat FEAT-042
package com.aiagent.admin.quota;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class QuotaGradeDefinitionTest {
    @Test void acceptsTransferToNewGradeOnlyWithinTheSameCommand() {
        var input=new HashMap<>(values(List.of(grade("신규",1,"1"))));
        input.put("transitionMappings",List.of(Map.of("fromGradeId","1","toClientKey","신규")));
        var result=QuotaGradeDefinition.parse(input);assertThat(result.transitions()).containsEntry("1","new:신규");
        assertThat(QuotaGradeDefinition.parse(result.values())).isEqualTo(result);
        input.put("transitionMappings",List.of(Map.of("fromGradeId","1","toClientKey","외부")));
        assertThatThrownBy(()->QuotaGradeDefinition.parse(input));
    }
    private Map<String,Object> grade(String name,int rank,String weight) {
        return Map.of("clientKey",name,"gradeNm",name,"rankNo",rank,"weight",weight,"description","");
    }
    private Map<String,Object> values(List<Map<String,Object>> rows) {
        return Map.of("startMonth","2040-01","memo","기준","grades",rows,"transitionMappings",List.of());
    }
    @Test void validatesRelativeWeightsAndStableInputWithoutPercentageRule() {
        var result=QuotaGradeDefinition.parse(values(List.of(grade("일반",1,"1"),grade("고급",2,"3.00"),grade("전문",3,"5"))));
        assertThat(result.grades()).hasSize(3);
        assertThat(result.grades().get(1).weight().toPlainString()).isEqualTo("3.00");
        for(var rows:List.of(List.<Map<String,Object>>of(),List.of(grade("일반",0,"1")),List.of(grade("일반",1,"0")),
                List.of(grade("일반",1,"1.001")),List.of(grade("일반",1,"2"),grade("고급",2,"1")),
                List.of(grade("일반",1,"1"),grade("일반",2,"2")),List.of(grade("일반",1,"1"),grade("고급",1,"2"))))
            assertThatThrownBy(()->QuotaGradeDefinition.parse(values(rows)));
    }
    @Test void rejectsUnknownFieldsBadMonthsAndForgedIdentifiers() {
        var good=values(List.of(grade("일반",1,"1")));
        for(String month:List.of("2040-13","40-01","2040-1","0000-01")) {
            var bad=new HashMap<>(good);bad.put("startMonth",month);assertThatThrownBy(()->QuotaGradeDefinition.parse(bad));
        }
        var forged=new HashMap<>(good);forged.put("regId","admin");assertThatThrownBy(()->QuotaGradeDefinition.parse(forged));
        var row=new HashMap<>(grade("일반",1,"1"));row.put("gradeId",12);assertThatThrownBy(()->QuotaGradeDefinition.parse(values(List.of(row))));
    }
}
