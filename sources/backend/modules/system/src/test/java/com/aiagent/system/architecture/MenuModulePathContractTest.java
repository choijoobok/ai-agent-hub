// @feat FEAT-503
package com.aiagent.system.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.aiagent.system.api.apicatalog.ApiCatalogController;
import com.aiagent.system.api.menu.MenuController;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** ARCH-022·ARCH-026 — 메뉴 관리와 API 카탈로그의 전용 구현은 system 모듈이 소유한다. */
class MenuModulePathContractTest {

    @Test
    @DisplayName("SLICE-503 메뉴·API 카탈로그·실패 폐쇄 인가 판정은 system 패키지에만 존재한다")
    void menuImplementationLivesOnlyInSystemModule() {
        assertPresent("com.aiagent.system.api.menu.MenuController");
        assertPresent("com.aiagent.system.api.apicatalog.ApiCatalogController");
        assertPresent("com.aiagent.system.domain.menu.MenuService");
        assertPresent("com.aiagent.system.domain.menu.MenuQueryService");
        assertPresent("com.aiagent.system.domain.apicatalog.ApiCatalogQueryService");
        assertPresent("com.aiagent.system.api.menu.request.MenuDeleteRequest");
        assertPresent("com.aiagent.system.api.menu.request.MenuNodeRequest");
        assertPresent("com.aiagent.system.api.menu.request.MenuSaveRequest");
        assertPresent("com.aiagent.system.api.menu.request.MenuTreeSaveRequest");
        assertPresent("com.aiagent.system.api.authz.AuthorizationInterceptor");

        assertMissing("com.aiagent.api.menu.MenuController");
        assertMissing("com.aiagent.api.apicatalog.ApiCatalogController");
        assertMissing("com.aiagent.domain.authz.menu.MenuService");
        assertMissing("com.aiagent.domain.authz.menu.MenuQueryService");
        assertMissing("com.aiagent.domain.authz.apicatalog.ApiCatalogQueryService");
        assertMissing("com.aiagent.api.menu.request.MenuDeleteRequest");
        assertMissing("com.aiagent.api.menu.request.MenuNodeRequest");
        assertMissing("com.aiagent.api.menu.request.MenuSaveRequest");
        assertMissing("com.aiagent.api.menu.request.MenuTreeSaveRequest");
        assertMissing("com.aiagent.api.authz.AuthorizationInterceptor");
    }

    @Test
    @DisplayName("TC-5035 메뉴·카탈로그 API는 system 접두어만 노출한다")
    void menuAndCatalogExposeOnlySystemEndpoints() throws NoSuchMethodException {
        assertThat(requestPath(MenuController.class)).isEqualTo("/api/system/service/menu");
        assertThat(requestPath(ApiCatalogController.class)).isEqualTo("/api/system/service/apiCatalog");

        assertThat(postPath(MenuController.class.getMethod("getTree"))).isEqualTo("/getTree");
        assertThat(postPath(MenuController.class.getMethod("saveNode", com.aiagent.system.api.menu.request.MenuNodeRequest.class)))
                .isEqualTo("/saveNode");
        assertThat(postPath(MenuController.class.getMethod("save", com.aiagent.system.api.menu.request.MenuSaveRequest.class)))
                .isEqualTo("/save");
        assertThat(postPath(MenuController.class.getMethod("saveTree", com.aiagent.system.api.menu.request.MenuTreeSaveRequest.class)))
                .isEqualTo("/saveTree");
        assertThat(postPath(MenuController.class.getMethod("deleteNode", com.aiagent.system.api.menu.request.MenuDeleteRequest.class)))
                .isEqualTo("/deleteNode");
        assertThat(postPath(ApiCatalogController.class.getMethod("getList", com.aiagent.core.search.SearchRequest.class)))
                .isEqualTo("/getList");
        assertThat(postPath(ApiCatalogController.class.getMethod("getMenuList", com.aiagent.core.search.SearchRequest.class)))
                .isEqualTo("/getMenuList");
    }

    private static void assertPresent(String className) {
        assertThatCode(() -> Class.forName(className)).doesNotThrowAnyException();
    }

    private static void assertMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static String requestPath(Class<?> controller) {
        return controller.getAnnotation(RequestMapping.class).value()[0];
    }

    private static String postPath(Method method) {
        return method.getAnnotation(PostMapping.class).value()[0];
    }
}
