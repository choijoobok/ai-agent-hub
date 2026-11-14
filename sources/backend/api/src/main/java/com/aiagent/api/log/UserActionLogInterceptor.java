package com.aiagent.api.log;

import com.aiagent.api.authz.ApiLayer;
import com.aiagent.api.trace.ClientIp;
import com.aiagent.api.trace.TraceId;
import com.aiagent.domain.log.entity.SysAccessLog;
import com.aiagent.domain.log.service.AccessLogService;
import com.aiagent.support.config.ConfigLookup;
import com.aiagent.support.config.props.HttpLogProps;
import com.aiagent.support.config.props.RequestCacheProps;
import com.aiagent.support.time.BusinessCalendar;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 사용 이력 — LOG-01 · backend/packages.md · screen/admin/system/log/access/requirements.md.
 *
 * <p><b>{@code afterCompletion} 이다.</b> 응답이 끝난 뒤라야 {@code HTTP_STAT} 을 알 수 있고,
 * 전문도 컨트롤러가 body 를 다 읽은 뒤라야 캐시에 차 있다.
 *
 * <p><b>등록은 인가 인터셉터보다 앞이다</b>(backend/packages.md · {@code InterceptorConfig}).
 * {@code preHandle} 이 거부해도 <b>이미 {@code true} 를 반환한 인터셉터의
 * {@code afterCompletion} 은 호출</b>되므로, 앞에 두어야 403 이 사용 이력에 남는다 —
 * 뒤에 두면 인가가 막은 요청이 통째로 사라진다.
 *
 * <h2>전 요청이 한 행이다</h2>
 *
 * <p>2xx 도 4xx 도 5xx 도 남는다(2026-08-28 개정 — screen/admin/system/log/access/requirements.md). 이전 규약은 예외가 난 요청을
 * 에러 로그에만 두는 배타였고, 그 배타가 sys/logs.md 의 전제를 깼다 — 감사 이력을 요청 전문
 * 로깅으로 <b>대체</b>했는데 실패한 시도가 빠지면 <i>"이 사용자가 무엇을 했나"</i> 에
 * 답하지 못한다.
 *
 * <h2>남기지 않는 요청</h2>
 *
 * <p><b>{@code /api/} 밖은 대상이 아니다</b>(backend/packages.md) — 정적 리소스와 SPA 라우트다.
 * {@link ApiLayer#of} 가 {@code null} 을 주는 것이 그 판정이며, <b>{@link ApiLayer#PUBLIC}
 * 은 남긴다</b>: 로그인 시도야말로 이력의 대상이다.
 *
 * <h2>자기 참조가 생긴다</h2>
 *
 * <p>사용 이력 화면에서 조회하면 그 조회가 이 테이블에 남고 <b>다음 조회에서 자기 행이 맨
 * 위에 보인다</b>. 고장이 아니다 — {@code MENU_CD = ACCESS_LOG} 로 걸러 보면 <i>누가 이력을
 * 얼마나 열람했는가</i> 가 나오며, 감사 이력을 두지 않기로 한 이 템플릿에서
 * <b>열람 감사에 가장 가까운 것</b>이 그 한 줄이다(screen/admin/system/log/access/requirements.md).
 */
@Component
public class UserActionLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UserActionLogInterceptor.class);

    private final AccessLogService accessLogs;
    private final RequestPayloadSerializer serializer;
    private final ConfigLookup configs;
    private final BusinessCalendar calendar;

    public UserActionLogInterceptor(
            AccessLogService accessLogs,
            RequestPayloadSerializer serializer,
            ConfigLookup configs,
            BusinessCalendar calendar) {
        this.accessLogs = accessLogs;
        this.serializer = serializer;
        this.configs = configs;
        this.calendar = calendar;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

        if (ApiLayer.of(RequestFacts.path(request)) == null || RequestFacts.path(request).startsWith("/api/portal/") || RequestFacts.path(request).equals("/api/integration/public/identity/receiveChange")) {
            return;
        }
        try {
            accessLogs.record(SysAccessLog.builder()
                    .userId(request.getAttribute("admin.audit.userId") instanceof String userId ? userId : RequestFacts.userId())
                    .menuCd(RequestFacts.menuCd(request))
                    .requestUri(RequestFacts.path(request))
                    .httpStat((short) response.getStatus())
                    .parameters(parameters(request))
                    .clientIp(ClientIp.of(request))
                    .traceId(TraceId.current())
                    .creDtm(calendar.now())
                    .build());
        } catch (RuntimeException e) {
            /*
              삼키는 자리가 여기다 — backend/packages.md. 트랜잭션 경계 <b>밖</b>이라야 rollback-only
              커밋이 던지는 것까지 잡힌다(AccessLogService 의 Javadoc).

              이력 테이블 하나가 서비스 전체를 멈추면 안 되고, 이 warn 이 <b>이력이 비는
              유일한 단서</b>다.
            */
            log.warn("사용 이력 기록 실패 uri={} — 응답에는 영향을 주지 않는다", request.getRequestURI(), e);
        }
    }

    // ── 값 만들기 ───────────────────────────────────────────────────────

    /**
     * 전문. <b>마스킹이 꺼져 있어도 직렬화는 한다</b> — 마스킹은 무엇을 가릴지의 문제이지
     * 담을지의 문제가 아니다. 담지 않으려면 {@code app.request-cache.enabled} 를 끈다.
     */
    private String parameters(HttpServletRequest request) {
        RequestCacheProps cache = configs.get(RequestCacheProps.class);
        if (!cache.enabled() || request.getAttribute(com.aiagent.api.auth.v2.SessionV2Filter.SCOPE) == null) {
            return null;
        }
        return serializer.serialize(request, configs.get(HttpLogProps.class), cache.maxBytes());
    }
}
