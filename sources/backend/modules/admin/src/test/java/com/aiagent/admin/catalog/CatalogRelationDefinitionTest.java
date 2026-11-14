// @feat FEAT-011
package com.aiagent.admin.catalog;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class CatalogRelationDefinitionTest {
 static Map<String,Object> relation(){var r=new LinkedHashMap<String,Object>();r.put("clientKey","new-relation");r.put("relationName","주문 고객");r.put("leftCatalogId","1");r.put("leftRevision","3");r.put("rightCatalogId","2");r.put("rightRevision","5");r.put("leftRole","주문");r.put("rightRole","고객");r.put("cardinality","N:1");r.put("description","고객별 주문. 미매칭 주문과 주문 없는 고객이 있을 수 있다.");r.put("reviewStatus","VERIFIED");r.put("pairs",List.of(Map.of("position",2,"leftColumnId","12","rightColumnId","22"),Map.of("position",1,"leftColumnId","11","rightColumnId","21")));return r;}
 @Test void orderedWholePairsAndServerFields(){
  var normalized=CatalogDefinition.normalize("RELATIONSHIPS",Map.of("relations",List.of(relation())));
  var r=CatalogDefinition.map(CatalogDefinition.list(normalized.get("relations")).getFirst());
  assertThat(CatalogDefinition.map(CatalogDefinition.list(r.get("pairs")).getFirst()).get("position")).isEqualTo(1);
  for(String field:List.of("ownerCatalogId","regId","reviewerId","dataSourceId")){var forged=relation();forged.put(field,"9");assertThatThrownBy(()->CatalogDefinition.normalize("RELATIONSHIPS",Map.of("relations",List.of(forged)))).isInstanceOf(RuntimeException.class);}
 }
 @Test void incompleteDuplicateAndForgedIdentityAreRejected(){
  var missing=relation();missing.remove("cardinality");assertThatThrownBy(()->CatalogDefinition.normalize("RELATIONSHIPS",Map.of("relations",List.of(missing)))).isInstanceOf(RuntimeException.class);
  var bad=relation();bad.put("relationId","99");assertThatThrownBy(()->CatalogDefinition.normalize("RELATIONSHIPS",Map.of("relations",List.of(bad)))).isInstanceOf(RuntimeException.class);
  for(var pairs:List.of(List.of(),List.of(Map.of("position",0,"leftColumnId","11","rightColumnId","21")),List.of(Map.of("position",1,"leftColumnId","11","rightColumnId","21"),Map.of("position",1,"leftColumnId","12","rightColumnId","22")),List.of(Map.of("position",1,"leftColumnId","11","rightColumnId","21"),Map.of("position",2,"leftColumnId","11","rightColumnId","21")))){var row=relation();row.put("pairs",pairs);assertThatThrownBy(()->CatalogDefinition.normalize("RELATIONSHIPS",Map.of("relations",List.of(row)))).isInstanceOf(RuntimeException.class);}
 }
}
