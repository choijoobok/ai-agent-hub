// @feat FEAT-305
package com.aiagent.portal.change;

import static org.assertj.core.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ExternalUserChangeTest {
    private static final String BODY="{\"schemaVersion\":1,\"sourceEventId\":\"event-1\",\"subject\":\"employee-1\",\"kind\":\"RETIRED\",\"occurredAt\":\"2026-11-14T01:00:00Z\",\"sourceVersion\":\"opaque-10\"}";
    @Test void exactBoundedMessageAndStableBusinessDigest() {
        var event=ExternalUserChange.parse(BODY.getBytes(StandardCharsets.UTF_8));
        assertThat(event.kind().name()).isEqualTo("RETIRED");
        assertThat(event.businessDigest()).isEqualTo(ExternalUserChange.parse((" \n"+BODY).getBytes(StandardCharsets.UTF_8)).businessDigest());
        assertThat(event.eventKey("source","epoch-1")).isNotEqualTo(event.eventKey("source","epoch-2"));
        assertThat(ExternalUserChange.digest("ab","c")).isNotEqualTo(ExternalUserChange.digest("a","bc"));
        assertThat(event.toString()).doesNotContain("employee-1");
    }
    @Test void rejectsUnknownFieldsDuplicateKeysAndUnapprovedKinds() {
        for(String input:new String[]{BODY.replace("\"RETIRED\"","\"HIRED\""),BODY.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"sourceKey\":\"spoof\""),BODY.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"),BODY.replace("\"schemaVersion\":1","\"schemaVersion\":1.0"),BODY.replace("employee-1"," "),BODY+"{}"})
            assertThatThrownBy(()->ExternalUserChange.parse(input.getBytes(StandardCharsets.UTF_8))).isInstanceOf(ChangeFailure.class);
    }
    @Test void missingCustomerVerifierCannotAcceptEvenWellFormedSourceInput() {
        var changes=new SourceChangeService(java.util.List.of(),null,null,null,null,null,new org.springframework.mock.env.MockEnvironment(),org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        assertThatThrownBy(()->changes.verify("POST",SourceChangeService.PATH,java.util.Map.of("x-aihub-source",java.util.List.of("test-hr")),BODY.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ChangeFailure.class,failure->assertThat(failure.status()).isEqualTo(503));
        assertThat(changes.rejectedEnvelopes()).isEqualTo(1);
    }
}
