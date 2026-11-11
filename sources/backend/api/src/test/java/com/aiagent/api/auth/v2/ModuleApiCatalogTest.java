// @feat FEAT-301
package com.aiagent.api.auth.v2;

import static org.assertj.core.api.Assertions.*;
import com.aiagent.core.security.PrincipalRef;
import com.aiagent.core.security.AuthDomain;
import com.aiagent.core.security.SessionPersistencePort.AuthLevel;
import com.aiagent.core.security.RequestPersistenceState.Capability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleApiCatalogTest {
    @Test @DisplayName("TC-30129 publicREAD문자열이인증수준metadata를익명허용으로바꿀수없다")
    void rejectsAuthenticatedOnlyPublicRead(){
        var operation=new ModuleApiCatalog.Operation("sensitive","GET","/api/system/public/user/getSensitive",ModuleApiCatalog.Domain.ADMIN,Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,null);
        assertThatThrownBy(()->new ModuleApiCatalog(List.of(operation),false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("TC-30129 관측handler누락/오연결/중복/넓은mapping은기동거부한다")
    void validatesObservedHandlerIdentity(){
        var op=operation("/api/system/service/user/getList",ModuleApiCatalog.Domain.ADMIN);var catalog=new ModuleApiCatalog(List.of(op),false);
        var expected=java.util.Map.of(op.id(),"UserController#getList");var actual=new ModuleApiCatalog.Handler(op.method(),op.path(),"UserController#getList");
        catalog.verifyHandlers(expected,List.of(actual));
        for(var observed:List.of(List.<ModuleApiCatalog.Handler>of(),List.of(new ModuleApiCatalog.Handler(op.method(),op.path(),"OtherController#write")),List.of(actual,actual),List.of(new ModuleApiCatalog.Handler("POST","/api/system/service/user/**",actual.identity()))))
            assertThatThrownBy(()->catalog.verifyHandlers(expected,observed)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->catalog.verifyHandlers(java.util.Map.of(),List.of(actual))).isInstanceOf(IllegalArgumentException.class);
    }
    private ModuleApiCatalog.Operation operation(String path,ModuleApiCatalog.Domain domain) {
        return new ModuleApiCatalog.Operation("handler-1","POST",path,domain,Set.of(AuthLevel.AUTHENTICATED),
                "READ",false,Capability.READ,null,null);
    }
    @Test @DisplayName("TC-30129 모듈 우선 정확 경로만 수락하고 구 경로 별칭은 없다")
    void acceptsExactModuleFirstOnly() {
        var catalog=new ModuleApiCatalog(List.of(operation("/api/system/service/user/getList",ModuleApiCatalog.Domain.ADMIN)),false);
        assertThat(catalog.resolve("POST","/api/system/service/user/getList")).isNotNull();
        for(String path:List.of("/api/service/system/user/getList","/api/system/public/user/getList","/api/sys/service/user/getList",
                "/api/system/service/user/getList/extra","/api/system/service/user/getList/","/api/system/service/user/getList%2f"))
            assertThat(catalog.resolve("POST",path)).as(path).isNull();
        assertThat(catalog.resolve("GET","/api/system/service/user/getList")).isNull();
    }
    @Test @DisplayName("TC-30129 모듈·영역·계층·와일드카드 오조립은 기동 거부")
    void rejectsWrongNamespaceAndBroadRoutes() {
        for(String path:List.of("/api/portal/service/user/getList","/api/unknown/service/user/getList","/api/system/search/user/getList",
                "/api/system/service/user/*","/api/system/service/user/{id}","/api/public/system/auth/login"))
            assertThatThrownBy(()->new ModuleApiCatalog(List.of(operation(path,ModuleApiCatalog.Domain.ADMIN)),false)).as(path).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("TC-30129 integration은 NONE 원천 검증 콜백이 필수이며 익명 브라우저 예외가 아니다")
    void sourceRequiresVerifier() {
        assertThatThrownBy(()->new ModuleApiCatalog(List.of(operation("/api/integration/public/user/changed",ModuleApiCatalog.Domain.NONE)),false))
                .isInstanceOf(IllegalArgumentException.class);
        var source=new ModuleApiCatalog.Operation("source","POST","/api/integration/public/user/changed",ModuleApiCatalog.Domain.NONE,
                Set.of(),null,false,Capability.READ,request->{},null);
        assertThat(new ModuleApiCatalog(List.of(source),false).resolve("POST",source.path())).isNotNull();
    }
    @Test @DisplayName("TC-30129 sample 도메인 생략은 local에서만 존재한다")
    void sampleOnlyLocal() {
        var sample=operation("/api/sample/service/getList",ModuleApiCatalog.Domain.ADMIN);
        assertThatThrownBy(()->new ModuleApiCatalog(List.of(sample),false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ModuleApiCatalog(List.of(sample),true).resolve("POST",sample.path())).isNotNull();
    }
    @Test @DisplayName("TC-30129 등록 단일 jobId 템플릿과 ADMIN 본인 소유만 수락한다")
    void exactProgressTemplateAndOwner() {
        String path="/api/system/common/progress/getProgress/{jobId}";
        var progress=new ModuleApiCatalog.Operation("progress","GET",path,ModuleApiCatalog.Domain.ADMIN,
                Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,(principal,job)->principal.subjectId().equals("owner")&&job.equals("123e4567-e89b-12d3-a456-426614174000"));
        var catalog=new ModuleApiCatalog(List.of(progress),false);
        var match=catalog.resolve("GET",path.replace("{jobId}","123e4567-e89b-12d3-a456-426614174000"));
        assertThat(match).isNotNull();
        assertThat(match.ownsJob(new PrincipalRef(AuthDomain.ADMIN,"owner"))).isTrue();
        assertThat(match.ownsJob(new PrincipalRef(AuthDomain.ADMIN,"other"))).isFalse();
        assertThat(match.ownsJob(new PrincipalRef(AuthDomain.PORTAL,"owner"))).isFalse();
        assertThat(catalog.resolve("GET",path.replace("{jobId}","bad"))).isNotNull();
        assertThat(catalog.resolve("GET",path.replace("{jobId}","bad")).ownsJob(new PrincipalRef(AuthDomain.ADMIN,"owner"))).isFalse();
        assertThat(catalog.resolve("GET",path.replace("{jobId}","a/b"))).isNull();
    }
    @Test @DisplayName("TC-30129 등록뒤 원본/반환 인증수준 집합 변조로 익명허용을 추가할 수 없다")
    void registeredLevelsAreImmutable() {
        var levels=new java.util.HashSet<>(Set.of(AuthLevel.AUTHENTICATED));
        var op=new ModuleApiCatalog.Operation("immutable","GET","/api/system/common/auth/getMe",ModuleApiCatalog.Domain.ADMIN,
                levels,null,true,Capability.READ,null,null);
        var catalog=new ModuleApiCatalog(List.of(op),false);levels.add(AuthLevel.ANONYMOUS);
        assertThat(catalog.resolve("GET",op.path()).operation().levels()).containsExactly(AuthLevel.AUTHENTICATED);
        assertThatThrownBy(()->op.levels().add(AuthLevel.ANONYMOUS)).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test @DisplayName("TC-30129 progress literal이 단일 템플릿 소유 검사를 가로챌 수 없다")
    void literalCannotShadowProgress() {
        var template=new ModuleApiCatalog.Operation("progress","GET","/api/system/common/progress/getProgress/{jobId}",ModuleApiCatalog.Domain.ADMIN,
                Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,(principal,job)->false);
        var literal=new ModuleApiCatalog.Operation("literal","GET","/api/system/common/progress/getProgress/123e4567-e89b-12d3-a456-426614174000",ModuleApiCatalog.Domain.ADMIN,
                Set.of(AuthLevel.AUTHENTICATED),null,true,Capability.READ,null,null);
        for(var entries:List.of(List.of(template,literal),List.of(literal,template)))
            assertThatThrownBy(()->new ModuleApiCatalog(entries,false)).isInstanceOf(IllegalArgumentException.class);
    }
}
