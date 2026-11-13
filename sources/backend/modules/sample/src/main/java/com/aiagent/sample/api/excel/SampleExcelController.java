// @feat FEAT-503
package com.aiagent.sample.api.excel;

import com.aiagent.sample.api.excel.request.SampleFlatItemSaveRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.sample.domain.SampleItemService;
import com.aiagent.sample.domain.SampleQueryService;
import com.aiagent.sample.domain.SmplOrderItemRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SMPL_EXCEL} &mdash; 68 &sect;8. <b>경로가 둘이고 탭도 둘인데, 탭마다 하나가
 * 아니다.</b>
 *
 * <p>두 탭이 <b>같은 둘을 함께</b> 쓴다 &mdash; 그것이 playbook/excel-upload.md 의 결정이다:
 *
 * <table><caption></caption>
 *   <tr><td>{@code getFlatItemList}</td>
 *       <td>업무 탭의 조회이자, 업로드 탭이 <b>파일을 읽은 직후 한 번</b> 부르는
 * 대조용 조회다(playbook/excel-upload.md &sect;3). 업로드 탭에는 조회 <b>버튼</b>이 없다
 *           (68 &sect;8.4) &mdash; 눌리면 읽어들인 412행이 통째로 날아간다</td></tr>
 *   <tr><td>{@code saveFlatItems}</td>
 *       <td>두 탭의 저장이 <b>같은 요청</b>이다. 업로드는 파일을 서버에 올리는 일이
 *           아니라 <b>브라우저가 읽어 그리드에 부은 행을 저장하는 일</b>이라, 새 경로를
 *           만들 근거가 없다 &mdash; 만들면 같은 표에 규칙이 둘 생기고 그 둘은 갈린다</td></tr>
 * </table>
 *
 * <h2>업로드 엔드포인트가 <b>없다</b></h2>
 *
 * <p>파일이 서버에 올라가지 않으므로(playbook/excel-upload.md &sect;1) 받을 곳이 없고 {@code SYS_FILE} 에도
 * 아무것도 남지 않는다. 이 컨트롤러에 {@code multipart} 가 하나도 없는 것이 그 결정의
 * 실물이다.
 *
 * <p>URL 은 여섯 화면이 {@code /api/sample/service} 하나를 나눠 쓴다(72 &sect;2.2) &mdash;
 * 애그리거트가 하나라서다. 패키지가 갈리는 것은 <b>인가 경계</b> 때문이며(68 &sect;10.2)
 * 그 둘은 다른 축이다.
 */
@RestController
@RequestMapping("/api/sample/service")
public class SampleExcelController {

    private final SampleQueryService queries;
    private final SampleItemService itemService;

    public SampleExcelController(SampleQueryService queries, SampleItemService itemService) {
        this.queries = queries;
        this.itemService = itemService;
    }

    @Operation(
            summary = "샘플 품목 평면 목록 조회",
            description = "주문을 가로질러 품목을 오프셋 페이징으로 반환한다. 엑셀 업로드 탭이 기존 행과 대조할 때도 같은 경로를 쓴다.")
    @PostMapping("/getFlatItemList")
    public ApiResponse<PageResponse<SmplOrderItemRow>> getFlatItemList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getFlatItemList(request), TraceId.current());
    }

    /**
     * &#9317;의 저장 &mdash; <b>업무 탭과 엑셀 업로드 탭이 함께 쓴다.</b>
     *
     * <p>대상을 {@code (ORD_NO, ITEM_CD)} 가 가리키며 서버가 {@code ORD_NO → ORD_ID} 를
     * 푼다(48 &sect;4.3). 하나라도 거절되면 <b>전부 거절</b>이다 &mdash; 일괄 저장이 한
     * 트랜잭션이라 부분 실패가 없다(68 &sect;8.7).
     */
    @Operation(
            summary = "샘플 품목 평면 저장",
            description = "주문번호와 품목코드로 대상을 가리켜 추가·수정·삭제를 한 트랜잭션으로 반영한다. 없는 주문번호나 겹친 품목코드가 있으면 전부 거절한다.")
    @PostMapping("/saveFlatItems")
    public ApiResponse<Void> saveFlatItems(@RequestBody @Valid SampleFlatItemSaveRequest request) {
        itemService.saveFlatItems(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }
}
