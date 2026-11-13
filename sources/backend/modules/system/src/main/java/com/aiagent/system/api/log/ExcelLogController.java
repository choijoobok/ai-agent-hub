// @feat FEAT-507
package com.aiagent.system.api.log;

import com.aiagent.system.api.log.request.ExcelLogRequest;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 엑셀을 내린 사실을 알린다 — playbook/excel-download.md · LOG-08.
 *
 * <h2>몸이 비어 있는 것이 이 컨트롤러의 설계다</h2>
 *
 * <p>저장하는 코드가 없다. {@link UserActionLogInterceptor} 가 <b>모든 {@code /api/**}
 * 요청</b>을 {@code SYS_LO_ACCESS} 한 행으로 남기므로(sys/logs.md), 이 요청이 <b>도착하는 것
 * 자체가 기록</b>이다 — 사용자·메뉴·IP·추적 ID 는 그 인터셉터가 이미 채우고, body 는
 * {@code PARAMETERS} 에 그대로 들어간다.
 *
 * <p><b>그래서 이 클래스가 하는 일은 셋뿐이다.</b>
 *
 * <ol>
 *   <li><b>존재하기</b> — 핸들러가 없으면 404 이고, 그때는 인터셉터의
 *       {@code afterCompletion} 도 돌지 않아 <b>아무 데도 남지 않는다</b>
 *   <li><b>모양을 본다</b> — {@code PARAMETERS} 는 {@code nclob} 이라 검증이 없으면 무엇이든
 * 들어간다. 로그가 유일한 감사 수단인 템플릿에서(sys/logs.md) 그것은 <b>오염</b>이다
 * <li><b>인증을 요구한다</b> — {@code common} 계층이 그것이다(backend/security.md)
 * </ol>
 *
 * <h2>권한을 보지 않는다 — 로그는 <b>주장</b>을 받는다</h2>
 *
 * <p>{@code MenuStatController} 는 유효 권한에 없는 메뉴를 <b>세지 않는다.</b> 여기서는
 * 그러지 않는데, 갈리는 자리가 <i>쓰는 곳</i> 이기 때문이다 — 저쪽이 만드는 것은
 * {@code SYS_USER_MENU_STAT} 라는 <b>업무 데이터</b>이고 이쪽이 만드는 것은 <b>로그</b>다.
 *
 * <p>sys/logs.md 이 {@code MENU_CD} 를 두고 <i>"클라이언트 주장이므로 신뢰하지 않는다"</i> 고
 * 이미 적었다. 이 엔드포인트 하나에만 권한 문을 달면 <b>나머지 행은 검증된 것처럼</b>
 * 읽히고, 그것이 더 위험하다. 무엇보다 <b>거부해도 되돌릴 것이 없다</b> — 파일은 이미
 * 브라우저에서 만들어졌다.
 *
 * <p><b>정말 막아야 하는 데이터라면 답은 {@code READ} 를 주지 않는 것이다</b>(backend/security.md).
 * 프론트 생성 엑셀에서 {@code EXPORT} 는 집행되는 통제가 아니라 운영 사실이며(sys/menu.md),
 * <b>그 사실이 값을 내는 것이 이 기록과 짝을 이룰 때</b>다.
 *
 * <h2>응답을 기다리지 않는 쪽이 정상이다</h2>
 *
 * <p>부수 효과다(playbook/excel-download.md) — 실패해도 다운로드는 이미 끝났고 화면은 알릴 것이 없다.
 * 프론트가 응답을 버리므로 <b>여기서 무엇을 돌려주든 보이지 않는다.</b> 그래도 형태를
 * 맞추는 것은 실패가 <b>이력에 4xx 로 남기</b> 때문이다 — 조용히 200 을 주면 잘못 보낸
 * 클라이언트가 영원히 드러나지 않는다.
 */
@RestController
public class ExcelLogController {

    @PostMapping("/api/system/common/excel/saveLog")
    public ApiResponse<Void> log(@Valid @RequestBody ExcelLogRequest request) {
        /*
          <b>{@code request} 를 쓰지 않는 것이 맞다.</b> 값은 인터셉터가 body 에서 읽어
          PARAMETERS 로 넣으며, 여기서 다시 저장하면 같은 사실이 두 곳에 생긴다 —
          playbook/excel-download.md 가 전용 테이블을 두지 않기로 한 것이 그 말이다.
        */
        return ApiResponse.success(null, TraceId.current());
    }
}
