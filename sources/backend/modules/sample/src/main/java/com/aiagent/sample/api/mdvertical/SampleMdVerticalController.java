// @feat FEAT-503
package com.aiagent.sample.api.mdvertical;

import com.aiagent.sample.api.mdvertical.request.SampleExcelPageRequest;
import com.aiagent.sample.api.mdvertical.request.SampleItemRecalcRequest;
import com.aiagent.sample.api.mdvertical.request.SampleItemSaveRequest;
import com.aiagent.sample.api.mdvertical.request.SampleOrderSaveRequest;
import com.aiagent.sample.api.mdvertical.request.SampleShipRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.domain.progress.ProgressHelper;
import com.aiagent.sample.domain.SampleItemService;
import com.aiagent.sample.domain.SampleOrderService;
import com.aiagent.sample.domain.SampleExcelService;
import com.aiagent.sample.domain.OrdIdParams;
import com.aiagent.sample.domain.SampleQueryService;
import com.aiagent.sample.domain.SmplOrderItemRow;
import com.aiagent.sample.domain.SmplOrderRow;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code SMPL_MD_VERT} — playbook/INDEX.md. <b>참조 구현이지 업무 화면이 아니다.</b>
 *
 * <h2>패키지가 화면마다 갈리고, URL 의 도메인 마디는 하나다</h2>
 *
 * <p>playbook/INDEX.md 이 {@code api/sample/{singleoffset,…}} 을 요구한다. 화면 여섯이 한 컨트롤러에
 * 모이면 <b>인가 경계가 사라진다</b> — {@code generateApiSeed} 는 화면 폴더의
 * {@code endpoints.ts} 를 읽어 {@code SYS_MENU_API} 를 만들므로(backend/api.md), 한 자리에 둘을
 * 두면 <b>두 화면의 엔드포인트가 서로에게 매핑</b>된다(playbook/INDEX.md).
 *
 * <p><b>그런데 URL 은 여섯이 {@code /api/sample/service} 하나를 나눠 쓴다.</b> backend/api.md 의
 * 도메인 마디는 <b>애그리거트 이름</b>이고 여섯 화면의 애그리거트는 하나다 — 화면마다
 * 마디를 가르면 같은 {@code getOrderList} 가 URL 을 여섯 벌 갖는다. 클래스 이름이
 * {@code SampleController} 가 아닌 것도 여기서 온다: 패키지가 갈려도 <b>스프링 빈 이름은
 * 단순명</b>이라 둘째 화면이 서는 순간 기동이 이름 충돌로 죽는다.
 *
 * <p>액션은 <b>경로 접두가 정한다</b>(backend/api.md) — {@code get*} 은 {@code READ},
 * {@code save*} 는 {@code SAVE}, {@code export*} 는 {@code EXPORT}, 나머지는
 * {@code EXECUTE}. 선언에 또 적지 않는다.
 *
 * <h2>엑셀 셋이 <b>봉투를 쓰지 않는다</b></h2>
 *
 * <p>backend/api.md 의 {@code ApiResponse} 는 <i>알맹이를 감싸는</i> 규약인데 파일 응답에는
 * 감쌀 알맹이가 없다 — <b>통째로 파일</b>이다. 실패할 때만 봉투가 나가며, 프론트가 그것을
 * 되읽는다({@code shared/api/download.ts}).
 *
 * <h2>다운로드 이력이 <b>저절로 남는다</b></h2>
 *
 * <p>playbook/excel-download.md 가 프론트 생성에만 {@code POST /api/system/common/excel/saveLog} 를 둔 이유가 여기서
 * 보인다: 이 경로는 <b>요청이 도착하는 것 자체가 기록</b>이다(사용 이력 인터셉터가 모든
 * {@code /api/**} 를 남긴다). 프론트가 이력을 또 부르면 <b>한 번 내려받은 것이 두 행</b>이
 * 된다.
 */
@RestController
@RequestMapping("/api/sample/service")
public class SampleMdVerticalController {

    private final SampleQueryService queries;
    private final SampleOrderService orderService;
    private final SampleItemService itemService;
    private final SampleExcelService excel;
    private final ProgressHelper progress;

    public SampleMdVerticalController(
            SampleQueryService queries,
            SampleOrderService orderService,
            SampleItemService itemService,
            SampleExcelService excel,
            ProgressHelper progress) {
        this.queries = queries;
        this.orderService = orderService;
        this.itemService = itemService;
        this.excel = excel;
        this.progress = progress;
    }

    @Operation(
            summary = "샘플 주문 목록 조회",
            description = "주문번호·고객명·상태·주문일로 걸러 오프셋 페이징으로 반환한다. 삭제 게이트가 읽을 품목 건수를 함께 싣는다.")
    @PostMapping("/getOrderList")
    public ApiResponse<PageResponse<SmplOrderRow>> getOrderList(
            @RequestBody @Valid SearchRequest<Void> request) {
        return ApiResponse.success(queries.getOrderList(request), TraceId.current());
    }

    /**
     * 디테일 목록 — 대상이 <b>조회조건이 아니라 파라미터</b>다(backend/api.md).
     *
     * <p>조건 목록에 두면 사용자가 지울 수 있는 것이 되고, 그러면 <i>이 주문의 품목</i> 을
     * 묻는 pane 이 <b>전 주문의 품목</b>을 쏟는다.
     */
    @Operation(
            summary = "샘플 주문 품목 조회",
            description = "고른 주문 하나의 품목을 순번 순으로 반환한다. 대상은 조회조건이 아니라 파라미터다.")
    @PostMapping("/getItemList")
    public ApiResponse<PageResponse<SmplOrderItemRow>> getItemList(
            @RequestBody @Valid SearchRequest<OrdIdParams> request) {
        long ordId = Long.parseLong(request.params().ordId());
        return ApiResponse.success(queries.getItemList(ordId), TraceId.current());
    }

    @Operation(
            summary = "샘플 주문 저장",
            description = "추가·수정·물리 삭제를 한 트랜잭션으로 반영한다. 품목이 있는 주문의 삭제와 잠긴 주문의 수정을 거부한다.")
    @PostMapping("/saveOrders")
    public ApiResponse<Void> saveOrders(@RequestBody @Valid SampleOrderSaveRequest request) {
        orderService.saveOrders(request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    @Operation(
            summary = "샘플 주문 품목 저장",
            description = "고른 주문 하나의 품목을 한 트랜잭션으로 반영한다. 신규 행의 순번은 서버가 정한다.")
    @PostMapping("/saveItems")
    public ApiResponse<Void> saveItems(@RequestBody @Valid SampleItemSaveRequest request) {
        itemService.saveItems(Long.parseLong(request.ordId()), request.rows());
        return ApiResponse.success(null, TraceId.current());
    }

    /**
     * 출고 지시 — playbook/INDEX.md. <b>프로그레스가 없다.</b>
     *
     * <p>상태 하나를 바꾸는 일이라 즉시 끝난다. 프로그레스를 붙이면 창이 떴다가 바로
     * 닫히며, 그 깜빡임은 <i>무언가 잘못됐나</i> 로 읽힌다(backend/crosscutting.md).
     */
    @Operation(
            summary = "샘플 주문 출고 지시",
            description = "확정 상태의 주문을 출고로 옮긴다. 하나라도 대상이 아니면 전부 거부한다.")
    @PostMapping("/shipOrders")
    public ApiResponse<Map<String, Integer>> shipOrders(@RequestBody @Valid SampleShipRequest request) {
        int shipped = orderService.ship(request.targets().stream().map(Long::valueOf).toList());
        return ApiResponse.success(Map.of("count", shipped), TraceId.current());
    }

    /**
     * 품목 금액 재계산 — <b>{@code jobId} 를 즉시 돌려준다</b>(backend/crosscutting.md · playbook/INDEX.md).
     *
     * <p><b>이 화면이 행×디테일 진행률의 실물이다.</b> 대상은 고른 주문 몇이고 일은 그
     * 주문마다 딸린 품목 전건이라, 주문 하나가 <b>수천 건</b>일 수 있다 — 요청 스레드로
     * 하면 그 요청이 타임아웃까지 열려 있고 사용자는 <i>화면이 멈췄다</i> 를 겪는다.
     *
     * <p><b>{@code totCnt} 를 {@code null} 로 준다.</b> 전체가 <b>하나가 아니라 구간마다</b>
     * 있기 때문이다 — 서비스가 주문마다 {@code progress.reset(소제목, 품목수)} 로 갈아
     * 끼우고(backend/crosscutting.md), 화면은 소제목으로 <i>지금 어느 주문</i>을, 막대로 <i>그 주문이 어디까지</i>
     * 를 본다. 여기서 품목 총합을 미리 세어 주면 <b>막대 하나가 두 뜻을 갖게</b> 되고, 그
     * 값은 구간이 바뀔 때마다 어긋난다.
     *
     * <p><b>출고 지시와 갈리는 지점이 이것이다</b>(바로 위). 같은 {@code EXECUTE} 이고 같은
     * {@code target:'selected'} 인데 하나는 즉시 끝나고 하나는 진행률을 갖는다 — 이 화면이
     * 시연하려는 것이 그 갈림이다.
     */
    @Operation(
            summary = "샘플 주문 품목 금액 재계산",
            description =
                    "고른 주문마다 딸린 품목의 금액을 수량×단가로 다시 센다. jobId 를 즉시 돌려주고,"
                            + " 진행률은 주문이 바뀔 때마다 소제목과 함께 그 주문의 품목 기준으로 되돌아간다.")
    @PostMapping("/recalcItems")
    public ApiResponse<Map<String, String>> recalcItems(
            @RequestBody @Valid SampleItemRecalcRequest request) {
        List<Long> ordIds = request.targets().stream().map(Long::valueOf).toList();
        String jobId = progress.start(null, job -> itemService.recalcItems(ordIds, job));
        return ApiResponse.success(Map.of("jobId", jobId), TraceId.current());
    }

    /**
     * ②의 <i>현재 페이지</i> — <b>주문 + 품목 합본</b>이다(playbook/excel-download.md).
     *
     * <p>화면의 아홉 컬럼을 내리지 않는다. 이 파일의 표는 <b>화면에 없는 표</b>이며,
     * 그것이 이 갈래가 서버에 있는 이유다.
     */
    @Operation(
            summary = "샘플 주문 합본 엑셀 — 현재 페이지",
            description = "화면이 지목한 주문들을 품목과 조인해 한 장으로 만든다. 화면에 없는 표라 프론트가 만들 수 없다.")
    @PostMapping("/exportOrderPage")
    public ResponseEntity<byte[]> exportOrderPage(@RequestBody @Valid SampleExcelPageRequest request) {
        return file("주문합본_현재페이지.xlsx", excel.pageWorkbook(request.ordNos()));
    }

    @Operation(
            summary = "샘플 주문 합본 엑셀 — 전체",
            description = "조회조건 그대로 전량을 품목과 조인해 한 장으로 만든다. 상한 판정도 서버가 한다.")
    @PostMapping("/exportOrderAll")
    public ResponseEntity<byte[]> exportOrderAll(@RequestBody @Valid SearchRequest<Void> request) {
        return file("주문합본_전체.xlsx", excel.allWorkbook(request));
    }

    /**
     * {@code extra} 항목 — <b>품목이 없는 주문</b>이다.
     *
     * <p>조회로는 낼 수 없는 표다: 마스터는 주문을 내고 디테일은 고른 하나의 품목을 내므로,
     * <i>품목이 하나도 없는 주문들</i> 은 어느 그리드의 결과도 아니다.
     */
    @Operation(
            summary = "샘플 주문 엑셀 — 품목 없는 주문",
            description = "품목이 하나도 없는 주문만 모아 내린다. 두 그리드 어느 쪽의 조회로도 낼 수 없는 표다.")
    @PostMapping("/exportOrderNoItem")
    public ResponseEntity<byte[]> exportOrderNoItem() {
        return file("품목없는주문.xlsx", excel.noItemWorkbook());
    }

    /**
     * 파일 응답 — <b>이름을 서버가 정한다</b>.
     *
     * <p>{@code filename*}(RFC 5987)로 싣는다. 옛 {@code filename=} 만 쓰면 한글 이름이
     * <b>깨진 채로</b> 저장되며, 그 파일은 목록에서 무엇인지 알아볼 수 없다. 둘 다 실을
     * 수도 있지만 그러면 클라이언트마다 고르는 쪽이 갈리므로 <b>하나만</b> 싣는다.
     */
    private static ResponseEntity<byte[]> file(String name, byte[] bytes) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(XLSX))
                .body(bytes);
    }

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
}
