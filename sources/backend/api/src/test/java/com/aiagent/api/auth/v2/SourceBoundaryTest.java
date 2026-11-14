// @feat FEAT-305
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aiagent.api.auth.boundary.*;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;

class SourceBoundaryTest {
    private static final String PATH="/api/integration/public/identity/receiveChange";
    private final BoundaryResponseWriter responses=mock(BoundaryResponseWriter.class);
    private SessionV2Filter filter(ModuleApiCatalog.SourceVerifier verifier) {
        var operation=new ModuleApiCatalog.Operation("API-307","POST",PATH,ModuleApiCatalog.Domain.NONE,Set.of(),null,false,Capability.READ,verifier,null);
        return new SessionV2Filter(mock(SessionRuntime.class),new ModuleApiCatalog(List.of(operation),false),null,Map.of(),responses);
    }
    @Test void acceptanceIsOnlyInsideVerifiedExactSynchronousRequest()throws Exception {
        var request=new MockHttpServletRequest("POST",PATH);var response=new MockHttpServletResponse();
        filter(r->assertThat(SessionV2Filter.sourceVerified(r)).isFalse()).doFilter(request,response,(req,res)->{
            var accepted=(HttpServletRequest)req;
            assertThat(SessionV2Filter.sourceVerified(accepted)).isTrue();
            request.setRequestURI(PATH+"/other");assertThat(SessionV2Filter.sourceVerified(accepted)).isFalse();request.setRequestURI(PATH);
            var other=java.util.concurrent.CompletableFuture.supplyAsync(()->SessionV2Filter.sourceVerified(accepted));assertThat(other.join()).isFalse();
        });
        assertThat(SessionV2Filter.sourceVerified(request)).isFalse();verifyNoInteractions(responses);
        var unknown=new MockHttpServletRequest("POST",PATH+"/other");
        filter(r->fail("미등록 경로 원천 검증 호출")).doFilter(unknown,response,(a,b)->fail("미등록 경로 통과"));
        verify(responses).denied(response,403);
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,503})
    void sourceFailuresRetainTheirSafeStatusAndNeverPublishAcceptance(int status)throws Exception {
        var request=new MockHttpServletRequest("POST",PATH);var response=new MockHttpServletResponse();
        filter(r->{throw new AuthBoundaryAssembly.Rejected(status);}).doFilter(request,response,(a,b)->fail("거부된 원천 통과"));
        assertThat(SessionV2Filter.sourceVerified(request)).isFalse();verify(responses).denied(eq(response),eq(status),any(Runnable.class));
    }
    @Test void oversizedEnvelopeIsClientErrorBeforeSourceOrBusinessUse()throws Exception {
        var request=new MockHttpServletRequest("POST",PATH);var response=new MockHttpServletResponse();request.setContent(new byte[1_048_577]);
        filter(r->fail("너무 큰 요청의 원천 검증 호출")).doFilter(request,response,(a,b)->fail("너무 큰 요청의 업무 효과"));
        verify(responses).denied(eq(response),eq(400),any(Runnable.class));
    }
}
