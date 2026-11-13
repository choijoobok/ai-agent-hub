// @feat FEAT-503
package com.aiagent.system.api.menu;

import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.authz.menu.MenuUserRow;
import com.aiagent.system.api.menu.request.MenuDeleteRequest;
import com.aiagent.system.api.menu.request.MenuNodeRequest;
import com.aiagent.system.api.menu.request.MenuSaveRequest;
import com.aiagent.system.api.menu.request.MenuTreeSaveRequest;
import com.aiagent.system.domain.menu.MenuApiRow;
import com.aiagent.system.domain.menu.MenuCdParams;
import com.aiagent.system.domain.menu.MenuQueryService;
import com.aiagent.system.domain.menu.MenuRoleRow;
import com.aiagent.system.domain.menu.MenuService;
import com.aiagent.system.domain.menu.MenuTreeRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code MENU_TREE} — screen/menu/requirements.md. <b>쓰기 경로가 넷이다.</b>
 *
 * <p><b>저장 버튼 하나로 모을 수 없다.</b> 이 화면은 <b>통째로 즉시 저장</b>이며(screen/menu/requirements.md)
 * 그것은 위치 변경이 즉시일 수밖에 없기 때문이다 — 형제 전체가 다시 쓰이고
 * {@code @CustomLock(WAIT)} 로 직렬화된다(sys/menu.md). 한 그리드 안에서 일괄과 즉시를 섞으면
 * <b>드래그는 이미 저장됐는데 셀 편집은 아직인 상태</b>가 같은 화면에 있게 되고, 저장 버튼이
 * 무엇을 저장하는지 알 수 없다.
 *
 * <p>그래서 <b>연산마다 경로가 하나</b>다.
 *
 * <table>
 *   <tr><td>{@code saveNode}</td><td>신규 노드 — 모달이 받는 칸은 {@code MENU_CD} 하나</td></tr>
 *   <tr><td>{@code save}</td><td>속성 — URL · 아이콘 · 사용 여부. <b>셀 하나에 요청 하나</b></td></tr>
 * <tr><td>{@code saveTree}</td><td>위치 — 형제 목록을 통째로(sys/menu.md)</td></tr>
 *   <tr><td>{@code deleteNode}</td><td>삭제 — 게이트 넷</td></tr>
 * </table>
 *
 * <p><b>{@code save} 와 {@code saveTree} 를 합치지 않는다.</b> sys/menu.md 가 저장 DTO 에서
 * {@code UP_MENU_CD}·{@code SORT_NO} 를 <b>빼 버린 것</b>이 이 분리의 이유다 — 합치면 속성
 * 저장이 위치 필드를 받게 되고, <i>받지 않으므로 조작할 경로가 없다</i> 는 원칙 P1 의 장치가
 * 사라진다.
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md) — {@code get*} 는 {@code READ},
 * {@code save*}·{@code delete*} 는 {@code SAVE}. 선언에 또 적지 않는다.
 *
 * <p><b>{@code EXPORT} 에는 경로가 없다</b> — 엑셀은 <b>프론트 생성</b>이다(frontend/grid.md ·
 * playbook/excel-download.md 첫째 · screen/menu/requirements.md).
 */
@RestController
@RequestMapping("/api/system/service/menu")
public class MenuController {

    private final MenuQueryService queries;
    private final MenuService admin;
    private final RequestLocale locale;

    public MenuController(
            MenuQueryService queries, MenuService admin, RequestLocale locale) {
        this.queries = queries;
        this.admin = admin;
        this.locale = locale;
    }

    // ── 트리 ─────────────────────────────────────────────────────────

    /**
     * <b>페이징도 조건도 없다.</b>
     *
     * <p>트리를 페이징하면 부모 없는 자식 페이지가 생기고(screen/menu/requirements.md), 조건으로 거르면 부모가
     * 사라져 <b>트리가 아니게 된다</b> — 검색은 강조와 이동이며 클라이언트가 한다.
     * 그래서 요청 본문이 {@code SearchRequest} 조차 아니다: 받을 것이 없는 자리에 봉투를 두면
     * <i>조건을 보내면 걸릴 것</i> 이라는 기대를 만든다.
     */
    @Operation(
            summary = "메뉴 트리 조회",
            description = "메뉴 전건을 전위 순회 순서로 반환한다. 매핑 API·접근 가능 인원·매핑 역할이 함께 실리며 삭제 게이트가 그것을 읽는다.")
    @PostMapping("/getTree")
    public ApiResponse<PageResponse<MenuTreeRow>> getTree() {
        return ApiResponse.success(queries.getTree(locale.current()), TraceId.current());
    }

    /**
     * 신규 노드 — <b>부모는 선택 노드이고 순서는 형제의 맨 끝</b>이다(sys/menu.md).
     *
     * <p>모달이 받는 칸이 {@code MENU_CD} 하나인 것이 frontend/grid.md 의 <i>편집 폼 금지</i> 를 어기지
     * 않는 이유다(screen/menu/requirements.md) — 이것은 행을 편집하는 폼이 아니라
     * {@code createNode(upMenuCd, menuCd)} 라는 <b>한 연산의 인자</b>이며, frontend/grid.md 의 파라미터
     * 모달이다.
     */
    @Operation(
            summary = "메뉴 노드 추가",
            description = "선택 노드의 자식으로 메뉴를 만든다. 선택이 없으면 최상위이며 순서는 형제의 맨 끝이다.")
    @PostMapping("/saveNode")
    public ApiResponse<Void> saveNode(@RequestBody @Valid MenuNodeRequest request) {
        admin.createNode(request.upMenuCd(), request.menuCd());
        return ApiResponse.success(null, TraceId.current());
    }

    /**
     * 속성 — <b>셀 하나에 요청 하나</b>다(screen/menu/requirements.md).
     *
     * <p>대가는 요청 수이며 <b>메뉴는 드물게 바뀌는 마스터</b>라 받아들인다. 얻는 것은
     * 되돌릴 수 있는 편집과 없는 편집이 한 그리드에 섞이지 않는 것이다.
     *
     * <p><b>실패하면 셀이 되돌아간다</b> — 즉시 저장은 낙관적 반영이 아니다. 되돌리기 수단이
     * 없는 화면에서 화면과 DB 가 어긋나면 고칠 방법이 없다.
     */
    @Operation(
            summary = "메뉴 속성 저장",
            description = "URL·아이콘·사용 여부를 저장한다. 구조 노드의 사용 여부는 자손 전체에 같은 값을 쓴다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid MenuSaveRequest request) {
        admin.saveNode(
                request.menuCd(),
                request.menuUrl(),
                request.iconCd(),
                Boolean.TRUE.equals(request.useYn()));
        return ApiResponse.success(null, TraceId.current());
    }

    /**
     * 위치 — <b>형제 목록을 통째로 받는다</b>(sys/menu.md).
     *
     * <p>부모가 바뀌면 <b>두 부모</b>가 다시 쓰이며 <b>한 요청</b>에 담긴다 — 나눠 보내면 그
     * 사이에 <i>양쪽 어디에도 속하지 않는 노드</i> 를 갖는 순간이 생긴다.
     *
     * <p><b>집합 검증이 낙관적 덮어쓰기를 막는다.</b> 불일치면 거부하고 화면은 조회로
     * 복구한다 — 조용한 유실을 시끄러운 실패로 바꾼 것이다.
     */
    @Operation(
            summary = "메뉴 트리 저장",
            description = "드래그 결과를 형제 목록으로 받아 순서를 다시 쓴다. 보낸 목록이 현재 자식 집합과 다르면 거부한다.")
    @PostMapping("/saveTree")
    public ApiResponse<Void> saveTree(@RequestBody @Valid MenuTreeSaveRequest request) {
        admin.saveTree(request.groups());
        return ApiResponse.success(null, TraceId.current());
    }

    @Operation(
            summary = "메뉴 삭제",
            description = "자식·매핑 API·매핑 역할·개인 예외가 모두 없을 때만 지운다. 즐겨찾기는 함께 지운다.")
    @PostMapping("/deleteNode")
    public ApiResponse<Void> deleteNode(@RequestBody @Valid MenuDeleteRequest request) {
        admin.deleteNode(request.menuCd());
        return ApiResponse.success(null, TraceId.current());
    }

    // ── 하단 탭 셋 (screen/menu/requirements.md) ────────────────────────────────────────

    /**
     * <b>탭 셋이 경로 셋이다.</b> screen/menu/requirements.md 에서는 탭 셋이 pane 하나가 됐지만 여기서는
     * <b>셋이 서로 다른 것을 본다</b> — 합치면 한 요청이 세 질문에 답하게 되고, 탭 하나를
     * 여는 데 나머지 둘의 계산이 딸려 온다.
     */
    @Operation(
            summary = "메뉴 매핑 API 조회",
            description = "이 메뉴가 쓰는 API 를 전량 반환한다. 0 건이면 이 화면의 기능을 아무도 쓸 수 없다.")
    @PostMapping("/getApiList")
    public ApiResponse<PageResponse<MenuApiRow>> getApiList(
            @RequestBody @Valid SearchRequest<MenuCdParams> request) {
        return ApiResponse.success(
                queries.getApiList(request.params().menuCd()), TraceId.current());
    }

    @Operation(
            summary = "메뉴 사용 가능 사용자 조회",
            description = "이 메뉴에 닿는 사용자를 경유(역할·개인 예외)마다 한 행으로 반환한다. 만료된 부여도 상태와 함께 낸다.")
    @PostMapping("/getAccessibleUsers")
    public ApiResponse<PageResponse<MenuUserRow>> getAccessibleUsers(
            @RequestBody @Valid SearchRequest<MenuCdParams> request) {
        return ApiResponse.success(
                queries.getAccessibleUsers(request.params().menuCd()), TraceId.current());
    }

    @Operation(
            summary = "메뉴 매핑 역할 조회",
            description = "이 메뉴에 권한을 준 역할을 전량 반환한다. 사용 안 함인 역할도 권한 행이 살아 있으므로 함께 낸다.")
    @PostMapping("/getRoleList")
    public ApiResponse<PageResponse<MenuRoleRow>> getRoleList(
            @RequestBody @Valid SearchRequest<MenuCdParams> request) {
        return ApiResponse.success(
                queries.getRoleList(request.params().menuCd()), TraceId.current());
    }
}
