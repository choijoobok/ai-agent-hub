// @feat FEAT-504
package com.aiagent.system.api.code;

import com.aiagent.system.api.code.request.CodeListRequest;
import com.aiagent.system.api.code.response.CodeListResponse;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.domain.code.service.CodeService;
import com.aiagent.domain.code.service.CodeRefQueryService;
import com.aiagent.system.api.code.request.CodeRefListRequest;
import com.aiagent.system.api.code.response.CodeRefListResponse;
import com.aiagent.domain.message.entity.SysCacheVersion;
import com.aiagent.domain.message.service.CacheVersionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 콤보가 읽는 공통코드 — sys/code.md · backend/security.md.
 *
 * <h2>{@code common} 계층이다</h2>
 *
 * <p>{@link com.aiagent.api.authz.ApiLayer#COMMON} 의 Javadoc 이 <i>"공통코드
 * 조회"</i> 를 첫 예시로 든 그 자리다. 판별 신호도 그대로다 — 이것을 {@code service} 아래
 * 두면 {@code SYS_MENU_API} 에 <b>코드 콤보를 쓰는 메뉴를 전부 나열</b>하게 되고, 화면이
 * 하나 늘 때마다 그 표를 고쳐야 한다. 콤보 하나 때문에 403 이 나는 화면은 <b>원인이 화면에
 * 드러나지 않는</b> 종류다.
 *
 * <p><b>{@code CodeAdminController} 와 갈린다.</b> 저쪽은 CODE_MGMT 화면의 것이고
 * ({@code /api/system/service/code/**}) 관리자만 부른다. 여기는 <b>로그인한 누구나</b> 부르며
 * 돌려주는 것도 다르다 — 저쪽은 관리 그리드의 행 전부, 여기는 <b>콤보 후보인 코드값</b>뿐이다.
 *
 * <h2>전체 그룹과 참조 조건 조회를 구분한다</h2>
 *
 * <p>그룹 하나짜리 {@code getOne} 을 함께 두지 않았다. 목록이 그것을 이미 할 수 있고,
 * <b>부르는 곳 없는 엔드포인트를 먼저 열면</b> 없는 기능이 API 로 먼저 생긴다
 * ({@code FavoriteController} 의 순서 재배치와 같은 태도다).
 */
@RestController
public class CodeController {

    private final CodeService codes;
    private final CacheVersionService versions;
    private final CodeRefQueryService references;

    public CodeController(CodeService codes, CacheVersionService versions,CodeRefQueryService references) {
        this.codes = codes;
        this.versions = versions;
        this.references = references;
    }

    /**
     * <b>버전을 먼저 읽는다.</b> 코드를 뜬 뒤에 읽으면 그 사이의 저장이 <i>새 데이터에 옛
     * 버전</i> 이 아니라 <b>옛 데이터에 새 버전</b>을 찍는다 — 앞엣것은 다음 로그인이
     * 바로잡지만 뒤엣것은 <b>영원히 낡은 채로 최신인 척</b> 한다.
     */
    @PostMapping("/api/system/common/code/getList")
    public ApiResponse<CodeListResponse> getList(@Valid @RequestBody CodeListRequest request) {
        long version = versions.current(SysCacheVersion.CODE);
        return ApiResponse.success(
                new CodeListResponse(version, codes.codesOf(request.grpCds())),
                TraceId.current());
    }

    @PostMapping("/api/system/common/code/getListByRef")
    public ApiResponse<CodeRefListResponse> getListByRef(@Valid @RequestBody CodeRefListRequest request) {
        long version=versions.current(SysCacheVersion.CODE);
        return ApiResponse.success(new CodeRefListResponse(version,references.codesByRef(request.grpCd(),request.refCd(),request.refVal())),TraceId.current());
    }
}
