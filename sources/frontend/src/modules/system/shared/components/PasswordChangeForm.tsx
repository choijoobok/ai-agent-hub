// @feat FEAT-501
import { useRef, useState, type ReactNode } from 'react';
import { App, Button, Flex, Form, Input, Typography } from 'antd';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';
import { ApiError } from '@/shared/api';
import { fetchMe } from '@/modules/system/public/auth/api/authApi';
import { useSessionCleanup } from '@/modules/system/public/auth/session';
import { useAuthStore } from '@/shared/stores/authStore';
import { changePassword } from '@/modules/system/public/profile/api/profileApi';
import type { PasswordRuleSpec } from '@/modules/system/public/auth/api/authApi';
import { allClientRulesSatisfied, evaluateRules } from '@/modules/system/public/auth/passwordRules';

interface Props {
  ruleHints?: ReactNode;
  rules?: PasswordRuleSpec[];
  secondaryAction?: ReactNode;
}

/** 프로필과 강제 변경 화면이 공유한다. 변경 뒤 새 쿠키의 getMe로 현재 제한 상태를 확인한다. */
export function PasswordChangeForm({ ruleHints, rules = [], secondaryAction }: Props) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ currentPwd: string; newPwd: string; confirmPwd: string }>();
  const [saving, setSaving] = useState(false);
  const submitting = useRef(false);
  const loginId = useAuthStore((state) => state.user?.userId ?? '');
  const signIn = useAuthStore((state) => state.signIn);
  const cleanup = useSessionCleanup();
  const newPwd = Form.useWatch('newPwd', form) ?? '';
  const states = evaluateRules(rules, newPwd, { loginId });
  const required = { required: true, message: label(MSG.PROFILE_PWD_REQUIRED) };

  const submit = async (values: { currentPwd: string; newPwd: string }) => {
    if (submitting.current || !allClientRulesSatisfied(states)) return;
    submitting.current = true;
    setSaving(true);
    try {
      await changePassword(values.currentPwd, values.newPwd);
      form.resetFields();
      signIn(await fetchMe());
      message.success(label(MSG.PROFILE_PWD_DONE));
    } catch (error) {
      if (error instanceof ApiError && error.status === 503 && error.code === 'PASSWORD_CHANGED_REAUTH_REQUIRED') {
        form.resetFields();
        await cleanup({ callServer: false });
        return;
      }
      message.error(error instanceof Error ? error.message : String(error));
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  };

  return (
    <Form form={form} layout="vertical" onFinish={(values) => void submit(values)} autoComplete="off">
      <Form.Item name="currentPwd" label={label(MSG.PROFILE_PWD_CURRENT)} rules={[required]}>
        <Input.Password maxLength={100} autoComplete="current-password" />
      </Form.Item>
      <Form.Item name="newPwd" label={label(MSG.PROFILE_PWD_NEW)} rules={[required]} extra={ruleHints}>
        <Input.Password maxLength={100} autoComplete="new-password" />
      </Form.Item>
      {states.length > 0 && (
        <ul>
          {states.map(({ rule, satisfied }) => (
            <li key={rule.id}>
              <Typography.Text type={satisfied === false ? 'secondary' : undefined}>
                {rule.message}
              </Typography.Text>
            </li>
          ))}
        </ul>
      )}
      <Form.Item
        name="confirmPwd"
        label={label(MSG.PROFILE_PWD_CONFIRM)}
        dependencies={['newPwd']}
        rules={[
          required,
          ({ getFieldValue }) => ({
            validator: (_, value: string) =>
              !value || getFieldValue('newPwd') === value
                ? Promise.resolve()
                : Promise.reject(new Error(label(MSG.PROFILE_PWD_CONFIRM_MISMATCH))),
          }),
        ]}
      >
        <Input.Password maxLength={100} autoComplete="new-password" />
      </Form.Item>
      <Flex align="center" justify={secondaryAction ? 'space-between' : 'flex-start'} gap="small">
        {secondaryAction}
        <Button type="primary" htmlType="submit" loading={saving} disabled={!allClientRulesSatisfied(states)}>
          {label(MSG.PROFILE_PWD_SUBMIT)}
        </Button>
      </Flex>
    </Form>
  );
}
