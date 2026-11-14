// @feat FEAT-024
package com.aiagent.admin.searchtag;
import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.error.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
class SearchTagDefinitionTest {
    @Test void preservesRawInputAndRejectsForgedFieldsAndAmbiguousChildIdentity() {
        var synonym=Map.<String,Object>of("clientKey","one","searchText"," 倉庫 ","description","설명");
        var input=Map.<String,Object>of("searchText"," ＷＭＳ ","useYn","N","synonyms",List.of(synonym));
        var parsed=SearchTagDefinition.parse(input);
        assertThat(parsed.searchText()).isEqualTo(" ＷＭＳ ");
        assertThat(parsed.normalizedText()).isEqualTo("wms");
        assertThat(SearchTagDefinition.parse(parsed.values())).isEqualTo(parsed);
        var forged=new HashMap<>(input);forged.put("normalizedText","forged");
        assertThatThrownBy(()->SearchTagDefinition.parse(forged)).isInstanceOf(BusinessException.class);
        for(var child:List.of(Map.of("synonymId","123","clientKey","one","searchText","Warehouse"),Map.of("searchText","Warehouse"),Map.of("clientKey","x","searchText","ｗｍｓ"))) {
            var bad=new HashMap<>(input);bad.put("synonyms",List.of(child));
            assertThatThrownBy(()->SearchTagDefinition.parse(bad)).isInstanceOf(BusinessException.class);
        }
    }
}
