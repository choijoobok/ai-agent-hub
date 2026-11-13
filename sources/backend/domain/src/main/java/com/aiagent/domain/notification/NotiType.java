// @feat FEAT-509
package com.aiagent.domain.notification;

/**
 * 알림의 종류 — 기존 네 생산자와 CR-034 외부 사건. 공통코드가 아니다.
 *
 * <p>sys/notification.md 이 {@code RSLT_CD} 계열과 <b>같은 판단</b>으로 닫았다: 값은 <b>소스가 분기하는
 * 것</b>이라 관리자가 늘릴 일이 없고, 늘리면 <b>처리 코드가 없는 값</b>이 생긴다.
 * 공통코드로 두면 {@code SYS_YN='Y'} 로 잠가야 하는데(sys/code.md) 잠가 두면 관리자에게
 * 남는 것이 없다. 라벨만 메시지 키({@code noti.type.*})다.
 *
 * <h2>종류는 생산 경로를 나타낸다</h2>
 *
 * <p>screen/admin/system/notice/requirements.md 의 표와 1:1 이며, 그것이 이 열거형이 <b>목록 필터용 분류</b>가 아니라
 * <b>어디서 왔는가</b>인 이유다.
 *
 * <table><caption></caption>
 * <tr><td>{@link #REQUEST}</td><td>요청 처리·반려</td></tr>
 *   <tr><td>{@link #AUTH}</td><td>비밀번호 만료 예고 — AUTH-05</td></tr>
 *   <tr><td>{@link #AUTHZ}</td><td>권한 만료 예정 — AUTHZ-08</td></tr>
 * <tr><td>{@link #BATCH}</td><td>배치 실패 — BATCH-06(screen/admin/system/batch/schedule/requirements.md)</td></tr>
 * <tr><td>{@link #PORTAL_CHANGE}</td><td>외부 퇴직·조직 이동 — CR-034</td></tr>
 * </table>
 *
 * <p><b>{@code NOTICE} 가 없다.</b> 공지는 알림함에 행을 넣지 않는다 — 로그인 후 팝업으로
 * 이미 도달했고, 배지에 또 세면 같은 것을 두 번 알린다(frontend/tabs.md). 이 열거형에 자리가
 * 없다는 것이 그 결정의 집행이다.
 */
public enum NotiType {

    /** 요청 처리 결과 — screen/admin/system/notice/requirements.md. */
    REQUEST,

    /** 인증 관련(비밀번호 만료 예고 등) — AUTH-05. */
    AUTH,

    /** 인가 관련(권한 만료 예정 등) — AUTHZ-08. */
    AUTHZ,

    /** 배치 실패 — BATCH-06. */
    BATCH,

    /** 외부 퇴직·조직 이동 사건. AUTHZ 만료예고 중복 판정과 분리한다. */
    PORTAL_CHANGE
}
