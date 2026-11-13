// @feat FEAT-501
import { get, post } from '@/shared/api';
import type { AuthSession } from '@/shared/stores/authStore';
import { AUTH_ENDPOINTS } from '@/modules/system/public/auth/api/endpoints';

export interface LoginForm {
  loginId: string;
  password: string;
}

export interface HeartbeatResult {
  expiresInSeconds: number;
}

export async function login(form: LoginForm): Promise<AuthSession> {
  await post<AuthSession>(AUTH_ENDPOINTS.login, form);
  // 발급 응답만으로 셸을 열지 않는다. 새 쿠키의 현재 상태/첫 사용을 서버에서 확인한다.
  return fetchMe();
}

/** 새로고침 시 세션 복원. 세션이 없으면 401 이며 그것이 정상 응답이다. */
export function fetchMe(): Promise<AuthSession> {
  return get<AuthSession>(AUTH_ENDPOINTS.getMe);
}

export function heartbeat(): Promise<HeartbeatResult> {
  return post<HeartbeatResult>(AUTH_ENDPOINTS.heartbeat);
}

export function logout(): Promise<void> {
  return post<void>(AUTH_ENDPOINTS.logout);
}

// ── 분실 비밀번호 재설정 (AUTH-07 · login 요건서 §6-3) ─────────────────

/**
 * 비밀번호 규칙 하나 — 서버가 목록의 <b>유일한 출처</b>다.
 *
 * <p>`message` 는 번역과 인자 치환이 <b>끝난</b> 문자열이다. 프론트가 문구를 만들지 않으므로
 * 즉시 검증 문구와 제출 뒤 거절 문구가 갈릴 수 없다(pwd-change 요건서 §6-2).
 */
export interface PasswordRuleSpec {
  id: string;
  params: Record<string, number>;
  /** `false` 면 프론트가 **건너뛴다** — 서버가 잡으며 그것이 실패의 안전한 방향이다. */
  clientCheckable: boolean;
  message: string;
}

/**
 * 재설정 화면이 그릴 두 숫자.
 *
 * <p>**화면이 타이머를 센다** — 서버가 남은 초를 말하면 존재하는 계정에만 쿨다운이 걸린다는
 * 사실이 계정 열거 단서가 된다(요건서 §6-3). 그래서 값은 받되 **경과는 받지 않는다**.
 */
export interface PasswordResetLimits {
  tokenValidMinutes: number;
  resendCooldownSeconds: number;
}

export interface PasswordPolicy {
  rules: PasswordRuleSpec[];
  reset: PasswordResetLimits;
}

/** 재설정 ① 진입 시 한 번. 강제 변경·프로필도 **같은 응답**을 쓴다. */
export function fetchPasswordPolicy(): Promise<PasswordPolicy> {
  return get<PasswordPolicy>(AUTH_ENDPOINTS.getPasswordPolicy);
}

/**
 * ① 인증번호 받기 — **계정 존재 여부와 무관하게 항상 성공한다**.
 *
 * <p>실패로 갈리는 것은 입력 검증(빈 값·길이)뿐이다. 서버가 쿨다운도 말하지 않으므로
 * 남은 시간을 그리는 것은 **화면의 몫**이다(요건서 §6-3).
 */
export function sendResetCode(loginId: string): Promise<void> {
  return post<void>(AUTH_ENDPOINTS.sendResetCode, { loginId });
}

/** ② 인증번호 확인. 실패는 `err.reset.code-invalid` 또는 `err.reset.code-expired` 다. */
export function verifyResetCode(loginId: string, resetCode: string): Promise<void> {
  return post<void>(AUTH_ENDPOINTS.verifyResetCode, { loginId, resetCode });
}

/**
 * ③ 새 비밀번호 저장 — **여기서** 잠금이 풀린다.
 *
 * <p>②의 코드를 다시 싣는다. 서버가 *확인됨* 상태를 갖지 않기 때문이며, 그래야 그 상태의
 * 수명과 무효화를 정할 필요가 없다.
 */
export function saveResetPassword(
  loginId: string,
  resetCode: string,
  newPwd: string,
): Promise<void> {
  return post<void>(AUTH_ENDPOINTS.saveResetPassword, { loginId, resetCode, newPwd });
}
