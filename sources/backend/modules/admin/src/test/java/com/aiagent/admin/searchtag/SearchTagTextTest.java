// @feat FEAT-024
package com.aiagent.admin.searchtag;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TC-0243: 표시 원문과 검색 동일성을 분리하고 Unicode 경계도 보존한다. */
class SearchTagTextTest {
    @Test void normalizesCompatibilityAndEdgeSpacesButPreservesInternalSpacingAndNonAsciiCase() {
        assertThat(SearchTagText.normalize("\u2003ＷＭＳ\u00a0")).isEqualTo("wms");
        assertThat(SearchTagText.normalize(" A  B ")).isEqualTo("a  b");
        assertThat(SearchTagText.normalize("ÄßİΣ")).isEqualTo("ÄßİΣ");
        assertThat(SearchTagText.normalize("\u0085\tWMS\n")).isEqualTo("wms");
        assertThat(SearchTagText.normalize("\u001cWMS\u001c")).isEqualTo("\u001cwms\u001c");
    }
    @Test void rejectsEmptyOverlongAndExpandedExpressionsWithoutTruncating() {
        for(String value:new String[]{null,"", "\u2003\u00a0", "가".repeat(201), "㍿".repeat(101)})
            assertThatThrownBy(()->SearchTagText.normalize(value)).isInstanceOf(BusinessException.class);
        assertThat(SearchTagText.normalize("😀".repeat(200))).isEqualTo("😀".repeat(200));
        assertThat(SearchTagText.normalize("㍿".repeat(100))).hasSize(400);
    }
    @Test void rejectsSynonymsMatchingTheParentOrEachOtherButAllowsReuseUnderAnotherTag() {
        assertThatThrownBy(()->SearchTagText.synonyms("WMS",List.of(" ｗｍｓ "))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->SearchTagText.synonyms("WMS",List.of("Warehouse","ＷＡＲＥＨＯＵＳＥ"))).isInstanceOf(BusinessException.class);
        assertThat(SearchTagText.synonyms("WMS",List.of("Stock"))).containsExactly("stock");
        assertThat(SearchTagText.synonyms("ERP",List.of("Stock"))).containsExactly("stock");
        assertThat(SearchTagText.synonyms("WMS",List.of())).isEmpty();
    }
}
