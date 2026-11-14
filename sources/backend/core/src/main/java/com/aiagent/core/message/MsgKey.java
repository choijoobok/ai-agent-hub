package com.aiagent.core.message;

/**
 * 백엔드가 쓰는 메시지 키 — sys/message.md.
 *
 * <p><b>이 클래스가 곧 "백엔드가 어떤 키를 쓰는가" 의 전부다.</b> {@code generateMessageSeed}
 * 가 이 파일을 읽어 {@code SYS_MESSAGE_KEY} 시드를 만들고, 여기서 사라진 키는 다음 생성에서
 * {@code USE_SCOPE='NONE'}(죽은 키)으로 표시된다 — 그것이 죽은 키 판정의 유일한 경로다
 * (seed.md).
 *
 * <p>그래서 <b>리터럴을 쓰면 안 된다.</b> {@code "err.login.failed"} 를 어딘가에 직접 적으면
 * 그 키는 시드에 들어가지 않고, 관리 화면에 행이 없어 <b>번역할 방법이 없다</b>. 화면에는
 * 키 문자열이 그대로 보인다(sys/code.md). {@code ErrorCodeKeyTest} 가 {@link
 * com.aiagent.core.error.ErrorCode} 구현 전부에 대해 그것을 막는다.
 *
 * <p><b>필드 Javadoc 이 시드의 {@code MSG_DESC} 가 된다</b>(sys/message.md) — 관리 화면에서
 * 번역하는 사람이 보는 유일한 맥락이므로 <i>"언제 이 문구가 나가는가"</i> 를 적는다.
 *
 * <p>값이 아니라 <b>키만</b> 여기 있다. 한국어·영어는 {@code SYS_MESSAGE} 의 것이며 시드가
 * 열지 않는다 — {@link com.aiagent.core.error.ErrorCode#defaultMessage()} 는
 * 그 조회가 실패했을 때의 폴백이지 원본이 아니다.
 */
public final class MsgKey {

    /** Portal 배포 인증 모드에서 허용하지 않는 인증 동작. */
    public static final String ERR_PORTAL_AUTH_MODE = "err.portal.authMode";
    /** Portal 본인 비밀번호 변경 정책 위반. */
    public static final String ERR_PORTAL_PASSWORD_POLICY = "err.portal.passwordPolicy";
    /** 외부 인증 요청 또는 증거의 중복 소비. */
    public static final String ERR_PORTAL_AUTH_EVIDENCE_USED = "err.portal.authEvidenceUsed";

    /** 외부 퇴직 사건의 관리자 알림 제목. */
    public static final String NOTI_PORTAL_RETIRED = "noti.portal.retired";
    /** 외부 조직 이동 사건의 관리자 알림 제목. */
    public static final String NOTI_PORTAL_ORG_MOVED = "noti.portal.orgMoved";
    /** 개인정보를 담지 않는 외부 사건 알림 본문. */
    public static final String NOTI_PORTAL_CONTENT = "noti.portal.content";

    // ── 공통 실패 — CommonErrorCode ─────────────────────────────────

    /** 요청 값 검증 실패. 필드별 오류가 함께 나간다(backend/api.md). */
    public static final String ERR_VALIDATION = "err.validation";

    /** 세션이 없거나 만료됐다. 프론트가 로그인 화면으로 돌린다. */
    public static final String ERR_UNAUTHENTICATED = "err.unauthenticated";

    /** 인증 문맥/세션 저장소 판정 불가. API-601의503이며 인증 명령을 자동 재전송하지 않는다. */
    public static final String ERR_AUTH_CONTEXT_UNAVAILABLE = "AUTH_CONTEXT_UNAVAILABLE";

    /** 비밀번호 DB확정 뒤 새 세션발급 실패. 새 비밀번호로 재로그인하며 원 요청을 재전송하지 않는다. */
    public static final String ERR_PASSWORD_CHANGED_REAUTH_REQUIRED = "PASSWORD_CHANGED_REAUTH_REQUIRED";

    /** 인가 거부의 기본 문구. 원인 넷이 이 하나를 공유한다(backend/security.md). */
    public static final String ERR_FORBIDDEN = "err.forbidden";

    /** 없는 자원 · 오타 난 API 경로. */
    public static final String ERR_NOT_FOUND = "err.not-found";

    /** 허용되지 않은 HTTP 메서드. 서버 고장이 아니라 클라이언트 잘못이다. */
    public static final String ERR_METHOD_NOT_ALLOWED = "err.method-not-allowed";

    /** 요청 본문을 파싱하지 못했다. 어느 필드인지는 알 수 없다. */
    public static final String ERR_MALFORMED_BODY = "err.malformed-body";

    /**
     * 다른 사용자가 먼저 같은 값을 저장했다 — DB 제약이 막은 경우다.
     *
     * <p>화면의 중복 검사도 서버의 존재 확인도 <b>읽은 시점과 쓰는 시점 사이</b>를 막지
     * 못한다. 그 틈으로 들어온 것이 여기로 오며, 사용자가 할 일은 <b>다시 조회</b>다.
     */
    public static final String ERR_CONFLICT = "err.conflict";

    /** 처리하지 못한 예외. 사용자에게는 {@code traceId} 하나만 나간다(backend/security.md). */
    public static final String ERR_INTERNAL = "err.internal";

    // ── 로그인 실패 — AuthErrorCode(screen/_standalone/README.md) ────────────────────────

    /** 없는 ID <b>와</b> 비밀번호 불일치가 같은 문구로 나간다 — 갈리면 계정 열거다. */
    public static final String ERR_LOGIN_FAILED = "err.login.failed";

    /** 사용 중지된 계정({@code USE_YN='N'}). */
    public static final String ERR_LOGIN_DISABLED = "err.login.disabled";

    /** 사용 기간이 지난 계정. */
    public static final String ERR_LOGIN_EXPIRED = "err.login.expired";

    /** 연속 실패로 잠긴 계정. 재설정으로 풀린다. */
    public static final String ERR_LOGIN_LOCKED = "err.login.locked";

    /** 장기 미접속 휴면. 본인 확인을 마치면 해제된다(sys/user-role.md). */
    public static final String ERR_LOGIN_DORMANT = "err.login.dormant";

    /** 허용되지 않은 IP 에서의 접속. */
    public static final String ERR_LOGIN_IP_DENIED = "err.login.ip-denied";

    /** 유효 권한이 공집합이라 로그인시키지 않는다 — 빈 셸로 들여보내지 않는다(backend/security.md). */
    public static final String ERR_LOGIN_NO_AUTHORITY = "err.login.no-authority";

    /** 임시 비밀번호의 유효 시간이 지났다. */
    public static final String ERR_LOGIN_TEMP_EXPIRED = "err.login.temp-expired";

    /** {@code prevent-login: true} 에서 기존 세션이 살아 있어 신규 로그인을 거부했다. */
    public static final String ERR_LOGIN_CONCURRENT = "err.login.concurrent";

    /** 다른 곳의 로그인에 밀려 이 세션이 끝났다 — 유휴 만료와 뜻이 다르다(backend/security.md). */
    public static final String ERR_SESSION_EVICTED = "err.session.evicted";

    // ── 인가 거부 — AuthzErrorCode(backend/security.md) ───────────────────────────

    /** {@code SYS_API} 에 행이 없다. 고치는 곳은 API 마스터 시드다. */
    public static final String ERR_API_NOT_REGISTERED = "err.api-not-registered";

    /** API 행은 있으나 메뉴 매핑이 0건이다. */
    public static final String ERR_API_NOT_MAPPED = "err.api-not-mapped";

    /** API 는 정상이고 이 사람에게 권한이 없다. */
    public static final String ERR_NO_AUTHORITY = "err.no-authority";

    /** 세 계층 프리픽스 밖의 {@code /api/**} — 경로 차원의 fail-closed. */
    public static final String ERR_UNCLASSIFIED_API = "err.unclassified-api";

    /** 제한 세션. 비밀번호를 바꾸면 풀리므로 이것만 문구가 다르다. */
    public static final String ERR_PASSWORD_CHANGE_REQUIRED = "err.password-change-required";

    // ── 조회 프로토콜 — SearchErrorCode(backend/api.md) ───────────────────
    //
    // 사용자가 고칠 수 없는 실패다 — 화면이 만든 요청이 규약과 어긋난 것이며,
    // 조용히 빈 결과를 돌려주면 "조회가 안 된다" 로 묻힌다.

    /** 화이트리스트에 없는 필드로 조건이 왔다. 스펙에 {@code .col(…)} 이 없다. */
    public static final String ERR_QUERY_UNKNOWN_FIELD = "err.query.unknown-field";

    /** 연산자가 그 필드의 타입을 받지 않는다 — 숫자 필드의 {@code contains} 같은 것. */
    public static final String ERR_QUERY_OP_TYPE = "err.query.op-type";

    /** 값의 형태가 연산자와 맞지 않는다 — {@code between} 이 2원소가 아니거나 해석 불가. */
    public static final String ERR_QUERY_VALUE = "err.query.value";

    /** {@code .sortable()} 이 없는 필드로 정렬을 요청했다. 파생·긴 텍스트가 그렇다. */
    public static final String ERR_QUERY_NOT_SORTABLE = "err.query.not-sortable";

    /** 전량 조회의 상한을 넘었다. 조용히 자르면 <i>전량</i> 이 거짓말이 된다. */
    public static final String ERR_QUERY_TOO_MANY_ROWS = "err.query.too-many-rows";

    /** 커서를 읽을 수 없다. 조용히 첫 장으로 떨어뜨리면 <i>데이터가 없다</i> 로 읽힌다. */
    public static final String ERR_QUERY_INVALID_CURSOR = "err.query.invalid-cursor";

    /** 커서형에 정렬 컬럼이 둘 이상 왔다 — 기준점이 성립하려면 정렬 키가 하나여야 한다. */
    public static final String ERR_QUERY_CURSOR_SORT_ARITY = "err.query.cursor-sort-arity";

    // ── 사용 이력 — AccessLogErrorCode(screen/admin/system/log/access/requirements.md) ──────────────
    //
    // 셋 다 부하이거나 시간의 문제다. 이 화면에는 쓰기가 없어 저장 거부라는 것이 없다.

    /** 기간 없이 조회하려 했다 — 조건 없이 열면 첫 조회가 보존 기간 전량을 향한다. */
    public static final String ERR_ACCESS_LOG_RANGE_REQUIRED = "err.access-log.range-required";

    /** 기간이 상한을 넘었다. 조용히 자르면 좁은 기간을 봤다고 믿은 채 넓은 결론을 낸다. */
    public static final String ERR_ACCESS_LOG_RANGE_TOO_WIDE = "err.access-log.range-too-wide";

    /** 전문을 열려는데 그 행이 없다 — 조회 뒤에 파기 배치가 지나갔다. */
    public static final String ERR_ACCESS_LOG_PURGED = "err.access-log.purged";

    // ── 로그인 이력 — LoginLogErrorCode(screen/admin/system/log/access/requirements.md) ─────────────
    //
    // 둘뿐이고 둘 다 기간이다. 쓰기도 없고 두 번째 호출도 없어(nclob 컬럼이 없다)
    // 거부할 것이 기간 말고 없다. 위의 access-log 와 키를 나누는 이유는 상한과 보존이
    // 서로 다르기 때문이다 — 문구가 말하는 숫자가 다르다.

    /** 기간 없이 조회하려 했다 — 보존이 1년이라 조건 없이 열면 그 전량을 향한다. */
    public static final String ERR_LOGIN_LOG_RANGE_REQUIRED = "err.login-log.range-required";

    /** 기간이 92일을 넘었다 — 보존(1년)보다 창이 짧은 유일한 화면이다(screen/admin/system/log/access/requirements.md). */
    public static final String ERR_LOGIN_LOG_RANGE_TOO_WIDE = "err.login-log.range-too-wide";

    // ── 에러 로그 — ErrorLogErrorCode(screen/admin/system/log/access/requirements.md) ───────────────
    //
    // 사용 이력과 셋이 같다. 키를 나누는 이유도 같다 — 문구가 말하는 숫자가 다르다:
    // 상한은 92일이고 보존은 180일이라 "못 본 절반이 실제로 있다".

    /** 기간 없이 조회하려 했다 — 추적 ID 를 넣으면 건너뛴다(이 화면의 주된 동선이다). */
    public static final String ERR_ERROR_LOG_RANGE_REQUIRED = "err.error-log.range-required";

    /** 기간이 92일을 넘었다 — 보존은 180일이라 잘라 주면 못 본 절반이 남는다. */
    public static final String ERR_ERROR_LOG_RANGE_TOO_WIDE = "err.error-log.range-too-wide";

    /** 상세를 열려는데 그 행이 없다 — 조회 뒤에 파기 배치가 지나갔다. */
    public static final String ERR_ERROR_LOG_PURGED = "err.error-log.purged";

    // ── 다국어 메시지 관리 — MessageErrorCode(screen/admin/system/message/requirements.md) ─────────────

    /** 이미 있는 {@code MSG_KEY} 로 신규 행을 저장했다. 현재 페이지 밖은 화면이 모른다. */
    public static final String ERR_MESSAGE_KEY_DUPLICATE = "err.message.key-duplicate";

    /** 메시지 키는 소스 상수와 시드 생성기만 만들며 관리 API의 신규 행은 거부한다. */
    public static final String ERR_MESSAGE_KEY_CREATE_FORBIDDEN = "err.message.key-create-forbidden";

    /** 지원하지 않는 언어의 값이 왔다. 지원 언어 목록은 서버가 갖는다. */
    public static final String ERR_MESSAGE_LANG_UNKNOWN = "err.message.lang-unknown";

    /** 저장하려는 키가 이미 지워졌다 — 다른 사람이 먼저 지웠다. */
    public static final String ERR_MESSAGE_KEY_MISSING = "err.message.key-missing";

    // ── 시스템 환경설정 — ConfigErrorCode(screen/admin/system/code/requirements.md) ────────────────────

    /** 코드가 정하지 않은 {@code CFG_KEY} 가 왔다 — 아우터 조인의 왼쪽에 없는 키다. */
    public static final String ERR_CONFIG_KEY_UNKNOWN = "err.config.key-unknown";

    /** 되돌리려는데 오버라이드 행이 없다 — 다른 사람이 먼저 되돌렸다. */
    public static final String ERR_CONFIG_NOT_OVERRIDDEN = "err.config.not-overridden";

    /** 재바인딩이 값을 거부했다 — 타입·{@code @Min}·상호 제약이 전부 여기로 온다. */
    public static final String ERR_CONFIG_INVALID_VALUE = "err.config.invalid-value";

    // ── 공통 코드 관리 — CodeErrorCode(screen/admin/system/code/requirements.md) ───────────────────────
    //
    // 아홉 다 화면이 미리 막을 수 없는 것이다. 현재 페이지 밖의 코드값과 다른 사람의
    // 삭제·추가는 화면이 모르고, SYS_YN 과 참조 코드 화이트리스트는 서버가 갖는다.

    /** 이미 있는 {@code GRP_CD} 로 신규 행을 저장했다. */
    public static final String ERR_CODE_GROUP_DUPLICATE = "err.code.group-duplicate";

    /** 고치거나 지우려는 그룹이 이미 없다 — 다른 사람이 먼저 지웠다. */
    public static final String ERR_CODE_GROUP_MISSING = "err.code.group-missing";

    /** 코드가 남아 있어 그룹을 지울 수 없다 — 조회 뒤에 누군가 추가했다(screen/admin/system/code/requirements.md). */
    public static final String ERR_CODE_GROUP_HAS_CODE = "err.code.group-has-code";

    /** 이미 있는 {@code (GRP_CD, DTL_CD)} 로 신규 행을 저장했다. */
    public static final String ERR_CODE_DUPLICATE = "err.code.duplicate";

    /** 고치거나 지우려는 코드가 이미 없다. */
    public static final String ERR_CODE_MISSING = "err.code.missing";

    /** 그 그룹에 정의되지 않은 참조 코드로 값을 보냈다 — 화면이 만들 수 없는 조합이다. */
    public static final String ERR_CODE_REF_DEF_UNKNOWN = "err.code.ref-def-unknown";

    /** 이미 있는 {@code (GRP_CD, REF_CD)} 정의를 다시 만들려 했다. */
    public static final String ERR_CODE_REF_DEF_DUPLICATE = "err.code.ref-def-duplicate";

    /** {@code CODE_REF_KEY} 화이트리스트 밖의 참조 코드다 — 소스가 이 값을 문자열로 참조한다. */
    public static final String ERR_CODE_REF_CD_UNKNOWN = "err.code.ref-cd-unknown";

    /** 시스템 코드({@code SYS_YN='Y'})를 지우거나 끄려 했다 — sys/code.md. */
    public static final String ERR_CODE_SYSTEM_LOCKED = "err.code.system-locked";

    /** 그룹명·코드값 같은 필수 값이 비었다. 화면도 막지만 판정은 서버에도 있다. */
    public static final String ERR_CODE_REQUIRED = "err.code.required";

    // ── 사용자 설정 — UserErrorCode(frontend/tabs.md) ──────────────────────────

    /** 지원하지 않는 언어를 고르려 했다. 화면은 목록만 보여주지만 판정은 서버다. */
    public static final String ERR_USER_LOCALE_UNSUPPORTED = "err.user.locale-unsupported";

    // ── 내 프로필 · 비밀번호 변경 — UserErrorCode(frontend/tabs.md · AUTH-05) ──
    //
    // 넷 다 **사용자가 고칠 수 있는** 실패다. 그래서 문구가 규칙을 말한다 — 무엇이
    // 틀렸는지 알려주지 않고 거절하면 세 번째 시도에서 전화가 온다.

    /** 현재 비밀번호가 다르다. <b>새 비밀번호 쪽 문제와 갈라야 한다</b> — 합치면 무엇을 고칠지 모른다. */
    public static final String ERR_USER_PWD_MISMATCH = "err.user.pwd-mismatch";

    // 문구에 정책 값을 넣지 않는다 — 응답 문구는 코드로만 조회되고 인자 치환이 없다
    // (ErrorMessages). 대신 화면이 규칙을 **미리** 보여준다(PasswordRule).
    /** 새 비밀번호가 정책의 최소 길이·문자 종류를 못 채웠다. */
    public static final String ERR_USER_PWD_WEAK = "err.user.pwd-weak";

    /** 최근 N건 안에 있던 비밀번호다 — 건수는 정책이 정한다(sys/user-role.md). */
    public static final String ERR_USER_PWD_REUSED = "err.user.pwd-reused";

    /** 지금 쓰는 것과 같다. 재사용 이력에는 아직 없으므로 별도 판정이다. */
    public static final String ERR_USER_PWD_UNCHANGED = "err.user.pwd-unchanged";

    // ── 분실 재설정 — PasswordResetErrorCode(AUTH-07 · login 요건서 §6-5) ──
    //
    // <b>발송 실패의 코드가 없다.</b> 계정이 없어도 메일이 안 나가도 응답이 같아야 하므로
    // (계정 열거 방지) 실패를 알릴 자리가 아예 없다 — 그 사실은 서버 로그에만 남는다.

    /** 인증번호가 맞지 않다. 남은 시도 횟수를 알려주지 않는다. */
    public static final String ERR_RESET_CODE_INVALID = "err.reset.code-invalid";

    /**
     * 만료 · 이미 사용 · 시도 한도 초과 — <b>셋이 한 문구다</b>.
     *
     * <p>사용자가 할 일이 셋 다 같기 때문이다(①로 돌아가 다시 받는다). 갈라 두면
     * <i>"이 아이디에 유효한 토큰이 있었다"</i> 를 알려주는 응답이 생긴다.
     */
    public static final String ERR_RESET_CODE_EXPIRED = "err.reset.code-expired";

    // ── 비밀번호 정책 문구 — 정책 API 가 완성해 내려준다(login 요건서 §7-1) ──
    //
    // <b>실패 문구(ERR_USER_PWD_*)와 다른 축이다.</b> 저쪽은 <i>거절당했다</i> 이고 이쪽은
    // <i>무엇을 지켜야 하나</i> 다. 화면은 입력 중에 이 문구로 체크리스트를 그리고, 제출
    // 뒤의 거절은 저쪽 문구를 받는다.
    //
    // <b>여기에는 인자 치환이 있다</b>({@link MsgFormat}) — 정책 값이 런타임에 바뀌므로
    // 숫자를 문구에 굳힐 수 없다. 응답 실패 문구에 치환이 없는 것과 갈리는 자리다.

    /** 최소 길이 규칙 — {@code {min}}. */
    public static final String PWD_RULE_MIN_LENGTH = "pwd.rule.min-length";

    /** 문자 종류 수 규칙 — {@code {kinds}}. */
    public static final String PWD_RULE_CHARSET_KINDS = "pwd.rule.charset-kinds";

    /** 재사용 금지 규칙 — {@code {count}}. 서버만 판정한다. */
    public static final String PWD_RULE_NO_REUSE = "pwd.rule.no-reuse";

    // ── 메일 본문 — Mailer 가 그대로 싣는다(login 요건서 §8) ──────────
    //
    // 파일 템플릿을 두지 않는 것이 결정이다. 여기 두면 다국어(수신자의 LOCALE_CD)와 운영 중
    // 수정이 메시지 관리 화면에서 공짜로 따라온다 — 템플릿 파일은 그 둘을 다시 만들게 한다.

    /** 재설정 인증번호 메일의 제목. */
    public static final String MAIL_PWD_RESET_SUBJECT = "mail.pwdReset.subject";

    /** 재설정 인증번호 메일의 본문 — {@code {code}} · {@code {minutes}}. */
    public static final String MAIL_PWD_RESET_BODY = "mail.pwdReset.body";

    // ── 저장된 조회조건 — SearchCondErrorCode(UX-07 · frontend/search-form.md) ────────

    // 상한 값을 문구에 넣지 않는다 — 응답 문구에 인자 치환이 없고(ErrorMessages),
    // 화면은 목록 길이로 이미 그것을 알고 있다.
    /** 한 화면에 저장할 수 있는 조회조건 수를 넘겼다. */
    public static final String ERR_SEARCH_COND_LIMIT = "err.searchCond.limit";

    // ── 분산 락 — LockErrorCode(backend/persistence.md) ──────────────────────────────

    // 대기할 수 있으면 대기한다(WAIT). 이 문구가 나가는 것은 **즉시 실패로 선언한 자리**
    // 뿐이며, 사용자가 할 수 있는 일이 *잠시 후 다시* 하나라 문구도 그 하나를 말한다.
    /** 같은 자원을 다른 작업이 잡고 있다. 경합은 정상이므로 400 대다. */
    public static final String ERR_LOCK_CONFLICT = "err.lock.conflict";

    // ── 메뉴 트리 — MenuErrorCode(sys/menu.md · screen/menu/requirements.md) ────────────────────
    //
    // 화면도 같은 것을 막지만 그것은 **UX 이지 방어가 아니다**(backend/security.md). 여기 문구가 나가는
    // 것은 화면 밖 경로이거나 **동시성으로 뚫린** 경우이며, 후자가 실제로 일어난다.

    /**
     * 보낸 형제 목록이 <b>지금의 자식 집합과 다르다</b> — sys/menu.md 의 집합 검증.
     *
     * <p>나머지와 성질이 다르다: <i>해서는 안 되는 일</i>이 아니라 <i>보던 트리가 낡았다</i>
     * 이며, <b>조회 한 번이 복구</b>다. 문구가 그 한 줄을 말한다.
     */
    public static final String ERR_MENU_TREE_CHANGED = "err.menu.tree-changed";

    /** 자기 자신 또는 자손 아래로 옮기려 했다 — 순환 네 층 중 <b>저장 서비스</b>(sys/menu.md). */
    public static final String ERR_MENU_CYCLE = "err.menu.cycle";

    /** 리프 포함 5단을 넘는다 — 서브트리를 통째로 옮기면 <b>그 높이까지 더해</b> 본다(sys/menu.md). */
    public static final String ERR_MENU_DEPTH = "err.menu.depth";

    /** 자식이 있는 노드에 URL 을 채우려 했다 — URL 과 자식은 배타다(backend/security.md). */
    public static final String ERR_MENU_URL_ON_PARENT = "err.menu.url-on-parent";

    /** URL 이 있는 노드 아래에 자식을 만들거나 옮기려 했다 — 〃. */
    public static final String ERR_MENU_CHILD_UNDER_LEAF = "err.menu.child-under-leaf";

    /**
     * 권한 행이 있는 노드의 URL 을 비우려 했다 — screen/menu/requirements.md.
     *
     * <p>비우는 순간 권한 대상이 아닌데 {@code SYS_ROLE_MENU_AUTH} 에 행이 남는다 —
     * sys/menu.md 가 <i>"들어오면 거부"</i> 라고 한 상태가 <b>거꾸로</b> 만들어진다.
     */
    public static final String ERR_MENU_URL_REQUIRED = "err.menu.url-required";

    /** 다른 메뉴가 이미 가진 URL 을 저장하려 했다 — 메뉴 URL은 화면 하나와만 연결된다. */
    public static final String ERR_MENU_URL_DUPLICATE = "err.menu.url-duplicate";

    /** {@code MENU_CD} 중복 — 다른 사람이 방금 만든 것을 클라이언트가 알 수 없다(frontend/grid.md). */
    public static final String ERR_MENU_DUPLICATE = "err.menu.duplicate";

    /** 삭제 게이트 — 자식·매핑 API·매핑 역할·개인 예외 중 하나라도 남아 있다(screen/menu/requirements.md). */
    public static final String ERR_MENU_NOT_DELETABLE = "err.menu.not-deletable";

    /** 없는 메뉴를 가리켰다. 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_MENU_NOT_FOUND = "err.menu.not-found";

    // ── 역할 · 역할 권한 — RoleErrorCode(screen/menu/requirements.md · sys/menu.md) ────────
    //
    // 앞의 넷이 **역할 자체의 생애**(screen/menu/requirements.md)이고 뒤의 셋이 **권한 부여**(screen/menu/requirements.md)다. 파일을
    // 가르지 않는 것은 화면 둘이 같은 저장 서비스 하나를 쓰지 않기 때문이 아니라, 두 화면이
    // **같은 테이블 짝**을 다루기 때문이다 — 역할을 지우려면 부여가 0 건이어야 한다.

    /** {@code ROLE_ID} 중복 — 다른 사람이 방금 만든 것을 클라이언트가 알 수 없다(frontend/grid.md). */
    public static final String ERR_ROLE_DUPLICATE = "err.role.duplicate";

    /** 없는 역할을 가리켰다. 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_ROLE_NOT_FOUND = "err.role.not-found";

    /**
     * 삭제 게이트 — 보유 사용자나 권한 행이 남아 있다(screen/menu/requirements.md).
     *
     * <p><b>거의 항상 삭제가 아니라 {@code USE_YN='N'} 이다.</b> 이 게이트를 통과하는 역할은
     * <b>한 번도 쓰이지 않은 것</b>뿐이며 그것이 정확히 <i>잘못 만든 역할</i> 이다 — 쓰인
     * 역할을 지우면 <i>누가 무슨 역할을 가졌었나</i> 가 사라진다(sys/menu.md).
     */
    public static final String ERR_ROLE_NOT_DELETABLE = "err.role.not-deletable";

    /** 역할명을 비운 채 저장했다 — screen/menu/requirements.md 의 필수 컬럼. */
    public static final String ERR_ROLE_NAME_REQUIRED = "err.role.name-required";

    /**
     * 구조 노드나 없는 메뉴에 권한을 주려 했다 — sys/menu.md 의 {@code MENU_URL IS NULL} 거부.
     *
     * <p>화면에는 구조 노드에 스위치가 아예 없다(screen/menu/requirements.md). 이 문구가 나가는 것은 화면 밖
     * 경로이거나, 트리를 열어 둔 사이에 그 메뉴가 폴더가 된 경우다.
     */
    public static final String ERR_ROLE_AUTH_MENU = "err.role.auth.menu";

    /**
     * 그 메뉴에 정의되지 않은 액션이다 — screen/admin/system/user/requirements.md 의 {@code —}.
     *
     * <p><b>줄 것이 없는 조합</b>이다. 받아 두면 인가 데이터가 <i>줬는데 안 되는</i> 상태를
     * 갖게 되고, 그 다음 문의는 <i>"권한을 줬는데 왜 안 되나"</i> 가 된다.
     *
     * <p>{@code EXPORT} 는 API 가 없어도 정의될 수 있으므로(sys/menu.md) 이유를 API 의 부재로
     * 대지 않는다.
     */
    public static final String ERR_ROLE_AUTH_ACTION = "err.role.auth.action";

    /**
     * {@code READ} 없이 다른 액션만 왔다 — screen/admin/system/user/requirements.md 의 <b>불변식 하나</b>.
     *
     * <p>화면이 이것을 <b>유지</b>하므로(끄면 함께 끄고, 켜면 함께 켠다) 정상 경로로는 오지
     * 않는다. 서버가 다시 보는 것은 sys/menu.md 의 원칙 P1 이며 {@code MENU_URL IS NULL} 거부와
     * <b>같은 자리</b>다.
     */
    public static final String ERR_ROLE_AUTH_READ = "err.role.auth.read";

    // ── 사용자 관리 — UserAdminErrorCode(screen/admin/system/user/requirements.md) ─────────────────────
    //
    // **위의 `err.user.*` 와 축이 다르다.** 저기는 **본인이** 자기 계정에 대해 하는 일이고
    // 여기는 **관리자가 남의 계정에** 하는 일이다 — 같은 접두를 쓰면 관리 화면에서 문구를
    // 고치는 사람이 로그인한 사용자 전원이 보는 문구를 건드리게 된다.

    /** {@code USER_ID} 중복 — 다른 사람이 방금 만든 것을 클라이언트가 알 수 없다(frontend/grid.md). */
    public static final String ERR_USER_ADMIN_DUPLICATE = "err.user-admin.duplicate";

    /** 없는 계정을 가리켰다. 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_USER_ADMIN_NOT_FOUND = "err.user-admin.not-found";

    /** 이름을 비운 채 저장했다 — screen/admin/system/user/requirements.md 의 필수 컬럼. */
    public static final String ERR_USER_ADMIN_NAME_REQUIRED = "err.user-admin.name-required";

    /**
     * 이메일을 비운 채 저장했다 — screen/admin/system/user/requirements.md 의 필수 컬럼.
     *
     * <p><b>필수인 이유가 발송처</b>다. 관리자가 발급한 임시 비밀번호를 전할 곳이 없으면
     * 그 계정은 만들어지자마자 들어올 수 없다(AUTH-07).
     */
    public static final String ERR_USER_ADMIN_EMAIL_REQUIRED = "err.user-admin.email-required";

    /** 사용 기간의 시작이 종료보다 늦다 — 그 계정은 어느 날도 쓸 수 없다(sys/INDEX.md). */
    public static final String ERR_USER_ADMIN_PERIOD = "err.user-admin.period";

    /**
     * 삭제 행이 왔다 — {@code SYS_USER} 는 <b>물리 삭제를 하지 않는다</b>(sys/user-role.md · USER-02).
     *
     * <p>화면에는 삭제 컬럼이 아예 없다(screen/admin/system/user/requirements.md). 못 쓰게 하는 수단은 {@code USE_YN='N'} 이며
     * 그것은 <b>셀 편집</b>이다 — 이력 테이블이 {@code USER_ID} 를 값으로 갖고 있어(sys/user-role.md)
     * 행을 지우면 <b>지난 이력의 주인이 사라진다</b>.
     */
    public static final String ERR_USER_ADMIN_NO_DELETE = "err.user-admin.no-delete";

    /**
     * 자기 계정을 비활성화하려 했다 — screen/admin/system/user/requirements.md.
     *
     * <p><b>권한이 아니라 대상 제약</b>이라 화면은 감추지 않고 <b>보이되 막고 사유를
     * 붙인다</b>(frontend/tabs.md 의 예외가 아니라 축이 다른 것이다). 그 자리에서 화면 밖으로 밀려나기
     * 때문이며, 되돌릴 사람이 그 사람 자신이다.
     */
    public static final String ERR_USER_ADMIN_SELF_DISABLE = "err.user-admin.self-disable";

    /**
     * 임시 비밀번호 발급이 꺼져 있다 — {@code sys-config.auth.password.admin-reset-enabled}.
     *
     * <p>기본값이 켜짐인 것이 결정이다(sys/code.md) — 메일 연동이 없는 배포에서 잠긴 계정을 푸는
     * <b>유일한 경로</b>이며, 끄면 복구 수단이 사라진다.
     */
    public static final String ERR_USER_ADMIN_RESET_DISABLED = "err.user-admin.reset-disabled";

    // ── 배치 스케줄 — BatchScheduleErrorCode(screen/admin/system/batch/schedule/requirements.md) ─────────────────
    //
    // **화면이 미리 막는 것이 거의 없다.** 62 의 삭제 게이트는 마스터의 건수 컬럼이 근거를
    // 목록에 실어 주지만, 여기서 막는 것들은 대개 **서버만 아는 사실**이다 — 크론의 최소
    // 간격은 파서가 있어야 알고(screen/admin/system/batch/schedule/requirements.md), 등록된 빈 목록은 스프링 컨텍스트가 안다.

    /** {@code BATCH_ID} 중복 — PK 이자 락 키다(backend/persistence.md). */
    public static final String ERR_BATCH_DUPLICATE = "err.batch.duplicate";

    /** 없는 등록을 가리켰다. 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_BATCH_NOT_FOUND = "err.batch.not-found";

    /** 배치명을 비운 채 저장했다 — screen/admin/system/batch/schedule/requirements.md 의 필수 컬럼. */
    public static final String ERR_BATCH_NAME_REQUIRED = "err.batch.name-required";

    /**
     * 설명을 비운 채 저장했다 — sys/batch.md · screen/admin/system/batch/schedule/requirements.md.
     *
     * <p><b>설명이 필수인 유일한 테이블</b>이다. 끄는 확인 창이 이 문장을 그대로 읽어 주며
     * (screen/admin/system/batch/schedule/requirements.md), 비어 있으면 그 확인이 아무 말도 하지 않는다 — 운영자가 배치를 끄는 순간은
     * 대개 장애 대응 중이고 그때 소스를 열 시간은 없다.
     */
    public static final String ERR_BATCH_DESC_REQUIRED = "err.batch.desc-required";

    /**
     * 실행 빈이 없거나 <b>등록된 {@code BatchJob} 구현이 아니다</b> — screen/admin/system/batch/schedule/requirements.md.
     *
     * <p>화면은 셀렉트로 고르게 해서 이 경로를 닫지만, 화면 밖 경로는 그 셀렉트를 지나지
     * 않는다. 막지 않으면 <b>실행 시점에 터지고</b> 그때는 크론이 깨운 새벽이다(원칙 P1).
     */
    public static final String ERR_BATCH_BEAN_UNKNOWN = "err.batch.bean-unknown";

    /** 크론 표현식을 파서가 읽지 못했다 — 스케줄러가 쓰는 그 파서다(screen/admin/system/batch/schedule/requirements.md). */
    public static final String ERR_BATCH_CRON_INVALID = "err.batch.cron-invalid";

    /**
     * <b>아는 시간대 이름이 아니다</b> — screen/admin/system/batch/schedule/requirements.md §6-1 의 13.
     *
     * <p>화면은 셀렉트라 이 값이 만들어지지 않는다. <b>API 를 직접 부르는 경로</b>의 마지막
     * 방어이며, 고정 오프셋(<code>+09:00</code>)도 여기서 걸린다 — 파싱은 되지만 서머타임이
     * 없어 <i>현지 시각을 유지한다</i> 는 그 컬럼의 목적을 조용히 깬다(backend/time.md).
     */
    public static final String ERR_BATCH_TIMEZONE_INVALID = "err.batch.timezone-invalid";

    /**
     * <b>쿨다운이 크론 최소 간격보다 길다</b> — backend/persistence.md 이 <i>등록 거부</i> 로 정한 그것.
     *
     * <p>같은 값이어도 거부한다 — 경계에서 한 주기가 통째로 누락될 수 있다. 이 조건이
     * 막으려는 사고가 <i>실행이 <b>조용히</b> 건너뛰어진다</i> 이며, 조용하기 때문에
     * 저장 시점에 막는 것 말고는 알릴 자리가 없다.
     *
     * <p><b>오류가 두 셀에 걸린다</b>(screen/admin/system/batch/schedule/requirements.md) — 어느 쪽이 틀렸는지 서버가 말할 수 없다.
     * 틀린 것은 값이 아니라 <b>관계</b>이며, 그래서 문구가 두 값을 함께 읽어 준다.
     */
    public static final String ERR_BATCH_COOLDOWN_TOO_LONG = "err.batch.cooldown-too-long";

    /** {@code PARAM_JSON} 이 JSON 이 아니다 — 프론트도 보고 서버도 본다(screen/admin/system/batch/schedule/requirements.md · frontend/grid.md). */
    public static final String ERR_BATCH_PARAM_JSON = "err.batch.param-json";

    /**
     * 지울 수 없다 — <b>켜져 있거나 실행 중</b>이다(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p><b>실행 이력은 게이트가 아니다.</b> 이력에 FK 가 없으므로(sys/batch.md) 등록을 지워도
     * 행은 남으며 남아야 한다 — 이력이 게이트가 되면 <b>오래된 배치는 영영 못 지운다</b>.
     */
    public static final String ERR_BATCH_NOT_DELETABLE = "err.batch.not-deletable";

    /**
     * 즉시 실행이 거부됐다 — <b>이미 실행 중</b>이다(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p><b>거부 사유가 이것 하나</b>다. 쿨다운은 수동이 우회하고, {@code USE_YN='N'} 은
     * 막지 않는다 — 꺼 둔 배치를 한 번 돌려 보는 것이 정상 동선이다.
     *
     * <p>문구에 <b>노드와 시각</b>이 들어간다. <i>"실행 중입니다"</i> 만 내면 운영자는
     * 얼마나 기다려야 하는지 모른다 — backend/persistence.md 이 {@code NODE_ID} 를 남긴 것과 같은 이유다.
     */
    public static final String ERR_BATCH_ALREADY_RUNNING = "err.batch.already-running";

    // ── 배치 실행 이력 — BatchHistoryErrorCode(screen/admin/system/batch/schedule/requirements.md) ───────────────

    /** {@code [보기]} 를 눌렀는데 그 사이 파기됐다 — 보존 1년(retention.md · screen/admin/system/batch/schedule/requirements.md). */
    public static final String ERR_BATCH_HIST_PURGED = "err.batch-hist.purged";

    /**
     * 중단 대상이 아니다 — <b>{@code RUNNING} 이 아니다</b>(screen/admin/system/batch/schedule/requirements.md).
     *
     * <p>화면이 {@code eligible} 로 이미 걸렀지만 그것은 <b>고른 시점의 사실</b>이고, 그
     * 사이 배치가 끝났을 수 있다. <b>조용히 건너뛰지 않고 거절하는 쪽</b>이 맞다 — 끝난
     * 행에 요청을 세우면 <i>영원히 응답하지 않을 요청</i>이 이력에 남는다.
     */
    public static final String ERR_BATCH_HIST_NOT_CANCELABLE = "err.batch-hist.not-cancelable";

    // ── 개인 예외 권한 — UserAuthErrorCode(screen/admin/system/user/requirements.md) ───────────────────
    //
    // **위의 `err.role.auth.*` 와 뜻이 겹치는 것이 셋 있다**(메뉴 · 액션 · READ). 같은 규칙을
    // 두 화면이 지키기 때문인데(sys/menu.md 원칙 P1) **키를 공유하지 않는다** — 문구를 고치는
    // 사람이 *역할 권한 관리* 를 보며 쓴 말이 *개인 예외 권한* 에 그대로 나가면, 두 화면의
    // 안내가 같은 것을 다르게 부르게 된다. 규칙이 하나인 것과 문구가 하나인 것은 다르다.

    /** 없는 계정에 예외를 주려 했다. 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_USER_AUTH_NOT_FOUND = "err.user-auth.not-found";

    /**
     * 구조 노드나 없는 메뉴에 예외를 주려 했다 — sys/menu.md 의 {@code MENU_URL IS NULL} 거부.
     *
     * <p>화면에는 구조 노드에 스위치가 아예 없다(screen/admin/system/user/requirements.md). 이 문구가 나가는 것은 화면 밖
     * 경로이거나, 트리를 열어 둔 사이에 그 메뉴가 폴더가 된 경우다.
     */
    public static final String ERR_USER_AUTH_MENU = "err.user-auth.menu";

    /**
     * 그 메뉴에 정의되지 않은 액션이다 — screen/admin/system/user/requirements.md 의 {@code —}.
     *
     * <p><b>줄 것이 없는 조합</b>이다. 받아 두면 <i>줬는데 안 되는</i> 상태가 인가 데이터에
     * 남고, 그 다음 문의는 <i>"권한을 줬는데 왜 안 되나"</i> 가 된다.
     */
    public static final String ERR_USER_AUTH_ACTION = "err.user-auth.action";

    /**
     * {@code READ} 없이 다른 액션만 남는다 — screen/admin/system/user/requirements.md 의 <b>불변식 하나</b>.
     *
     * <p><b>역할 쪽과 판정이 다르다.</b> 거기서는 그 역할 안에서만 봤지만 여기서는 유효
     * 권한이 <b>역할과 개인 예외의 합집합</b>이므로(backend/security.md ③), <b>역할이 조회를 주고 있으면
     * 개인 예외에는 저장만 있어도 된다</b> — 실제로 그것이 이 화면의 흔한 쓰임이다.
     */
    public static final String ERR_USER_AUTH_READ = "err.user-auth.read";

    /** 기간을 비운 채 저장했다 — 개인 예외에는 <b>무기한이라는 선택지가 없다</b>(sys/menu.md). */
    public static final String ERR_USER_AUTH_PERIOD_REQUIRED = "err.user-auth.period-required";

    /** 시작이 종료보다 늦다 — 그 예외는 어느 날도 유효하지 않다(sys/INDEX.md). */
    public static final String ERR_USER_AUTH_PERIOD = "err.user-auth.period";

    /**
     * 사유를 비운 채 저장했다 — <b>필수</b>다(AUTHZ-07).
     *
     * <p>변경 이력 테이블을 두지 않았으므로(sys/logs.md) <i>"누구에게 왜 무엇을 언제까지
     * 줬는가"</i> 를 남기는 것이 이 컬럼뿐이다.
     */
    public static final String ERR_USER_AUTH_REASON_REQUIRED = "err.user-auth.reason-required";

    /**
     * 같은 {@code (메뉴, 액션)} 이 두 번 왔거나 이미 있는 행을 새로 만들려 했다.
     *
     * <p>PK 가 {@code (USER_ID, MENU_CD, ACTION_CD)} 이므로 <b>한 조합에 행은 하나뿐</b>이며,
     * 재부여는 새 행이 아니라 <b>같은 행의 기간·사유를 갱신하는 것</b>이다(screen/admin/system/user/requirements.md).
     */
    public static final String ERR_USER_AUTH_DUPLICATE = "err.user-auth.duplicate";

    /** 고치거나 지우려는 행이 없다 — 다른 사람이 먼저 지웠다. */
    public static final String ERR_USER_AUTH_MISSING = "err.user-auth.missing";

    // ── 사용자 디테일 탭 — UserDetailErrorCode(screen/admin/system/user/requirements.md) ─────────────
    //
    // **`err.user-admin.*` 와 축이 다르다.** 저기는 `SYS_USER` 한 테이블의 일이고 여기는
    // **그 계정에 매달린 것들**의 일이다 — 보유 역할과 접속 IP. 편집이 있는 탭이 그 둘뿐이라
    // 여기 있는 것도 그 둘의 것이며, 강제 로그아웃의 거부는 세션이 도메인 밖이라 `api` 가 낸다.

    /** 없는 계정에 무언가를 매달려 했다 — FK 가 막기 전에 우리가 먼저 말한다. */
    public static final String ERR_USER_DETAIL_USER = "err.user-detail.user";

    /** 없는 역할을 줬다 — 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_USER_DETAIL_ROLE = "err.user-detail.role";

    /**
     * 이미 가진 역할을 또 줬다 — PK 가 {@code (USER_ID, ROLE_ID)} 다(sys/menu.md).
     *
     * <p>재부여는 <b>같은 행의 기간을 갱신</b>하는 것이지 새 행이 아니다. 만료된 부여가
     * 목록에 남아 있는데 다시 주려고 행을 추가하면 이 오류가 나며, 할 일은 <b>그 행의
     * 종료일을 미는 것</b>이다.
     */
    public static final String ERR_USER_DETAIL_ROLE_DUPLICATE = "err.user-detail.role-duplicate";

    /** 고치거나 지우려는 부여가 없다 — 다른 사람이 먼저 정리했다. */
    public static final String ERR_USER_DETAIL_ROLE_MISSING = "err.user-detail.role-missing";

    /** 부여 기간의 시작이 종료보다 늦다 — 그 부여는 어느 날도 유효하지 않다(sys/INDEX.md). */
    public static final String ERR_USER_DETAIL_PERIOD = "err.user-detail.period";

    /**
     * IP 도 CIDR 도 아니다 — sys/user-role.md 이 DDL 에 두지 않은 검사다.
     *
     * <p>벤더 중립 DDL 에 정규식 {@code CHECK} 를 둘 수 없고(backend/persistence.md), 둘 수 있어도 IPv6
     * 축약까지 정규식으로 맞추는 것은 <b>저장 시 파싱보다 약하다</b>.
     */
    public static final String ERR_USER_DETAIL_IP = "err.user-detail.ip";

    /** 같은 대역을 두 번 넣었다 — PK 가 {@code (USER_ID, IP_CIDR)} 다(sys/user-role.md). */
    public static final String ERR_USER_DETAIL_IP_DUPLICATE = "err.user-detail.ip-duplicate";

    /** 고치거나 지우려는 대역이 없다. */
    public static final String ERR_USER_DETAIL_IP_MISSING = "err.user-detail.ip-missing";

    /**
     * 자기 세션을 끊으려 했다 — screen/admin/system/user/requirements.md. <b>그 자리에서 화면 밖으로 밀려난다.</b>
     *
     * <p>사용 여부 {@code 'N'} 전환과 <b>같은 이유로 막고 다른 자리에서 막는다</b>: 저것은
     * 셀 편집이라 컬럼이 막고, 이것은 실행이라 <b>버튼이 비활성</b>이며 서버가 다시 본다.
     */
    public static final String ERR_USER_SESSION_SELF = "err.user-session.self";

    /**
     * 세션 인덱스가 없어 활성 세션을 나열할 수 없다 — backend/session.md · backend/security.md.
     *
     * <p><b>빈 목록으로 답하지 않는다.</b> 그러면 <i>"이 사람은 지금 아무 데도 안 들어와
     * 있다"</i> 로 읽히는데 그것은 <b>모르는 것을 아는 것처럼</b> 말하는 것이다 — 이 탭이
     * 하는 일이 <i>끊을까 말까</i> 이므로 그 거짓말의 대가가 크다.
     *
     * <p>스토어가 있는데 인덱스가 없는 배포는 {@code SecurityConfig} 가 <b>기동에서</b>
     * 막으므로, 여기 닿는 것은 {@code store-type: none} 하나이며 그것은 <b>선택된
     * 동작</b>이다(단일 노드 · 개발).
     */
    public static final String ERR_USER_SESSION_INDEX = "err.user-session.index";

    // ── 공지사항 — NoticeErrorCode(screen/admin/system/notice/requirements.md) ───────────────────────────

    /** 제목·본문·게시 시작이 비었다 — 셀 단위 오류로 돌려준다(frontend/grid.md). */
    public static final String ERR_NOTICE_REQUIRED = "err.notice.required";

    /** 없는 공지다 — 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_NOTICE_NOT_FOUND = "err.notice.not-found";

    /** 게시 종료가 시작보다 앞이다. */
    public static final String ERR_NOTICE_PERIOD = "err.notice.period";

    /**
     * <b>팝업 기간이 게시 기간을 벗어났다</b> — screen/admin/system/notice/requirements.md.
     *
     * <p>이 검증이 없으면 <b>목록에 없는 공지가 팝업으로 뜬다</b>. 팝업의
     * <i>"공지 N건 더 있음"</i> 링크가 알림 모달의 공지 탭을 여는데 그 탭은 게시 기간을
     * 보므로, 눌러도 그 공지가 없다 — 팝업이 도달 보장 장치라는 전제가 거기서 깨진다.
     */
    public static final String ERR_NOTICE_POPUP_RANGE = "err.notice.popup-range";

    /** 팝업을 켰는데 팝업 기간이 비었다 — screen/admin/system/notice/requirements.md. */
    public static final String ERR_NOTICE_POPUP_PERIOD = "err.notice.popup-period";

    // ── 요청함 — NotiRequestErrorCode(screen/admin/system/notice/requirements.md) ────────────────────────

    /** 없는 요청이다. */
    public static final String ERR_NOTI_REQ_NOT_FOUND = "err.noti-req.not-found";

    /**
     * <b>이미 처리된 요청이 선택에 섞였다</b> — screen/admin/system/notice/requirements.md.
     *
     * <p>처리 가능한 것만 골라 실행하지 않는다. 일부만 처리하면 <b>알림이 일부에게만
     * 나가고</b> 사용자는 무엇이 처리됐는지 모른다 — 되돌리는 길이 없으므로
     * 부분 성공을 만들지 않는다.
     */
    public static final String ERR_NOTI_REQ_NOT_RECEIVED = "err.noti-req.not-received";

    /**
     * <b>반려인데 사유가 비었다</b> — sys/notification.md 이 필수로 정했다.
     *
     * <p>사유 없는 반려는 요청자에게 아무 정보도 주지 않는다. 그러면 같은 요청을 다시
     * 내고, 그것이 워크플로를 만들지 않기로 한 대가를 실제 비용으로 바꾼다 —
     * 단계가 없으므로 <b>한 번의 답이 전부</b>여야 한다.
     */
    public static final String ERR_NOTI_REQ_MEMO_REQUIRED = "err.noti-req.memo-required";

    // ── 내 알림 — NotiInboxErrorCode(frontend/tabs.md · screen/_standalone/README.md) ─────────────────

    /**
     * <b>내 알림이 아니다</b> — 남의 번호이거나 이미 파기됐다(보존 90일 · retention.md).
     *
     * <p>둘을 갈라 말하지 않는다. <i>"남의 것"</i> 이라고 답하면 <b>그 번호가 실재한다</b>는
     * 사실을 알려 주게 되고, 알림은 사람마다 행이 다르므로(sys/notification.md) 그 사실 자체가 정보다.
     *
     * <p>조용히 넘기지 않는 이유는 화면이 <i>읽음으로 바뀐 줄 알고</i> 배지를 깎기
     * 때문이다 — 새로고침하면 숫자가 되돌아온다.
     */
    public static final String ERR_NOTI_INBOX_NOT_FOUND = "err.noti-inbox.not-found";

    /** 요청 내용이 비었다 — sys/notification.md 이 {@code REQ_CONTENT} 를 필수로 두었다. */
    public static final String ERR_NOTI_REQ_CONTENT_REQUIRED = "err.noti-req.content-required";

    /**
     * <b>같은 유형의 접수 상태 요청이 이미 있다.</b>
     *
     * <p>워크플로가 없으므로 요청은 <b>한 번 내면 답이 올 때까지 그대로</b>다 —
     * 같은 것을 또 내도 진행이 빨라지지 않고 대기열만 늘어난다. 그리고 관리자는 그 여벌을
     * <b>하나씩 반려</b>해야 하는데, 반려마다 요청자에게 알림이 나간다(screen/admin/system/notice/requirements.md).
     *
     * <p>막는 것은 <b>같은 유형</b>뿐이다. 권한 신청과 잠금 해제는 서로 다른 답을 기다리는
     * 것이라 함께 열려 있어야 한다.
     */
    public static final String ERR_NOTI_REQ_DUPLICATE = "err.noti-req.duplicate";

    // ── 샘플 — SampleErrorCode(playbook/INDEX.md) ───────────────────────────────
    //
    // 접두가 err.sample. 이다 — playbook/INDEX.md 이 요구한 기계가 알아볼 접두이며, 샘플을 걷어낼 때
    // 제거 changelog 의 LIKE 한 줄이 이 키들을 잡는다. 하나라도 다른 접두를 쓰면 그 키만
    // 남아 관리 화면의 고아가 되고, 그 누락은 조용하다.

    /** 주문번호·주문명·고객이 비었다 — 셀 단위 오류로 돌려준다(frontend/grid.md). */
    public static final String ERR_SAMPLE_REQUIRED = "err.sample.required";

    /** 없는 주문이다 — 화면이 낡았거나 화면 밖 경로다. */
    public static final String ERR_SAMPLE_NOT_FOUND = "err.sample.not-found";

    /** 이미 있는 주문번호다 — {@code UK_SMPL_ORDER_NO} 를 사람이 읽는 문구로 옮긴다. */
    public static final String ERR_SAMPLE_DUPLICATED = "err.sample.duplicated";

    /**
     * 잠긴 주문이다 — {@code LOCK_YN} 은 상태와 <b>다른 축</b>이다(playbook/INDEX.md).
     *
     * <p>상태는 <i>일이 어디까지 갔나</i> 이고 이것은 <i>운영이 잠갔나</i> 다. 합치면
     * 완료된 주문의 잠금을 풀 방법이 없어진다.
     */
    public static final String ERR_SAMPLE_LOCKED = "err.sample.locked";

    /**
     * <b>품목이 있는 주문은 지울 수 없다</b> — frontend/grid.md 의 마스터 삭제 게이트다.
     *
     * <p>화면이 체크박스를 열지 않지만 그것만으로는 부족하다: 화면 밖 경로가 실재하고,
     * FK 의 {@code RESTRICT} 는 <i>제약 위반</i> 이라는 <b>읽을 수 없는 문구</b>로만
     * 답한다(playbook/INDEX.md).
     */
    public static final String ERR_SAMPLE_HAS_ITEMS = "err.sample.has-items";

    /**
     * 확정 상태가 아니다 — 출고 지시는 {@code CFM} 에서만 열린다(playbook/INDEX.md 의 전이표).
     *
     * <p>화면이 {@code eligible} 로 대상을 거르지만(frontend/grid.md), 고른 뒤 남이 상태를 바꾸면
     * 그 판정은 <b>낡은 값</b>이다.
     */
    public static final String ERR_SAMPLE_NOT_SHIPPABLE = "err.sample.not-shippable";

    /**
     * 접수 상태가 아니다 — 확정은 {@code RCV} 에서만 열린다(playbook/INDEX.md 의 전이표).
     *
     * <p>{@link #ERR_SAMPLE_NOT_SHIPPABLE} 과 같은 자리이며, 갈라 두는 것은 사용자가 보는
     * 문장이 <b>어느 전이가 막혔는지</b>를 말해야 하기 때문이다 — 하나로 합치면
     * <i>상태가 맞지 않습니다</i> 가 되어 무엇을 해야 할지가 사라진다.
     */
    public static final String ERR_SAMPLE_NOT_CONFIRMABLE = "err.sample.not-confirmable";

    /**
     * <b>금액이 잠긴 주문의 품목을 다시 세려 했다</b> — playbook/INDEX.md 의 행×디테일 재계산.
     *
     * <p>{@link #ERR_SAMPLE_NOT_CONFIRMABLE} 와 갈라 두는 것은 <b>사용자가 할 일이 다르기</b>
     * 때문이다. 확정은 <i>지금 하려는 일</i> 이 막힌 것이고, 이쪽은 <i>이미 확정된 금액</i>
     * 이라 막힌 것이다 — 같은 문구로 말하면 <b>확정을 먼저 하라는 뜻</b>으로 읽힌다.
     */
    public static final String ERR_SAMPLE_NOT_RECALCULABLE = "err.sample.not-recalculable";

    /**
     * 기간을 비우고 조회했다 — ②는 <b>기간이 부하의 상한</b>인 화면이다(playbook/INDEX.md · screen/admin/system/log/access/requirements.md).
     *
     * <p>화면의 {@code required} 가 먼저 막지만 <b>도구·스크립트는 그 폼을 지나지 않는다</b>.
     */
    public static final String ERR_SAMPLE_RANGE_REQUIRED = "err.sample.range-required";

    /** 조회 기간이 상한을 넘었다 — 조용히 자르지 않는다(screen/admin/system/log/access/requirements.md). */
    public static final String ERR_SAMPLE_RANGE_TOO_WIDE = "err.sample.range-too-wide";

    /**
     * 할인율이 있는데 비고가 없다 — <b>조건부 필수</b>(playbook/INDEX.md · frontend/grid.md).
     *
     * <p>{@link #ERR_SAMPLE_REQUIRED} 와 갈라 두는 것은 그 문구가 <i>주문번호·주문명·고객·
     * 주문일</i> 넷을 열거하기 때문이다. 조건부 필수를 거기 얹으면 <b>사용자에게 틀린 칸을
     * 가리킨다</b> — 다 채웠는데 왜 필수라는지 알 수 없고, 그 넷을 다시 들여다보게 된다.
     *
     * <p>실기동에서 드러났다(2026-09-02). 화면의 {@code required} 함수가 먼저 막으므로
     * 평소에는 이 문구가 보이지 않지만, <b>보이는 순간은 화면 밖 경로</b>라 더 정확해야 한다.
     */
    public static final String ERR_SAMPLE_RMK_REQUIRED = "err.sample.rmk-required";

    /**
     * 태그 행의 필수 — <b>태그명과 정렬순서</b>(playbook/INDEX.md).
     *
     * <p>{@link #ERR_SAMPLE_REQUIRED} 와 갈라 두는 이유가 {@link #ERR_SAMPLE_RMK_REQUIRED}
     * 와 같다: 그 문구는 <i>주문</i> 의 넷을 열거하는데 여기서 빈 것은 <b>태그 탭의 행</b>이라,
     * 얹으면 사용자가 마스터를 들여다보게 된다.
     */
    public static final String ERR_SAMPLE_TAG_REQUIRED = "err.sample.tag-required";

    /**
     * 태그의 정렬순서가 겹친다 — playbook/INDEX.md.
     *
     * <p><b>순서가 값이라서 막는다.</b> 같은 {@code SORT_NO} 가 둘이면 읽는 순서가 정해지지
     * 않고, 사용자에게는 <i>고치지도 않았는데 조회할 때마다 태그가 뒤바뀐다</i> 로 보인다 —
     * 그 증상을 없애려고 둔 컬럼이 그 증상을 만들게 된다.
     *
     * <p>DB 의 유일 제약이 아니라 <b>서비스가</b> 막는다. 제약을 걸면 ①의 전량 교체가
     * <b>같은 트랜잭션 안에서 옛 행과 새 행이 겹치는 순간</b>에 걸리고(지웠다 넣는다),
     * 그 실패는 사용자가 읽을 수 없는 문구로만 답한다.
     */
    public static final String ERR_SAMPLE_TAG_SORT_DUPLICATED = "err.sample.tag-sort-duplicated";

    /**
     * 한 주문의 태그가 상한을 넘었다 &mdash; 48 &sect;8 의 결정(2026-09-02).
     *
     * <p><b>문구가 숫자를 그대로 적는다.</b> <i>태그가 너무 많습니다</i> 만으로는 몇 개를
     * 빼야 하는지 알 수 없고, 사용자는 하나 지우고 저장하기를 반복한다 &mdash; 56
     * &sect;3.3 이 <i>건수를 먼저 알려준다</i> 로 정한 것과 같은 이유다.
     * {@link #ERR_SAMPLE_RANGE_TOO_WIDE} 가 <i>31일</i> 을 적은 것과 같은 자리이며,
     * 응답 문구는 {@code SYS_MESSAGE} 에서 오므로 <b>인자를 끼울 통로가 없다</b>
     * ({@code BusinessException} 은 서버 언어의 로그 문구만 나른다).
     *
     * <p><b>저장 둘이 이 문구를 쓴다.</b> &#9312;은 배열의 개수로 &#9316;은 행의 개수로
     * 걸리는데, 사용자에게 사실은 하나다 &mdash; <i>이 주문에 태그가 몇 개까지</i> 다.
     * 갈라 쓰면 같은 사실이 두 문구로 설명된다.
     */
    public static final String ERR_SAMPLE_TAG_TOO_MANY = "err.sample.tag-too-many";

    /**
     * 엑셀의 주문번호가 DB 에 없다 — playbook/INDEX.md.
     *
     * <p>{@link #ERR_SAMPLE_NOT_FOUND} 와 갈라 두는 것은 <b>사용자가 할 일이 다르기</b>
     * 때문이다. 저것은 <i>화면을 다시 조회하세요</i> 이고(남이 지웠다), 이것은 <b>파일의
     * 그 칸을 고치는</b> 일이다 — 다시 조회해 봐야 없는 주문번호는 그대로다.
     */
    public static final String ERR_SAMPLE_NO_SUCH_ORDER = "err.sample.no-such-order";

    /**
     * 한 주문에 같은 품목코드가 둘이다 — playbook/INDEX.md.
     *
     * <p>{@link #ERR_SAMPLE_DUPLICATED} 는 <i>주문번호</i> 의 중복이라 문구가 그 낱말을
     * 쓴다. 여기서 겹친 것은 <b>주문 안의 품목</b>이며, 파일 안에서 겹쳤을 수도 이미 있는
     * 행과 겹쳤을 수도 있다 — 어느 쪽이든 사용자가 볼 곳은 <b>그 두 줄</b>이다.
     */
    public static final String ERR_SAMPLE_ITEM_DUPLICATED = "err.sample.item-duplicated";

    // @feat FEAT-040 — 공통 관리 저장 판정
    /** 관리 저장 입력이 올바르지 않습니다. */
    public static final String ERR_MANAGED_VALIDATION = "err.managed.validation";
    /** 현재 관리 변경 권한이 없습니다. */
    public static final String ERR_MANAGED_FORBIDDEN = "err.managed.forbidden";
    /** 기준 정보가 변경되었습니다. 다시 조회해 주세요. */
    public static final String ERR_MANAGED_REVISION_CONFLICT = "err.managed.revisionConflict";
    /** 현재 참조 때문에 변경할 수 없습니다. */
    public static final String ERR_MANAGED_REFERENCE_CONFLICT = "err.managed.referenceConflict";
    /** 다른 저장 의도에 사용한 명령 키입니다. */
    public static final String ERR_MANAGED_COMMAND_CONFLICT = "err.managed.commandConflict";
    /** 보존 기간이 종료된 명령입니다. */
    public static final String ERR_MANAGED_COMMAND_EXPIRED = "err.managed.commandExpired";
    /** 정확한 과거 구성을 확인할 수 없습니다. */
    public static final String ERR_MANAGED_HISTORY_UNAVAILABLE = "err.managed.historyUnavailable";
    /** 관리 저장 원천을 확인할 수 없습니다. 원 명령으로 결과를 확인해 주세요. */
    public static final String ERR_MANAGED_UNAVAILABLE = "err.managed.unavailable";

    private MsgKey() {}
}
