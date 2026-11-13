// @feat FEAT-510
package com.aiagent.system.api.userstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class UserStateMigrationContractTest {

    private static final String FAVORITE = "com.aiagent.system.api.favorite.FavoriteController";
    private static final String GRID_PREF = "com.aiagent.system.api.gridpref.GridPrefController";
    private static final String SEARCH_COND = "com.aiagent.system.api.searchcond.SearchCondController";
    private static final String MENU_STAT = "com.aiagent.system.api.menustat.MenuStatController";

    @Test
    @DisplayName("SLICE-515 사용자 상태 API와 요청 형식은 system 패키지에만 존재한다")
    void userStateImplementationLivesOnlyInSystemModule() {
        assertPresent(FAVORITE);
        assertPresent(GRID_PREF);
        assertPresent(SEARCH_COND);
        assertPresent("com.aiagent.system.api.favorite.request.FavoriteMenuRequest");
        assertPresent("com.aiagent.system.api.gridpref.request.GridPrefSaveRequest");
        assertPresent("com.aiagent.system.api.gridpref.request.GridPrefResetRequest");
        assertPresent("com.aiagent.system.api.searchcond.request.SearchCondSaveRequest");
        assertPresent("com.aiagent.system.api.searchcond.request.SearchCondDeleteRequest");
        assertPresent(MENU_STAT);
        assertPresent("com.aiagent.system.api.menustat.request.MenuSelectRequest");

        assertMissing("com.aiagent.api.favorite.FavoriteController");
        assertMissing("com.aiagent.api.gridpref.GridPrefController");
        assertMissing("com.aiagent.api.searchcond.SearchCondController");
        assertMissing("com.aiagent.api.favorite.request.FavoriteMenuRequest");
        assertMissing("com.aiagent.api.gridpref.request.GridPrefSaveRequest");
        assertMissing("com.aiagent.api.gridpref.request.GridPrefResetRequest");
        assertMissing("com.aiagent.api.searchcond.request.SearchCondSaveRequest");
        assertMissing("com.aiagent.api.searchcond.request.SearchCondDeleteRequest");
        assertMissing("com.aiagent.api.menustat.MenuStatController");
        assertMissing("com.aiagent.api.menustat.request.MenuSelectRequest");
    }

    @Test
    @DisplayName("TC-5101·TC-5104 사용자 상태 API는 system 공통 경로 하나만 사용한다")
    void userStateApisUseOnlyTheSystemPath() throws Exception {
        assertGet(FAVORITE, "getList", "/api/system/common/favorite/getList");
        assertPost(FAVORITE, "add", "/api/system/common/favorite/save");
        assertPost(FAVORITE, "remove", "/api/system/common/favorite/delete");
        assertGet(GRID_PREF, "getOne", "/api/system/common/gridPref/getOne");
        assertPost(GRID_PREF, "save", "/api/system/common/gridPref/save");
        assertPost(GRID_PREF, "reset", "/api/system/common/gridPref/delete");
        assertGet(SEARCH_COND, "getList", "/api/system/common/searchCond/getList");
        assertPost(SEARCH_COND, "save", "/api/system/common/searchCond/save");
        assertPost(SEARCH_COND, "delete", "/api/system/common/searchCond/delete");
        assertPost(MENU_STAT, "select", "/api/system/common/menu/saveSelect");
        assertGet(MENU_STAT, "frequent", "/api/system/common/menu/getFrequent");
    }

    @Test
    @DisplayName("TC-5104 요청 형식에는 타 사용자 상태를 지정할 userId 자리가 없다")
    void userStateRequestsDoNotAcceptAnOwner() throws Exception {
        assertNoUserId("com.aiagent.system.api.favorite.request.FavoriteMenuRequest");
        assertNoUserId("com.aiagent.system.api.gridpref.request.GridPrefSaveRequest");
        assertNoUserId("com.aiagent.system.api.gridpref.request.GridPrefResetRequest");
        assertNoUserId("com.aiagent.system.api.searchcond.request.SearchCondSaveRequest");
        assertNoUserId("com.aiagent.system.api.searchcond.request.SearchCondDeleteRequest");
        assertNoUserId("com.aiagent.system.api.menustat.request.MenuSelectRequest");
    }

    private static void assertGet(String className, String methodName, String expectedPath)
            throws Exception {
        Method method = method(Class.forName(className), methodName);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expectedPath);
    }

    private static void assertPost(String className, String methodName, String expectedPath)
            throws Exception {
        Method method = method(Class.forName(className), methodName);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedPath);
    }

    private static Method method(Class<?> type, String methodName) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static void assertPresent(String className) {
        assertThatCode(() -> Class.forName(className)).doesNotThrowAnyException();
    }

    private static void assertMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static void assertNoUserId(String className) throws Exception {
        assertThat(Class.forName(className).getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("userId");
    }
}
