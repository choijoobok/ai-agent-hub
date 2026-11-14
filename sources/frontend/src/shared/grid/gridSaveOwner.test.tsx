// @feat FEAT-042
import { App } from 'antd';
import { act, renderHook } from '@testing-library/react';
import { expect, it } from 'vitest';
import { useGridController } from './useGridController';
import { useAuthStore } from '@/shared/stores/authStore';

it('소유 저장의 상세는 추가·삭제가 가능하지만 독립 저장은 없고 현재 SAVE 권한을 재검사한다', () => {
  useAuthStore.setState({ permissions: ['TEST:SAVE', 'TEST:READ'] });
  const { result, rerender } = renderHook(({ allowed }) => useGridController({ menuCd: 'TEST', columns: [],
    saveOwner: { canSave: allowed }, addRow: { defaults: () => ({ value: '' }) } }), {
    initialProps: { allowed: true }, wrapper: ({ children }) => <App>{children}</App>,
  });
  expect(result.current.canAddRow).toBe(true); expect(result.current.savesItself).toBe(false);
  act(() => result.current.addRow()); expect(result.current.rows).toHaveLength(1);
  act(() => result.current.toggleDelete(result.current.rows[0].id)); expect(result.current.rows).toHaveLength(0);
  act(() => useAuthStore.setState({ permissions: ['TEST:READ'] })); rerender({ allowed: true });
  expect(result.current.canSave).toBe(false); expect(result.current.canAddRow).toBe(false);
});
