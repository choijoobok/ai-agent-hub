// @feat FEAT-006
package com.aiagent.admin.group;

import static org.assertj.core.api.Assertions.*;
import static com.aiagent.admin.group.UserGroupLinkSource.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

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
    @Test void confirmedUninstalledOwnersAllowDeletionWithoutPretendingDetailIsConnected() {
        var links=new UserGroupLinks(List.of(),()->Set.of(Source.values()));
        assertThat(links.useState(1)).isEqualTo("UNUSED");
        assertThat(links.read(Source.membership,1).state()).isEqualTo(State.NOT_CONNECTED);
        links.requireUnreferenced(1);
    }
    @Test void failedStorageOrInstalledInspectorNeverFallsBackToAbsent() {
        var failedStorage=new UserGroupLinks(List.of(),()->{throw new DataAccessResourceFailureException("시험용 저장소 장애");});
        assertThat(failedStorage.useState(1)).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->failedStorage.requireUnreferenced(1));
        var failedOwner=new UserGroupLinkSource() {
            public Source source(){return Source.membership;}
            public Projection read(long id){throw new AssertionError("참조 검사에서 상세 목록을 조회하면 안 된다");}
            public Reference references(long id){throw new DataAccessResourceFailureException("시험용 참조 조회 장애");}
        };
        var links=new UserGroupLinks(List.of(failedOwner),()->Set.of(Source.values()));
        assertThat(links.useState(1)).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->links.requireUnreferenced(1));
    }
}
