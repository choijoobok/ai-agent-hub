// @feat FEAT-006
package com.aiagent.admin.group;

import static org.assertj.core.api.Assertions.*;
import static com.aiagent.admin.group.UserGroupLinkSource.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UserGroupLinksTest {
    private UserGroupLinkSource source(Source source,Reference reference) {
        return new UserGroupLinkSource() {
            public Source source(){return source;}
            public Projection read(long id){return new Projection(State.READY,List.of());}
            public Reference references(long id){return reference;}
        };
    }
    @Test void missingOwnerDoesNotPermitDelete() {
        var links=new UserGroupLinks(List.of());assertThat(links.useState(1)).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->links.requireUnreferenced(1));
    }
    @Test void hiddenOrInactiveReferencesStillPreventDeleteAndCompleteAbsenceAllowsIt() {
        var providers=Arrays.stream(Source.values()).map(s->source(s,s==Source.membership?Reference.PRESENT:Reference.ABSENT)).toList();
        assertThatThrownBy(()->new UserGroupLinks(providers).requireUnreferenced(1));
        new UserGroupLinks(Arrays.stream(Source.values()).map(s->source(s,Reference.ABSENT)).toList()).requireUnreferenced(1);
        assertThatThrownBy(()->new UserGroupLinks(Arrays.stream(Source.values()).map(s->source(s,Reference.UNKNOWN)).toList()).requireUnreferenced(1));
    }
}
