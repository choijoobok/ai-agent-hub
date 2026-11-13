// @feat FEAT-501
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Form, Input, Typography } from 'antd';
import { ApiError } from '@/shared/api';
import { useAuthStore } from '@/shared/stores/authStore';
import { login, type LoginForm } from '@/modules/system/public/auth/api/authApi';
import { PasswordResetFlow } from '@/modules/system/public/auth/components/PasswordResetFlow';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { useMessageStore } from '@/shared/i18n/messageStore';
import { useCodeStore } from '@/shared/code/codeStore';

/**
 * 재설정으로 가는 길이 배너 안에도 있어야 하는 실패들 — screen/_standalone/README.md · login 요건서 §6-4.
 *
 * <p>잠긴 사람과 휴면인 사람은 <b>지금 막혀 있고 스스로 풀 수 있다</b>. 폼 아래의 평소
 * 링크에만 의존하면 복구 경로가 안 보여 전부 문의로 가고, 문의가 유일한 경로가 되면
 * <b>관리자 발급이 정상 경로</b>가 된다 — 그것은 메일 불가 환경의 마지막 수단이지
 * 일상 경로가 아니다.
 */
const RECOVERABLE_CODES: ReadonlySet<string> = new Set([
  'err.login.locked',
  'err.login.dormant',
]);

/**
 * 로그인 — screen/_standalone/README.md · {@code documents/screen/_standalone/system/login/requirements.md}.
 *
 * <p><b>실패 문구를 화면이 만들지 않는다.</b> 서버가 완성해 내려준 {@code message} 를 배너에
 * 그대로 넣는다(backend/packages.md). 존재하지 않는 ID 와 비밀번호 불일치를 같은 문구로 유지하는
 * 책임이 <b>서버 한 곳</b>에 있어야 하기 때문이다 — 프론트가 조립하면 언젠가 갈리고, 갈리는
 * 순간 계정 열거가 된다(screen/_standalone/README.md).
 *
 * <p>안내는 <b>폼 상단 한 곳</b>에 낸다(screen/_standalone/README.md). 필드별로 흩으면 어느 쪽이 틀렸는지
 * 알려주는 꼴이 된다.
 *
 * <p><b>재설정은 라우트가 아니라 이 화면의 단계다</b>(요건서 §1). 나누면 뒤로 가기로 ②에
 * 직접 들어올 수 있고, 그 화면은 코드를 받은 적 없는 사람에게 인증번호 칸을 보여 준다.
 *
 * <p><b>§6-4 의 링크만 {@code code} 로 분기한다.</b> 문구가 아니라 <b>배치</b>이므로 화면의
 * 판단이며, 새 버튼을 만들지 않는 것이 요건이다 — 만들면 <i>잠긴 사용자만 보는 버튼</i>이
 * 생기고 그 상태를 따로 그려야 한다.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const signIn = useAuthStore((state) => state.signIn);

  const [error, setError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [resetting, setResetting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<LoginForm>();

  function openReset() {
    setError(null);
    setNotice(null);
    setResetting(true);
  }

  function closeReset(completed: boolean) {
    setResetting(false);
    // 완료했으면 새 비밀번호로 로그인하라고 말한다. 여기서 세션을 만들지 않는 것이
    // 서버의 결정이며(인증번호를 아는 것이 곧 로그인이 되면 접근 제어 체인을 건너뛴다),
    // 화면은 그 결정을 안내로 잇는다.
    setNotice(completed ? label(MSG.RESET_COMPLETED) : null);
  }

  async function handleSubmit(values: LoginForm) {
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      const session = await login(values);
      signIn(session);
      // 여기가 의 비교 자리다 — LOCALE_CD 가 정본이 되고, 버전이 같으면 캐시를
      // 그대로 쓴다. 다른 PC 의 첫 로그인이면 여기서 언어가 갈아 끼워진다.
      await useMessageStore.getState().syncWithSession(
        session.user.localeCd,
        session.cacheVersions?.message,
      );
      // 코드 캐시도 같은 자리에서 맞춘다 — 왕복이 없다(sys/code.md). 버전이 같으면 아무 일도
      // 하지 않으므로, 로그인할 때마다 코드를 다시 받지 않는다.
      useCodeStore.getState().syncWithSession(session.cacheVersions?.code);
      // react-router 7 의 navigate 는 Promise 를 돌려준다. 기다릴 것이 없으므로 void 로 뗀다.
      void navigate('/admin', { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : null);
      // 입력값은 유지하고 비밀번호만 비운다 — screen/_standalone/README.md.
      form.setFieldValue('password', '');
    } finally {
      setSubmitting(false);
    }
  }

  if (resetting) {
    return (
      <PasswordResetFlow
        initialLoginId={form.getFieldValue('loginId') as string | undefined}
        onExit={closeReset}
      />
    );
  }

  return (
    <>
      <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
        {label(MSG.LOGIN_TITLE)}
      </Typography.Title>

      {error && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          // antd 6 에서 message 는 title 로 바뀌었다.
          title={error.message}
          description={
            RECOVERABLE_CODES.has(error.code) ? (
              // 배너 **본문 안**의 링크다(요건서 §6-4). 버튼이 아닌 것이 요건이다.
              <Button type="link" style={{ padding: 0, height: 'auto' }} onClick={openReset}>
                {label(MSG.LOGIN_LOCKED_RESET)}
              </Button>
            ) : // 5xx·네트워크 오류의 문의 열쇠다. 로그인 실패(401)에는 보여줄 이유가 없다.
            error.status >= 500 && error.traceId ? (
              label(MSG.LOGIN_TRACE_ID, { traceId: error.traceId })
            ) : undefined
          }
        />
      )}

      {!error && notice && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }} title={notice} />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => void handleSubmit(values)}
        requiredMark={false}
      >
        <Form.Item
          name="loginId"
          label={label(MSG.LOGIN_ID)}
          rules={[{ required: true, message: label(MSG.LOGIN_ID_REQUIRED) }]}
        >
          <Input autoComplete="username" autoFocus size="large" />
        </Form.Item>

        <Form.Item
          name="password"
          label={label(MSG.LOGIN_PASSWORD)}
          rules={[{ required: true, message: label(MSG.LOGIN_PASSWORD_REQUIRED) }]}
        >
          <Input.Password autoComplete="current-password" size="large" />
        </Form.Item>

        <Button type="primary" htmlType="submit" block size="large" loading={submitting}>
          {label(MSG.LOGIN_SUBMIT)}
        </Button>
      </Form>

      {/* AUTH-07 자기 재설정(screen/_standalone/README.md)의 진입 링크. */}
      <Button type="link" block onClick={openReset} style={{ marginTop: 8 }}>
        {label(MSG.LOGIN_FORGOT_PASSWORD)}
      </Button>
    </>
  );
}
