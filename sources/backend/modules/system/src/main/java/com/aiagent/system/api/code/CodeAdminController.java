// @feat FEAT-504
package com.aiagent.system.api.code;

import com.aiagent.system.api.code.request.CodeGroupSaveRequest;
import com.aiagent.system.api.code.request.CodeRefDefSaveRequest;
import com.aiagent.system.api.code.request.CodeSaveRequest;
import com.aiagent.api.message.RequestLocale;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.code.admin.CodeQueryService;
import com.aiagent.domain.code.admin.CodeAdminService;
import com.aiagent.domain.code.admin.CodeGroupParams;
import com.aiagent.domain.code.admin.CodeGroupRow;
import com.aiagent.domain.code.admin.CodeRefDefRow;
import com.aiagent.domain.code.admin.CodeRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code CODE_MGMT} — screen/admin/system/code/requirements.md. <b>그리드가 셋이라 경로가 여섯이다.</b>
 *
 * <p>마스터(그룹) · 하단(코드) · 정의 모달이 각각 조회와 저장을 갖는다. 하나로 합치지 않는
 * 것은 <b>저장 단위가 셋</b>이기 때문이다 — screen/admin/system/code/requirements.md 의 메시지 화면이 하단을 마스터의
 * {@code list} 컬럼으로 접은 것과 갈리는 자리이며, 여기 하단은 <b>자기 테이블에 붙어
 * 있다</b>(FK 도 걸린다).
 *
 * <p><b>{@code getRefDefList} 를 화면이 두 자리에서 부른다</b>(screen/admin/system/code/requirements.md) — 모달의 조회 버튼과
 * 하단 조회가 <b>병렬로</b> 부른다. 하단 응답에 정의를 얹지 않는 이유는 같은 목록이 두 경로로
 * 생기기 때문이고, 병렬인 이유는 컬럼 없이 행을 그리면 값이 갈 자리가 없어서다.
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md) — {@code get*} 는 {@code READ},
 * {@code save*} 는 {@code SAVE}. 선언에 또 적지 않는다: 같은 사실이 두 곳에 표현되면 시드와
 * 어긋난다.
 *
 * <p><b>{@code EXPORT} 에는 경로가 없다.</b> 엑셀 다운로드는 마스터에만 있고 그것은
 * <b>프론트 생성</b>이다(frontend/grid.md · playbook/excel-download.md 첫째) — 서버가 낼 것이 없다. 권한은 시드에 있고
 * 버튼은 {@code <DataGrid>} 가 그린다.
 */
@RestController
@RequestMapping("/api/system/service/code")
public class CodeAdminController {

    private final CodeQueryService queries;
    private final CodeAdminService admin;
    private final RequestLocale locale;

    public CodeAdminController(
            CodeQueryService queries, CodeAdminService admin, RequestLocale locale) {
        this.queries = queries;
        this.admin = admin;
        this.locale = locale;
    }

    // ── 마스터 ───────────────────────────────────────────────────────

    @Operation(
            summary = "코드 그룹 목록 조회",
            description = "코드 그룹을 페이지 단위로 반환한다. 그룹마다 코드 수가 함께 실리며 삭제 게이트가 그것을 읽는다.")
    @PostMapping("/getGroupList")
    public ApiResponse<PageResponse<CodeGroupRow>> getGroupList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getGroupList(request), TraceId.current());
    }

    @Operation(
            summary = "코드 그룹 저장",
            description = "추가·수정·삭제 행을 한 트랜잭션으로 반영하고 코드 캐시 버전을 올린다.")
    @PostMapping("/saveGroup")
    public ApiResponse<Void> saveGroup(@RequestBody @Valid CodeGroupSaveRequest request) {
        admin.saveGroup(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    // ── 하단 ─────────────────────────────────────────────────────────

    /**
     * <b>페이징하지 않는다</b>({@code mode: 'all'}).
     *
     * <p>참조값을 컬럼 필터로 거르기로 했으므로(screen/admin/system/code/requirements.md) 하단의 범위가 곧 전부여야 한다 —
     * 컬럼 필터는 현재 페이지 안에서만 걸리고(frontend/grid.md), 페이징하면 <i>어느 코드가 아직 안
     * 채워졌나</i> 의 답이 장마다 달라진다.
     */
    @Operation(
            summary = "코드 목록 조회",
            description = "선택한 그룹의 코드를 전량 반환한다. 세션 언어의 코드명과 참조값 맵이 함께 실린다.")
    @PostMapping("/getList")
    public ApiResponse<PageResponse<CodeRow>> getList(
            @RequestBody @Valid SearchRequest<CodeGroupParams> request) {
        return ApiResponse.success(queries.getList(request, locale.current()), TraceId.current());
    }

    /**
     * 저장 하나가 테이블 셋을 쓴다 — {@code SYS_CODE} · {@code SYS_MESSAGE} · {@code SYS_CODE_REF}
     * (screen/admin/system/code/requirements.md).
     *
     * <p><b>언어를 요청이 나르지 않는다.</b> 코드명이 쓰이는 행의 언어는 <i>지금 이 사람이
     * 보고 있는 언어</i> 이며 그것은 세션이 갖는다 — 본문에 두면 화면이 다른 언어의 값을
     * 덮어쓸 수 있고, 그 사고는 조용하다.
     */
    @Operation(
            summary = "코드 저장",
            description = "코드·코드명·참조값을 한 트랜잭션으로 반영한다. 코드명은 세션 언어의 메시지 행에 쓴다.")
    @PostMapping("/save")
    public ApiResponse<Void> save(@RequestBody @Valid CodeSaveRequest request) {
        admin.save(request.grpCd(), locale.current(), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    // ── 정의 모달 ────────────────────────────────────────────────────

    @Operation(
            summary = "참조 코드 정의 목록 조회",
            description = "그룹이 쓰는 참조 코드 정의를 전량 반환한다. 이 목록이 곧 코드 그리드의 동적 컬럼 집합이다.")
    @PostMapping("/getRefDefList")
    public ApiResponse<PageResponse<CodeRefDefRow>> getRefDefList(
            @RequestBody @Valid SearchRequest<CodeGroupParams> request) {
        return ApiResponse.success(queries.getRefDefList(request), TraceId.current());
    }

    @Operation(
            summary = "참조 코드 정의 저장",
            description = "정의를 추가·수정·삭제한다. 삭제는 그 정의로 입력된 참조값을 함께 지운다.")
    @PostMapping("/saveRefDef")
    public ApiResponse<Void> saveRefDef(@RequestBody @Valid CodeRefDefSaveRequest request) {
        admin.saveRefDef(request.grpCd(), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
