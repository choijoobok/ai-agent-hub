// @feat FEAT-503
package com.aiagent.sample.domain;

import static com.aiagent.sample.domain.SampleOrderSpec.O;

import com.aiagent.core.error.BusinessException;
import com.aiagent.core.search.PageMode;
import com.aiagent.core.search.PageResponse;
import com.aiagent.core.search.RangeGuard;
import com.aiagent.core.search.SearchRequest;
import com.aiagent.sample.domain.entity.QSmplOrderItem;
import com.aiagent.sample.domain.entity.QSmplOrderTag;
import com.aiagent.sample.domain.entity.SmplOrder;
import com.aiagent.sample.domain.entity.SmplOrderItem;
import com.aiagent.sample.domain.entity.SmplOrderTag;
import com.aiagent.sample.domain.repository.SmplOrderItemRepository;
import com.aiagent.sample.domain.repository.SmplOrderRepository;
import com.aiagent.sample.domain.repository.SmplOrderTagRepository;
import com.aiagent.domain.search.Built;
import com.aiagent.domain.search.QueryDslBuilder;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.SearchRangeProps;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code SMPL_MD_VERT} 의 조회 — playbook/INDEX.md.
 *
 * <h2>디테일은 조회조건을 갖지 않는다</h2>
 *
 * <p>대상이 <b>마스터의 선택</b>이며 그것은 {@code searchParams}(구조)이지
 * {@code conditions}(사용자가 좁힌 것)가 아니다(playbook/INDEX.md · backend/api.md). 조건 목록에 두면
 * 사용자가 지울 수 있는 것이 되고, 그러면 <i>이 주문의 품목</i> 을 묻는 pane 이
 * <b>전 주문의 품목</b>을 쏟는다.
 *
 * <h2>{@code itemCnt} 를 마스터가 함께 싣는다</h2>
 *
 * <p>마스터 삭제 게이트가 이 값을 읽는다(frontend/grid.md). 행마다 물으면 목록 한 장에 왕복이
 * 스물이고, 안 실으면 <b>화면이 게이트를 열 근거가 없어</b> 눌러 보고 실패로 알게 된다.
 */
@Service
@Transactional(readOnly = true)
public class SampleQueryService {

    private static final QSmplOrderItem I = QSmplOrderItem.smplOrderItem;

    /*
      후보 목록만 이 별칭을 쓴다. 태그를 주문 단위로 읽는 자리는 리포지토리 메서드가
      맡는데(SmplOrderTagRepository), 여기서 필요한 것은 주문을 가로지르는 distinct 라
      그 메서드로 표현되지 않는다.
    */
    private static final QSmplOrderTag T = QSmplOrderTag.smplOrderTag;

    /*
      nclob 셋 중 ERR_MSG 만 싣는다. MEMO·RMK 는 이 화면의 아홉 컬럼에 없고(playbook/INDEX.md),
      목록이 안 쓰는 nclob 을 나르면 페이지마다 그만큼이 그냥 왕복한다.
    */
    private static final Expression<?>[] LIST = {
        O.ordId,
        O.ordNo,
        O.ordNm,
        O.custId,
        O.custNm,
        O.ordStatCd,
        O.ordDt,
        O.ordDtm,
        O.ordQty,
        O.unitAmt,
        O.ordAmt,
        O.vatAmt,
        O.dcRt,
        O.urgentYn,
        O.useYn,
        O.lockYn,
        O.errMsg,
        O.regId,
        O.regDtm,
        O.modId,
        O.modDtm
    };

    /**
     * ①의 서른 — playbook/INDEX.md. <b>{@code nclob} 셋을 전부 싣는다.</b>
     *
     * <p>③이 {@code ERR_MSG} 하나만 싣는 것과 갈리는 자리이며, 이 화면은 {@code MEMO}(에디터
     * {@code largeText}) · {@code RMK}({@code textModal}) · {@code ERR_MSG}({@code viewer})
     * 셋이 <b>서로 다른 어휘의 유일한 실물</b>이라 뺄 수가 없다(playbook/INDEX.md).
     */
    private static final Expression<?>[] FULL = {
        O.ordId,
        O.ordNo,
        O.ordNm,
        O.custId,
        O.custNm,
        O.custGrdCd,
        O.ordStatCd,
        O.chnlCd,
        O.priorityCd,
        O.ordDt,
        O.dlvrYm,
        O.ordDtm,
        O.ordQty,
        O.unitAmt,
        O.ordAmt,
        O.vatAmt,
        O.dcRt,
        O.procMs,
        O.urgentYn,
        O.useYn,
        O.memo,
        O.rmk,
        O.errMsg,
        O.lockYn,
        O.srcSysCd,
        O.regId,
        O.regDtm,
        O.modId,
        O.modDtm
    };

    /**
     * ②의 열둘 — playbook/INDEX.md. <b>{@code nclob} 은 {@code ERR_MSG} 하나다.</b>
     *
     * <p>{@code viewer:'largeText'} 가 그것을 펼쳐 읽게 한다 — 읽기 전용인데 셀 폭에 갇히면
     * 오류 문구를 읽을 수가 없다. {@code MEMO}·{@code RMK} 는 이 화면에 없다.
     */
    private static final Expression<?>[] CURSOR = {
        O.ordId,
        O.ordNo,
        O.ordNm,
        O.custNm,
        O.custGrdCd,
        O.ordStatCd,
        O.ordDt,
        O.ordDtm,
        O.ordQty,
        O.ordAmt,
        O.dcRt,
        O.procMs,
        O.errMsg
    };

    /**
     * 화면 층의 상한 — playbook/INDEX.md 의 {@code rangeDays.max}.
     *
     * <p><b>넓히는 쪽으로는 못 간다</b>(screen/admin/system/log/access/requirements.md). 시스템 값이 더 좁으면 그쪽이 이긴다.
     */
    public static final int MAX_RANGE_DAYS = 31;

    private final JPAQueryFactory query;
    private final QueryDslBuilder builder;
    private final SmplOrderRepository orders;
    private final SmplOrderItemRepository items;
    private final SmplOrderTagRepository tags;
    private final ConfigLookup configs;

    public SampleQueryService(
            JPAQueryFactory query,
            QueryDslBuilder builder,
            SmplOrderRepository orders,
            SmplOrderItemRepository items,
            SmplOrderTagRepository tags,
            ConfigLookup configs) {
        this.query = query;
        this.builder = builder;
        this.orders = orders;
        this.items = items;
        this.tags = tags;
        this.configs = configs;
    }

    /** 마스터 목록 — 오프셋 페이징. 기본 정렬은 {@code ORD_DTM DESC, ORD_ID DESC} 다. */
    public PageResponse<SmplOrderRow> getOrderList(SearchRequest<Void> request) {
        Built built = builder.build(request, SampleOrderSpec.SPEC, PageMode.offset);

        List<Tuple> found = built.apply(query.select(Projections.tuple(LIST))).fetch();
        Long total = built.count(query).fetchOne();

        return built.respond(toRows(found), total == null ? 0L : total);
    }

    /**
     * ①의 목록 — 오프셋 페이징, 컬럼 서른(playbook/INDEX.md).
     *
     * <p><b>{@link #getOrderList} 와 엔드포인트가 갈린다.</b> 같은 테이블이고 같은 페이징
     * 모드지만 <b>투영이 다르다</b> — playbook/INDEX.md 가 <i>"모드는 엔드포인트가 정한다"</i> 고 한 것과
     * 같은 이유로, 무엇을 실을지는 테이블이 아니라 <b>부르는 화면</b>이 정한다.
     */
    public PageResponse<SmplOrderFullRow> getOrderOffsetList(SearchRequest<Void> request) {
        Built built = builder.build(request, SampleOrderSpec.SPEC, PageMode.offset);

        List<Tuple> found = built.apply(query.select(Projections.tuple(FULL))).fetch();
        Long total = built.count(query).fetchOne();

        return built.respond(toFullRows(found), total == null ? 0L : total);
    }

    /**
     * 조건 그대로 <b>전량</b> — 엑셀의 <i>전체</i> 항목이 쓴다(playbook/excel-download.md).
     *
     * <p>페이징이 없으므로 상한은 부르는 쪽이 건다({@link SampleExcelService}).
     */
    public List<SmplOrderRow> findOrders(SearchRequest<Void> request, int limit) {
        Built built = builder.build(request, SampleOrderSpec.SPEC, PageMode.all);
        List<Tuple> found = built
                .apply(query.select(Projections.tuple(LIST)))
                .limit(limit)
                .fetch();
        return toRows(found);
    }

    /** 품목이 <b>하나도 없는</b> 주문 — 조회로는 낼 수 없는 표다(playbook/INDEX.md 의 `extra`). */
    public List<SmplOrderRow> findOrdersWithoutItems(int limit) {
        List<Tuple> found = query
                .select(Projections.tuple(LIST))
                .from(O)
                .where(JPAExpressions.selectOne().from(I).where(I.ordId.eq(O.ordId)).notExists())
                .orderBy(O.ordDtm.desc(), O.ordId.desc())
                .limit(limit)
                .fetch();
        return toRows(found);
    }

    /**
     * ②의 목록 — <b>커서형</b>이다(playbook/INDEX.md · backend/api.md).
     *
     * <p><b>총 건수를 세지 않는다.</b> 그것이 이 모드의 값이며, 대용량 이력에서
     * {@code COUNT(*)} 는 매 장마다 전 범위를 훑는다 — 화면의 푸터에 총 건수 자리가 없는
     * 것이(playbook/INDEX.md) 이 사실의 표현이다.
     *
     * <p><b>기간이 필수다.</b> screen/admin/system/log/access/requirements.md 의 규칙을 업무 테이블에서 되풀이하는 것이 이 화면의
     * 역할이며(playbook/INDEX.md), 화면만 막으면 <b>도구·스크립트가 그대로 지나간다</b>.
     *
     * <p><b>{@code CURSOR_SPEC} 을 쓴다</b> — 기본 정렬이 하나다. {@code SPEC} 을 그대로
     * 쓰면 정렬 키가 둘이라 서버가 400 이며, 그 실패는 <i>조회를 눌렀는데 아무 일도 없다</i>
     * 로 보인다.
     */
    public PageResponse<SmplOrderCursorRow> getOrderCursorList(SearchRequest<Void> request) {
        checkRange(request);
        Built built = builder.build(request, SampleOrderSpec.CURSOR_SPEC, PageMode.cursor);

        List<Tuple> found = built.apply(query.select(Projections.tuple(CURSOR))).fetch();

        List<SmplOrderCursorRow> rows = new ArrayList<>(found.size());
        for (Tuple row : found) {
            Long ordId = row.get(O.ordId);
            rows.add(new SmplOrderCursorRow(
                    ordId == null ? null : String.valueOf(ordId),
                    row.get(O.ordNo),
                    row.get(O.ordNm),
                    row.get(O.custNm),
                    row.get(O.custGrdCd),
                    row.get(O.ordStatCd),
                    row.get(O.ordDt),
                    row.get(O.ordDtm),
                    row.get(O.ordQty) == null ? 0 : row.get(O.ordQty),
                    row.get(O.ordAmt),
                    row.get(O.dcRt),
                    row.get(O.procMs),
                    row.get(O.errMsg)));
        }
        /*
          커서형은 respondCursor 로 답한다 — 기준점을 뽑을 함수가 필요하다. 총 건수를 싣는
          respond 로는 다음 장의 커서를 만들 수 없고, 그 자리를 0 으로 채우면 봉투는
          그럴듯한데 다음 이 영원히 비활성이다.

          정렬 키가 ORD_DTM 이고 tie-breaker 가 ORD_ID 인 것이 CURSOR_SPEC 과 같아야 한다 —
          갈리면 WHERE 의 튜플 비교와 커서가 어긋나 같은 행이 두 장에 나오거나 빠진다.
        */
        return built.respondCursor(rows, SmplOrderCursorRow::ordDtm, SmplOrderCursorRow::ordId);
    }

    /**
     * 기간 조건을 <b>서버가 다시 본다</b> — screen/admin/system/log/access/requirements.md 의 두 층 중 위.
     *
     * <p>통과하지 못하면 400 이다. <b>조용히 자르지 않는다</b> — 잘라 주면 사용자는
     * <i>"상한만큼 봤다"</i> 고 믿은 채 더 넓은 기간의 결론을 낸다.
     *
     * <p><b>우회 필드가 없다.</b> 사용 이력에는 {@code traceId} 가 있었지만(한 요청을 콕
     * 집는 조건이라 기간이 부하가 되지 않는다) 이 화면에는 그런 칸이 없다 — 주문번호는
     * {@code contains} 라 전 범위를 훑는다.
     */
    private void checkRange(SearchRequest<Void> request) {
        int maxDays = Math.min(configs.get(SearchRangeProps.class).maxDays(), MAX_RANGE_DAYS);
        RangeGuard.check(
                request,
                new RangeGuard.Rule(
                        "ordDtm",
                        null,
                        maxDays,
                        SampleErrorCode.RANGE_REQUIRED,
                        SampleErrorCode.RANGE_TOO_WIDE));
    }

    /**
     * 참조 팝업의 후보 — playbook/INDEX.md. <b>주문에서 중복 없이 뽑는다.</b>
     *
     * <p>고객 테이블이 없는 이유는 {@link SmplCustomerRow} 가 적어 두었다.
     *
     * <p><b>조회조건이 없다.</b> 후보가 시드의 다섯이라 <i>고르러 왔는데 먼저 찾아야</i> 하는
     * 창이 되면 안 된다(PopupSpec 의 {@code searchFields} 가 선택인 이유).
     *
     * <p>등급은 <b>가장 최근 주문의 것</b>이다. 같은 고객이 여러 등급으로 나오면 팝업이
     * 같은 이름을 여러 줄로 보이고, 고르는 사람은 <b>어느 줄이 맞는지 알 방법이 없다</b>.
     */
    public PageResponse<SmplCustomerRow> getCustomerList() {
        List<Tuple> found = query
                .select(O.custId, O.custNm, O.custGrdCd)
                .from(O)
                .orderBy(O.custId.asc(), O.ordDtm.desc())
                .fetch();

        Map<String, SmplCustomerRow> byId = new LinkedHashMap<>();
        for (Tuple row : found) {
            byId.putIfAbsent(
                    row.get(O.custId),
                    new SmplCustomerRow(row.get(O.custId), row.get(O.custNm), row.get(O.custGrdCd)));
        }
        // 팝업도 그리드다(frontend/grid.md) — 같은 봉투를 읽는다.
        return PageResponse.all(List.copyOf(byId.values()));
    }

    /**
     * {@code listModal} 이 항목을 고르는 팝업의 후보 &mdash; 54 &sect;5.6 &middot; 68 &sect;9.6.
     *
     * <p><b>이름이 &#9316;의 {@code getTagList} 보다 길다.</b> 그 짧은 이름은 이미
     * <i>한 주문의 태그 행들</i> 이고(같은 {@code /api/sample/service} 를 나눠 쓴다),
     * 여기서 내는 것은 <b>주문을 가로지르는 후보</b>다 &mdash; 같은 이름을 둘이 쓰면 기동이
     * 매핑 충돌로 죽고, 비슷한 이름을 쓰면 다음 사람이 <i>어느 쪽이 내 것인가</i> 를
     * 응답을 까 보고 안다.
     *
     * <p><b>태그 마스터가 없다.</b> 그래서 후보는 <b>이미 자식 테이블에 있는 것</b>이며,
     * 고객 후보를 주문에서 뽑는 것과 같은 대역이다({@link #getCustomerList}) &mdash; 48
     * &sect;2 가 담는 테이블을 둘로 정했고, 태그 마스터는 그 둘에 없다.
     *
     * <p><b>새 태그를 이 창에서 만들 수는 없다.</b> 54 &sect;5.6 이 {@code listModal} 에서
     * 타이핑을 받지 않기로 한 결과이며(자유 입력을 열면 어느 코드에도 걸리지 않는 태그가
     * 남는다), 사용 프로젝트에서 태그 마스터가 생기면 <b>이 메서드의 {@code from} 만</b>
     * 그 테이블로 바뀐다.
     */
    public PageResponse<SmplOrderTagRow> getTagCandidateList() {
        /*
          distinct 를 DB 가 한다. 전건을 끌어와 Map 으로 접는 위의 방식(getCustomerList)은
          거기서는 등급을 최근 주문에서 골라야 해서 필요했지만, 여기 두 컬럼에는 고를 것이
          없다 — 2,000 주문의 태그를 전부 나른 뒤 아홉으로 접을 이유가 없다.
        */
        return PageResponse.all(query
                .select(Projections.constructor(SmplOrderTagRow.class, T.tagNm, T.tagTypeCd))
                .distinct()
                .from(T)
                .orderBy(T.tagNm.asc())
                .fetch());
    }

    /**
     * 디테일 목록 — 대상은 마스터의 선택이다(위).
     *
     * <p><b>봉투에 담아 돌려준다</b>({@code PageMode.all}). 그리드 컨트롤러는 응답을 언제나
     * {@code PageResponse} 로 읽으므로(backend/api.md) 맨 배열을 주면 {@code content} 가
     * {@code undefined} 이고 화면이 <b>그 자리에서 죽는다</b> — 조회는 200 인데 모달에
     * {@code TypeError} 가 뜬다.
     *
     * <p>페이징이 없는 것은 대상이 <b>주문 하나의 품목</b>이라서다. 모드는 엔드포인트가
     * 정하고 응답이 나른다(backend/api.md) — 화면은 그 사실로 푸터를 그리지 않는다.
     */
    public PageResponse<SmplOrderItemRow> getItemList(long ordId) {
        return PageResponse.all(
                items.findByOrdIdOrderByItemSeq(ordId).stream()
                        .map(SampleQueryService::toRow)
                        .toList());
    }

    /**
     * ⑤의 업무 데이터 탭 목록 — playbook/INDEX.md. <b>주문을 가로질러 품목을 낸다.</b>
     *
     * <p>{@link #getItemList} 와 갈리는 것이 <b>대상</b>이다: 저것은 고른 주문 하나의
     * 품목이라 조회조건이 없고(파라미터다), 이것은 사용자가 좁히는 조회조건 넷을 받는다
     * (주문번호 · 품목코드 · 품목상태 · 납기일).
     *
     * <p><b>엑셀 업로드 탭도 이 경로를 쓴다</b>(playbook/excel-upload.md). 파일을 읽은 직후 기존 행을
     * 받아 대조하는 자리이며, 새 경로를 만들면 <i>화면이 보는 표</i> 와 <i>대조하는 표</i>
     * 가 갈릴 수 있다 — 그 어긋남은 <b>이미 있는 행이 신규로</b> 잡히는 모습으로 나온다.
     */
    public PageResponse<SmplOrderItemRow> getFlatItemList(SearchRequest<Void> request) {
        Built built = builder.build(request, SampleItemSpec.SPEC, PageMode.offset);

        // from 은 `Built` 가 스펙의 root 로 붙인다 — selectFrom 을 쓰면 같은 것이 두 번 든다.
        List<SmplOrderItem> found = built.apply(query.select(SampleItemSpec.I)).fetch();
        Long total = built.count(query).fetchOne();

        return built.respond(
                found.stream().map(SampleQueryService::toRow).toList(),
                total == null ? 0L : total);
    }

    /**
     * 태그 탭의 목록 — playbook/INDEX.md. <b>⑤가 {@code SMPL_ORDER_TAG} 의 둘째 소비자다.</b>
     *
     * <p>①은 같은 데이터를 {@code list} 컬럼 하나로 나르고(그 화면의 {@code tagList}),
     * 여기서는 <b>행으로</b> 본다. playbook/INDEX.md 의 <i>"배열은 화면의 어휘이고 저장은 자식
     * 테이블"</i> 이 두 화면을 나란히 놓아야 이해되는 결정이며, 그 나란함이 이 메서드다.
     *
     * <p>봉투에 담는다({@code PageMode.all}) — {@link #getItemList} 와 같은 이유다.
     */
    public PageResponse<SmplTagRow> getTagList(long ordId) {
        return PageResponse.all(
                tags.findByOrdIdOrderBySortNoAscTagSeqAsc(ordId).stream()
                        .map(SampleQueryService::toRow)
                        .toList());
    }

    /** 엑셀 합본이 쓰는 것 — 주문 여럿의 품목을 <b>한 번에</b> 읽는다(왕복이 N 이 되지 않게). */
    public List<SmplOrderItem> itemsOf(List<Long> ordIds) {
        return ordIds.isEmpty() ? List.of() : items.findByOrdIdInOrderByOrdIdAscItemSeqAsc(ordIds);
    }

    public SmplOrder require(Long ordId) {
        return orders.findById(ordId)
                .orElseThrow(() -> new BusinessException(
                        SampleErrorCode.NOT_FOUND, "no such order: " + ordId));
    }

    public long itemCount(Long ordId) {
        return items.countByOrdId(ordId);
    }

    /**
     * 행마다 품목 수를 붙인다 — <b>한 번의 group by</b> 다.
     *
     * <p>행마다 세면 목록 한 장에 왕복이 스물이고, 안 세면 화면이 삭제 게이트를 열 근거가
     * 없다(위).
     */
    private List<SmplOrderRow> toRows(List<Tuple> found) {
        List<Long> ordIds = found.stream().map(row -> row.get(O.ordId)).filter(id -> id != null).toList();
        Map<Long, Long> counts = countItems(ordIds);

        List<SmplOrderRow> rows = new ArrayList<>(found.size());
        for (Tuple row : found) {
            Long ordId = row.get(O.ordId);
            rows.add(new SmplOrderRow(
                    // 문자열로 내린다 — frontend/packages.md. 숫자로 내리면 브라우저가 정밀도를 잃는다.
                    ordId == null ? null : String.valueOf(ordId),
                    row.get(O.ordNo),
                    row.get(O.ordNm),
                    row.get(O.custId),
                    row.get(O.custNm),
                    row.get(O.ordStatCd),
                    row.get(O.ordDt),
                    row.get(O.ordDtm),
                    row.get(O.ordQty) == null ? 0 : row.get(O.ordQty),
                    row.get(O.unitAmt),
                    row.get(O.ordAmt),
                    row.get(O.vatAmt),
                    row.get(O.dcRt),
                    row.get(O.urgentYn),
                    row.get(O.useYn),
                    row.get(O.lockYn),
                    row.get(O.errMsg),
                    counts.getOrDefault(ordId, 0L),
                    row.get(O.regId),
                    row.get(O.regDtm),
                    row.get(O.modId),
                    row.get(O.modDtm)));
        }
        return rows;
    }

    /**
     * 태그는 <b>한 번에</b> 읽는다 — 행마다 물으면 목록 한 장에 왕복이 스물이다.
     *
     * <p>{@code countItems} 와 같은 규칙이며, 자식 테이블을 조인으로 붙이지 않는 것은
     * <b>행이 태그 수만큼 불어나</b> 오프셋 페이징의 {@code LIMIT} 이 주문이 아니라 태그를
     * 세게 되기 때문이다 — 스무 건을 달라고 했는데 주문 일곱이 온다.
     */
    private Map<Long, List<SmplOrderTagRow>> tagsOf(List<Long> ordIds) {
        if (ordIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SmplOrderTagRow>> byOrder = new LinkedHashMap<>();
        for (SmplOrderTag tag : tags.findByOrdIdInOrderByOrdIdAscSortNoAsc(ordIds)) {
            byOrder.computeIfAbsent(tag.getOrdId(), key -> new ArrayList<>())
                    .add(new SmplOrderTagRow(tag.getTagNm(), tag.getTagTypeCd()));
        }
        return byOrder;
    }

    private List<SmplOrderFullRow> toFullRows(List<Tuple> found) {
        List<Long> ordIds = found.stream().map(row -> row.get(O.ordId)).filter(id -> id != null).toList();
        Map<Long, List<SmplOrderTagRow>> tagsByOrder = tagsOf(ordIds);

        List<SmplOrderFullRow> rows = new ArrayList<>(found.size());
        for (Tuple row : found) {
            Long ordId = row.get(O.ordId);
            rows.add(new SmplOrderFullRow(
                    ordId == null ? null : String.valueOf(ordId),
                    row.get(O.ordNo),
                    row.get(O.ordNm),
                    row.get(O.custId),
                    row.get(O.custNm),
                    row.get(O.custGrdCd),
                    row.get(O.ordStatCd),
                    row.get(O.chnlCd),
                    row.get(O.priorityCd),
                    row.get(O.ordDt),
                    row.get(O.dlvrYm),
                    row.get(O.ordDtm),
                    row.get(O.ordQty) == null ? 0 : row.get(O.ordQty),
                    row.get(O.unitAmt),
                    row.get(O.ordAmt),
                    row.get(O.vatAmt),
                    row.get(O.dcRt),
                    row.get(O.procMs),
                    row.get(O.urgentYn),
                    row.get(O.useYn),
                    // 태그가 없는 주문은 빈 배열이다 — null 이면 화면이 "없음" 과 "모름" 을 가른다.
                    tagsByOrder.getOrDefault(ordId, List.of()),
                    row.get(O.memo),
                    row.get(O.rmk),
                    row.get(O.errMsg),
                    row.get(O.lockYn),
                    row.get(O.srcSysCd),
                    row.get(O.regId),
                    row.get(O.regDtm),
                    row.get(O.modId),
                    row.get(O.modDtm)));
        }
        return rows;
    }

    private Map<Long, Long> countItems(List<Long> ordIds) {
        if (ordIds.isEmpty()) {
            return Map.of();
        }
        return query
                .select(I.ordId, I.count())
                .from(I)
                .where(I.ordId.in(ordIds))
                .groupBy(I.ordId)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        row -> row.get(I.ordId),
                        row -> {
                            Long count = row.get(I.count());
                            return count == null ? 0L : count;
                        }));
    }

    static SmplOrderItemRow toRow(SmplOrderItem item) {
        return new SmplOrderItemRow(
                String.valueOf(item.getOrdId()),
                item.getItemSeq(),
                item.getOrdNo(),
                item.getItemCd(),
                item.getItemNm(),
                item.getUomCd(),
                item.getQty(),
                item.getUnitAmt(),
                item.getAmt(),
                item.getDlvrDt(),
                item.getItemStatCd(),
                item.getUseYn(),
                item.getRmk(),
                item.getRegId(),
                item.getRegDtm(),
                item.getModId(),
                item.getModDtm());
    }

    static SmplTagRow toRow(SmplOrderTag tag) {
        return new SmplTagRow(
                String.valueOf(tag.getOrdId()),
                tag.getTagSeq(),
                tag.getTagNm(),
                tag.getTagTypeCd(),
                tag.getSortNo(),
                tag.getRegId(),
                tag.getRegDtm(),
                tag.getModId(),
                tag.getModDtm());
    }
}
