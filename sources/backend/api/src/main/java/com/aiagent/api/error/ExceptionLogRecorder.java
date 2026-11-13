package com.aiagent.api.error;

import com.aiagent.api.authz.ApiLayer;
import com.aiagent.api.log.RequestFacts;
import com.aiagent.api.trace.ClientIp;
import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.ErrorCode;
import com.aiagent.domain.log.LogText;
import com.aiagent.domain.log.entity.SysExceptionLog;
import com.aiagent.domain.log.service.ExceptionLogService;
import com.aiagent.support.time.BusinessCalendar;
import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 예외 한 건을 {@code SYS_LO_EXCEPTION} 한 행으로 — backend/packages.md · LOG-03 · screen/admin/system/log/access/requirements.md.
 *
 * <p><b>부르는 자리가 {@link GlobalExceptionHandler} 하나다.</b> 그 클래스가 <i>예외를
 * 봉투로 바꾸는 유일한 자리</i> 이므로, 적재도 거기 하나여야 <b>봉투에 나간 {@code code} ·
 * {@code message} 와 테이블의 {@code ERR_CD} · {@code ERR_MSG} 가 같은 값</b>임이 구조로
 * 보장된다. 인터셉터가 따로 적으면 그 둘이 갈릴 수 있고, 갈리는 순간 운영자가 보는 화면과
 * 사용자가 받은 화면이 서로 다른 말을 한다.
 *
 * <p>{@code ERR_CD} · {@code ERR_MSG} 는 <b>응답이 이미 갖고 있는 값</b>이다(backend/packages.md) —
 * 새로 만드는 것이 아니라 <b>버리고 있던 것을 적는다</b>.
 *
 * <h2>남기지 않는 둘</h2>
 *
 * <table><caption></caption>
 * <tr><td><b>401</b></td><td>세션 만료는 <b>정상 흐름</b>이고(재인증 모달) 하트비트마다
 *     쌓이면 <b>에러 로그가 만료 로그가 된다</b>. 그 사건은 로그인 이력의
 * {@code SESSION_TIMEOUT} 이 갖는다(sys/logs.md · screen/admin/system/log/access/requirements.md)</td></tr>
 * <tr><td><b>{@code /api/} 밖</b></td><td>정적 리소스와 SPA 라우트다(backend/packages.md). 사용 이력이
 *     같은 판정을 쓰며({@link ApiLayer#of} 가 {@code null}), 두 테이블의 대상이 어긋나면
 *     {@code TRACE_ID} 로 잇는 화면이 그것을 <b>유실로 보이게</b> 한다</td></tr>
 * </table>
 *
 * <h2>4xx 는 스택을 남기지 않는다</h2>
 *
 * <p>{@code AccessDeniedException} 의 스택은 인터셉터의 프레임뿐이고 <b>전 행이 같다</b> —
 * 180일치 노이즈다(screen/admin/system/log/access/requirements.md). {@code ROOT_CAUSE_STACK} 이 {@code null} 이며 그것이 모달의
 * 스택 탭이 비활성으로 뜨는 근거다.
 *
 * <h2>여기서 실패해도 응답은 나간다</h2>
 *
 * <p>삼키는 자리가 이 클래스다 — {@link ExceptionLogService} 의 경계 <b>밖</b>이라야
 * rollback-only 커밋이 던지는 것까지 잡힌다. <b>실패를 알리는 경로가 스스로 실패해서 사용자가
 * 봉투 대신 500 HTML 을 받는 것</b>이 여기서 막아야 할 최악이며, {@link ErrorMessages} 가
 * 문구를 못 읽을 때 폴백으로 가는 것과 같은 판단이다.
 */
@Component
public class ExceptionLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLogRecorder.class);

    /**
     * 스택을 저장하는 하한 — <b>4xx 와 5xx 를 가르는 그 선</b>이다(screen/admin/system/log/access/requirements.md).
     *
     * <p>{@code HTTP_STAT} 이 이 값 미만이면 {@code ROOT_CAUSE_STACK} 은 {@code null} 이다.
     */
    private static final int STACK_FROM = 500;

    /** 기록하지 않는 유일한 상태 — 세션 만료는 정상 흐름이다(screen/admin/system/log/access/requirements.md). */
    private static final int SKIPPED_STATUS = 401;

    /**
     * 스택의 UTF-8 바이트 상한.
     *
     * <p><b>{@code SYS_CONFIG} 키로 두지 않는다.</b> 운영자가 조절할 값이 아니라 <i>사고를
     * 막는 선</i> 이며(폭주하는 재귀가 수 MB 스택을 만든다), 전문의 1MB 와 달리 <b>정상 값이
     * 이 근처에 오지 않는다</b> — 근본 원인까지 이어 붙인 체인도 보통 수 KB 다. 조절 가능한
     * 값으로 두면 <b>바꿀 이유가 없는 손잡이가 하나 는다</b>(sys/code.md 의 기준).
     */
    private static final int STACK_MAX_BYTES = 32 * 1024;

    private final ExceptionLogService exceptionLogs;
    private final BusinessCalendar calendar;

    public ExceptionLogRecorder(ExceptionLogService exceptionLogs, BusinessCalendar calendar) {
        this.exceptionLogs = exceptionLogs;
        this.calendar = calendar;
    }

    /**
     * @param message <b>사용자에게 나간 그 문구</b>다 — {@link ErrorMessages} 가 완성한 값을
     *     그대로 받는다. 예외의 {@code getMessage()} 가 아니다: 그것은 서버 언어의 <i>로그의
     *     것</i> 이며, 사용자 언어로 남겨야 <i>"사용자가 무엇을 봤나"</i> 에 답한다
     * @param cause 스택을 낼 예외. 4xx 는 무엇이 오든 저장하지 않으므로 {@code null} 이어도 된다
     */
    public void record(
            HttpServletRequest request, int status, ErrorCode errorCode, String message, Throwable cause) {
        if (status == SKIPPED_STATUS) {
            return;
        }
        String path = RequestFacts.path(request);
        if (ApiLayer.of(path) == null) {
            return;
        }
        try {
            exceptionLogs.record(SysExceptionLog.builder()
                    .userId(RequestFacts.userId())
                    .menuCd(RequestFacts.menuCd(request))
                    .requestUri(path)
                    .httpStat((short) status)
                    .errCd(errorCode.code())
                    .errMsg(message)
                    .rootCauseStack(status < STACK_FROM || com.aiagent.api.log.SensitiveRequest.excluded(request) ? null : stackOf(cause))
                    .clientIp(ClientIp.of(request))
                    .traceId(TraceId.current())
                    .creDtm(calendar.now())
                    .build());
        } catch (RuntimeException e) {
            /*
              이력 테이블 하나가 응답을 막으면 안 된다 — backend/packages.md 의 <i>"warn 만 남기고 삼킴"</i>.
              사용 이력과 다른 점이 하나 있다: 여기서 삼킨 사건은 <b>이미 예외가 난 요청</b>
              이므로, 이 warn 이 비면 <b>고장이 두 번</b> 조용해진다.
            */
            log.warn("에러 로그 기록 실패 uri={} code={} — 응답에는 영향을 주지 않는다",
                    request.getRequestURI(), errorCode.code(), e);
        }
    }

    /**
     * <b>체인을 통째로 적는다</b> — {@code Caused by:} 까지다.
     *
     * <p>컬럼 이름은 {@code ROOT_CAUSE_STACK} 이고 진단이 필요로 하는 것도 근본 원인이지만,
     * <b>가장 깊은 예외만 남기면 그것이 어디서 감싸였는지가 사라진다</b> — 같은
     * {@code NullPointerException} 이 세 경로에서 나면 세 행이 구별되지 않는다. 표준 형식이
     * 근본 원인을 <b>맨 끝에</b> 두므로 이름이 말하는 것은 그대로 들어 있고, 맥락이 함께 온다.
     *
     * <p>{@code null} 을 받을 수 있다 — 예외 없이 상태만 정해지는 경로가 있다(검증 실패는
     * 예외가 있지만 스택이 무의미하고, 5xx 라면 언제나 예외가 있다).
     */
    private static String stackOf(Throwable cause) {
        if (cause == null) {
            return null;
        }
        StringWriter buffer = new StringWriter();
        try (PrintWriter writer = new PrintWriter(buffer)) {
            cause.printStackTrace(writer);
        }
        return LogText.truncate(buffer.toString(), STACK_MAX_BYTES);
    }
}
