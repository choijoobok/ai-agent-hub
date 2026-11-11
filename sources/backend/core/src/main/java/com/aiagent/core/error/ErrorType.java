package com.aiagent.core.error;

/**
 * 에러가 어떤 HTTP 상태로 나가는지. 코드마다 숫자를 적지 않고 정의된 상태 중 하나를 고른다.
 *
 * <p>목록은 backend/api.md 의 상태 코드 표와 1:1 이다 — 200 은 여기 없다(성공은 에러가 아니다).
 */
public enum ErrorType {

    /** 검증 실패 · 조회조건 위반. 폼이 필드별 오류를 표시한다. */
    INVALID_REQUEST(400),

    /** 세션 만료. 프론트는 재인증 모달을 띄우고 로그아웃하지 않는다(frontend/packages.md). */
    UNAUTHENTICATED(401),

    /** 인가 거부. {@code code} 가 갈래를 나른다(backend/security.md). */
    FORBIDDEN(403),

    /**
     * 경합 — 읽은 뒤 쓰기 전에 남이 먼저 썼다. <b>400 이 아니다</b>: 요청은 옳았고 세상이
     * 바뀌었을 뿐이라, 사용자가 할 일이 <i>입력을 고치는 것</i> 이 아니라 <b>다시 조회</b>다.
     */
    CONFLICT(409),

    /** 그 밖. 스택트레이스를 응답에 담지 않는다 — {@code traceId} 만 준다(backend/security.md). */
    INTERNAL_ERROR(500),

    /** 인증 문맥·저장소 판정 불가. 안전 실패하며 자동 명령 재시도를 요구하지 않는다. */
    SERVICE_UNAVAILABLE(503);

    private final int status;

    ErrorType(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
