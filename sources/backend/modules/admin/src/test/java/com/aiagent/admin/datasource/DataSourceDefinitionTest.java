// @feat FEAT-002
package com.aiagent.admin.datasource;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DataSourceDefinitionTest {
    static Map<String,Object> values() {
        var v=new LinkedHashMap<String,Object>();
        v.put("dbmsVersion","17");v.put("sourceType","DW");v.put("syncMethodNote","  ");
        v.put("timeZoneId",null);v.put("description","업무 조회");v.put("dataBasis",null);
        v.put("applicableGroups",List.of());v.put("queryGuides",List.of(Map.of("clientKey","new-1","title","코드 조회","description","현재 허용 원천에서 확인한다")));
        return v;
    }
    @Test void normalizesOnlyOwnedValues() {
        var input=values();input.put("queryGuides",List.of(Map.of("clientKey","new-1","title","코드 조회","description","  SELECT code\n  FROM allowed_source\n")));
        var parsed=DataSourceDefinition.parse(input);assertThat(parsed.guides().getFirst().description()).isEqualTo("  SELECT code\n  FROM allowed_source\n");
        var v=parsed.values();
        assertThat(v.get("syncMethodNote")).isNull();assertThat(v.get("timeZoneId")).isNull();
        assertThat((List<?>)v.get("queryGuides")).hasSize(1);
    }
    @Test void rejectsTechnicalAndIncompleteOrAmbiguousInput() {
        for(String field:List.of("alias","jdbcUrl","parserId","dbmsCode","regId")) {
            var v=values();v.put(field,"위조");assertThatThrownBy(()->DataSourceDefinition.parse(v)).isInstanceOf(RuntimeException.class);
        }
        var missing=values();missing.remove("applicableGroups");assertThatThrownBy(()->DataSourceDefinition.parse(missing)).isInstanceOf(RuntimeException.class);
        var badZone=values();badZone.put("timeZoneId","Asia/Seoull");assertThatThrownBy(()->DataSourceDefinition.parse(badZone)).isInstanceOf(RuntimeException.class);
        var duplicate=values();duplicate.put("queryGuides",List.of(Map.of("clientKey","same","title","가이드","description","본문"),Map.of("clientKey","same","title","다른 제목","description","본문")));
        assertThatThrownBy(()->DataSourceDefinition.parse(duplicate)).isInstanceOf(RuntimeException.class);
    }
}
