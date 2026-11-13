// @feat FEAT-503
package com.aiagent.sample.api.mdtabs;

import com.aiagent.sample.api.mdtabs.request.SampleTagSaveRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.sample.domain.OrdIdParams;
import com.aiagent.sample.domain.SampleTagService;
import com.aiagent.sample.domain.SampleQueryService;
import com.aiagent.sample.domain.SmplTagRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SMPL_MD_TABS} &mdash; 68 &sect;7. <b>새 경로가 둘뿐이다.</b>
 *
 * <p>&#9315;는 하나도 열지 않았고(&#9314;의 부분집합이라) 이 화면은 <b>태그 탭 하나만큼</b>
 * 연다. 마스터와 품목 탭은 &#9314;의 경로를 그대로 부르며, 그 사실이
 * {@code endpoints.ts} 에 적혀 있어 007 시드가 <b>같은 URL 을 두 메뉴에 매핑</b>한다.
 *
 * <p><b>같은 URL 을 두 컨트롤러가 매핑하면 기동이 죽는다.</b> 그것이 여기 {@code getOrderList}
 * 가 없는 이유이며 &mdash; 화면마다 패키지를 가르는 것은 <b>인가 경계</b>를 위한 것이지
 * (68 &sect;10.2) URL 을 가르기 위한 것이 아니다. 여섯 화면의 애그리거트는 하나라
 * {@code /api/sample/service} 을 나눠 쓴다(72 &sect;2.2).
 *
 * <h2>&#9316;가 {@code SMPL_ORDER_TAG} 의 <b>둘째 소비자</b>다</h2>
 *
 * <p>&#9312;은 같은 데이터를 {@code list} 컬럼 하나로 나르고({@code listModal} 로 편집),
 * 여기서는 <b>행으로</b> 본다. 48 &sect;4.1 의 <i>"배열은 화면의 어휘이고 저장은 자식
 * 테이블"</i> 이 두 화면을 나란히 놓아야 이해되는 결정이며, 그것이 이 탭의 존재 이유다.
 */
@RestController
@RequestMapping("/api/sample/service")
public class SampleMdTabsController {

    private final SampleQueryService queries;
    private final SampleTagService tagService;

    public SampleMdTabsController(SampleQueryService queries, SampleTagService tagService) {
        this.queries = queries;
        this.tagService = tagService;
    }

    /**
     * 태그 탭의 목록 &mdash; 대상이 <b>조회조건이 아니라 파라미터</b>다(72 &sect;3.1.1).
     *
     * <p>{@code getItemList} 와 같은 모양이다. 탭 둘이 같은 대상을 각자 묻는 것이
     * <b>지연 로딩</b>의 값이며(68 &sect;7.6), 마스터를 클릭할 때마다 둘 다 나가면
     * <i>목록을 훑는 동작 자체가 부하</i>가 된다.
     */
    @Operation(
            summary = "샘플 주문 태그 조회",
            description = "고른 주문 하나의 태그를 정렬순서대로 반환한다. ①의 list 컬럼과 같은 데이터를 행으로 본다.")
    @PostMapping("/getTagList")
    public ApiResponse<PageResponse<SmplTagRow>> getTagList(
            @RequestBody @Valid SearchRequest<OrdIdParams> request) {
        long ordId = Long.parseLong(request.params().ordId());
        return ApiResponse.success(queries.getTagList(ordId), TraceId.current());
    }

    /**
     * 태그 탭의 저장 &mdash; 68 &sect;7.4.
     *
     * <p><b>&#9312;의 저장과 갈래가 다르다.</b> 저쪽은 배열을 통째로 받아 전량 교체하고
     * 여기는 행마다 {@code rowState} 를 본다 &mdash; 이유는 {@code SampleTagService.saveTags}
     * 가 적어 두었다.
     */
    @Operation(
            summary = "샘플 주문 태그 저장",
            description = "고른 주문 하나의 태그를 한 트랜잭션으로 반영한다. 신규 행의 순번은 서버가 정하고 정렬순서가 겹치면 거부한다.")
    @PostMapping("/saveTags")
    public ApiResponse<Void> saveTags(@RequestBody @Valid SampleTagSaveRequest request) {
        tagService.saveTags(Long.parseLong(request.ordId()), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
