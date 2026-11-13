package com.aiagent.api.error;

import com.aiagent.api.trace.TraceId;
import com.aiagent.core.error.BusinessException;
import com.aiagent.core.error.CommonErrorCode;
import com.aiagent.core.error.ErrorCode;
import com.aiagent.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 봉투로 바꾼다 — backend/api.md.
 *
 * <p><b>스택트레이스를 응답에 담지 않는다</b>(backend/security.md). 사용자가 받는 것은 {@code traceId}
 * 하나이고, 상세는 서버 로그와 에러 로그 테이블에 남는다.
 *
 * <h2>여기가 에러 로그의 유일한 적재 지점이다</h2>
 *
 * <p>{@code SYS_LO_EXCEPTION} 한 행이 여기서 난다(LOG-03 · screen/admin/system/log/access/requirements.md). <b>봉투를 만드는 자리와
 * 같은 자리</b>여야 하는 이유가 있다 — 테이블의 {@code ERR_CD} · {@code ERR_MSG} 는
 * <i>응답에 나간 바로 그 값</i> 이며(backend/packages.md), 다른 곳에서 적으면 둘이 갈릴 수 있고 갈리는
 * 순간 <b>운영자가 보는 화면과 사용자가 받은 화면이 다른 말을 한다</b>.
 *
 * <p>적재의 규칙(401 제외 · 4xx 는 스택 없음 · 실패는 삼킴)은 {@link ExceptionLogRecorder}
 * 가 갖는다. 여기 있는 것은 <b>어느 핸들러가 무엇을 넘기는가</b> 뿐이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    public static final String HANDLED_FAILURE = GlobalExceptionHandler.class.getName() + ".handledFailure";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorMessages errorMessages;
    private final ExceptionLogRecorder exceptionLogs;

    public GlobalExceptionHandler(ErrorMessages errorMessages, ExceptionLogRecorder exceptionLogs) {
        this.errorMessages = errorMessages;
        this.exceptionLogs = exceptionLogs;
    }

    /**
     * 업무 규칙 위반. 로그 수준을 나눈다 — 로그인 실패(401)는 <b>정상 흐름</b>이라 WARN 으로
     * 쌓으면 로그가 실패 로그로 뒤덮인다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            HttpServletRequest request, BusinessException e) {
        ErrorCode code = e.errorCode();
        // 로그에는 예외가 든 문구(서버 언어), 응답에는 사용자 언어 — 다른 축이다(ErrorMessages).
        log.debug("업무 예외 {} — {}", code.code(), e.getMessage());
        /*
          <b>403 의 세 갈래가 여기를 지난다</b> — 인가 인터셉터가 던지는 것이 이 예외이며
          (backend/security.md), 그 갈래를 나르는 것이 `ERR_CD` 다(screen/admin/system/log/access/requirements.md). 401 만 기록기가 걸러 낸다.
        */
        return envelope(request, code.type().status(), code, e.fieldErrors(), e.rowErrors(), e);
    }

    /**
     * <b>읽은 뒤 쓰기 전에 남이 먼저 썼다</b> — 409 다.
     *
     * <p>화면의 중복 검사도 서비스의 존재 확인도 이 틈을 막지 못한다. 막는 것은 DB 제약
     * 하나뿐이고, 그것이 걸리면 여기로 온다. <b>500 이면 안 된다</b>: 서버가 고장난 것이
     * 아니라 <i>정상적으로 일어나는 경합</i> 이며, 500 로그는 사람을 깨운다(위의 405 와 같은 이유).
     *
     * <p><b>{@code rows} 를 채우지 않는다.</b> 제약 위반은 flush 시점에 나므로 여기서는
     * <i>어느 행이었나</i> 를 알 수 없다 — 짐작해 채우면 없는 정보를 있는 것처럼 보이게
     * 한다(위의 {@code handleMalformedBody} 와 같은 규칙). 행을 짚어야 하는 화면은
     * <b>서비스가 이 예외를 잡아 다시 검사하고</b> {@link BusinessException#rows} 로 바꿔
     * 던진다 — 그 비용은 예외 경로에서만 든다(backend/api.md).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            HttpServletRequest request, DataIntegrityViolationException e) {
        ErrorCode code = CommonErrorCode.CONFLICT;
        // WARN 이다 — 정상 흐름은 아니지만 고장도 아니라, 잦아지면 보고 싶은 종류다.
        if (com.aiagent.api.log.SensitiveRequest.excluded(request)) log.warn("인증 요청 무결성 제약 위반");
        else log.warn("무결성 제약 위반 — {}", e.getMostSpecificCause().getMessage());
        return envelope(request, code.type().status(), code, List.of(), e);
    }

    /** 필드별 오류는 <b>배열</b>이다 — 관계가 틀린 검증은 같은 문구가 두 필드에 실린다(backend/api.md). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            HttpServletRequest request, MethodArgumentNotValidException e) {
        List<BusinessException.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new BusinessException.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();

        ErrorCode code = CommonErrorCode.VALIDATION;
        return envelope(request, code.type().status(), code, fields, e);
    }

    /**
     * 존재하지 않는 경로. <b>500 이 아니라 404 여야 한다</b> — SPA 폴백이
     * {@code /api/**} 를 넘기지 않으므로(build-deploy.md) 오타 난 API 경로가 여기로 온다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(
            HttpServletRequest request, NoResourceFoundException e) {
        ErrorCode code = CommonErrorCode.NOT_FOUND;
        log.debug("없는 경로 {}", e.getResourcePath());
        return envelope(request, 404, code, List.of(), e);
    }

    /**
     * 잘못된 HTTP 메서드. <b>500 이 아니라 405 여야 한다</b>(2026-08-29 실측).
     *
     * <p>backend/security.md 는 <i>"잘못된 메서드는 프레임워크가 막는다 — Spring MVC 가 405 로 거부하며
     * 인터셉터에 닿지 않는다"</i> 고 전제했다. 앞 절반은 맞다 — 인터셉터에 닿지 않는다.
     * <b>뒤 절반이 틀렸다</b>: {@link HttpRequestMethodNotSupportedException} 을 아무도 잡지
     * 않아 아래 포괄 핸들러가 받고 <b>500 + ERROR 로그</b>가 된다. 인가 슬라이스의 403 을
     * 확인하다 드러났다.
     *
     * <p>그냥 지저분한 것이 아니다. 500 은 <b>서버가 고장났다</b>는 뜻이고 그 로그는 사람을
     * 깨운다. 클라이언트가 메서드를 틀린 것이 그것과 같은 신호를 내면 안 된다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
        ErrorCode code = CommonErrorCode.METHOD_NOT_ALLOWED;
        log.debug("허용되지 않은 메서드 {}", e.getMethod());
        return envelope(request, 405, code, List.of(), e);
    }

    /**
     * 깨진 요청 본문. <b>클라이언트 잘못이므로 400 이다</b> — 같은 이유로 500 에서 옮긴다.
     *
     * <p>{@code fields} 를 채우지 않는다. 파싱이 실패했으므로 어느 필드가 문제인지 알 수
     * 없고, 짐작해 채우면 <b>없는 정보를 있는 것처럼</b> 보이게 한다(backend/api.md).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedBody(
            HttpServletRequest request, HttpMessageNotReadableException e) {
        ErrorCode code = CommonErrorCode.MALFORMED_BODY;
        log.debug("본문을 읽지 못했다 type={}", e.getClass().getSimpleName());
        return envelope(request, code.type().status(), code, List.of(), e);
    }

    /**
     * <b>스택이 남는 유일한 갈래다</b> — 500 이기 때문이다(screen/admin/system/log/access/requirements.md).
     *
     * <p>위의 다섯은 전부 4xx 이고 그 스택은 프레임워크의 프레임뿐이라 전 행이 같다.
     * 여기만 <i>무엇이 어디서 터졌나</i> 를 담는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(HttpServletRequest request, Exception e) {
        ErrorCode code = CommonErrorCode.INTERNAL;
        // traceId 가 로그와 응답 양쪽에 있어야 사용자가 들고 온 값으로 이 줄을 찾을 수 있다.
        if (com.aiagent.api.log.SensitiveRequest.excluded(request))
            log.error("인증 요청 처리 실패 traceId={} type={}", TraceId.current(), e.getClass().getSimpleName());
        else log.error("처리하지 못한 예외 traceId={}", TraceId.current(), e);
        return envelope(request, code.type().status(), code, List.of(), e);
    }

    /**
     * 봉투를 만들고 <b>같은 값으로</b> 에러 로그 한 행을 남긴다.
     *
     * <p><b>문구를 한 번만 만드는 것이 요점이다.</b> {@link ErrorMessages#of} 를 두 번 부르면
     * 두 값이 <i>이론적으로는</i> 같지만 실제로는 갈릴 수 있다 — 그 사이 메시지 캐시가
     * 갱신되면(sys/code.md) 사용자가 받은 문구와 테이블에 남은 문구가 다르다. 한 변수를 둘이
     * 나눠 쓰면 갈릴 자리가 없다.
     *
     * <p><b>순서도 요점이다.</b> 기록이 먼저고 응답이 나중이다 — 기록기가 실패를 삼키므로
     * (그 클래스의 Javadoc) 이 순서가 응답을 늦추지도 막지도 않는다.
     */
    private ResponseEntity<ApiResponse<Void>> envelope(
            HttpServletRequest request,
            int status,
            ErrorCode code,
            List<BusinessException.FieldError> fields,
            Throwable cause) {
        return envelope(request, status, code, fields, List.of(), cause);
    }

    private ResponseEntity<ApiResponse<Void>> envelope(
            HttpServletRequest request,
            int status,
            ErrorCode code,
            List<BusinessException.FieldError> fields,
            List<BusinessException.RowError> rows,
            Throwable cause) {
        request.setAttribute(HANDLED_FAILURE, Boolean.TRUE);
        String message = errorMessages.of(code);
        exceptionLogs.record(request, status, code, message, cause);
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(code.code(), message, fields, rows, TraceId.current()));
    }
}
