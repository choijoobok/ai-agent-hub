// @feat FEAT-003
package com.aiagent.admin.catalog;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.admin.managed.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class CatalogDefinitionTest {
 @Test void rejectsCrossAreaAndUnknownFlagsAndPreservesSourceText(){
  assertThatThrownBy(()->CatalogDefinition.normalize("MASTER",Map.of("columns",List.of()))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->CatalogDefinition.normalize("COLUMNS",Map.of("columns",List.of(Map.of("clientKey","new","columnName","x","privacyFlag","MAYBE"))))).isInstanceOf(RuntimeException.class);
  var column=new LinkedHashMap<String,Object>();column.put("clientKey","new");column.put("columnName","x");column.put("detailDescription","  예시\n    설명");column.put("privacyFlag","UNKNOWN");
  assertThat(((Map<?,?>)((List<?>)CatalogDefinition.normalize("COLUMNS",Map.of("columns",List.of(column))).get("columns")).getFirst()).get("detailDescription")).isEqualTo("  예시\n    설명");
 }
 @Test void draftIndexNameIsOptionalButUniqueKeyUniquenessIsFixed(){
  var draft=new LinkedHashMap<String,Object>();draft.put("clientKey","draft");draft.put("definitionKind","INDEX");draft.put("unique","UNKNOWN");draft.put("columns",List.of(Map.of("columnId","1","direction","UNKNOWN")));
  assertThat(CatalogDefinition.normalize("INDEXES",Map.of("indexes",List.of(draft)))).containsKey("indexes");draft.put("definitionKind","UNIQUE_KEY");assertThatThrownBy(()->CatalogDefinition.normalize("INDEXES",Map.of("indexes",List.of(draft)))).isInstanceOf(RuntimeException.class);draft.put("unique","YES");assertThat(CatalogDefinition.normalize("INDEXES",Map.of("indexes",List.of(draft)))).containsKey("indexes");
 }

}
