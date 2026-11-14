import type { CSSProperties } from 'react';
import { Button, Select } from 'antd';
import { useCodeOptionsOf } from '@/shared/code/useCodeOptions';
import { useCodeOptionsByRef, type CodeRefFilter } from './useCodeOptionsByRef';
import { codeLabel } from '@/shared/i18n/codeLabel';
import { label } from '@/shared/i18n/label';
import { MSG } from '@/shared/i18n/messageKeys';

/**
 * 공통코드 콤보 — sys/code.md.
 *
 * <p><b>화면은 그룹코드만 적는다.</b> 목록을 받고 캐시하고 이름을 붙이는 일이 전부 이 안에
 * 있으므로, 코드를 더하거나 빼는 데 소스 변경이 따르지 않는다 — 그것이 공통코드로 관리할
 * 코드와 <b>소스가 값을 갖는</b> 코드({@code ACTION_CD} 류, sys/code.md)를 가르는 선이다.
 *
 * <p><b>조회조건과 그리드는 이것을 쓰지 않는다.</b> 그쪽은 선언(`SearchField`·`ColumnDef`)에
 * `grpCd` 를 적고 폼과 판이 후보를 채운다 — 컨트롤을 직접 그리는 것이 아니라 <b>선언을
 * 넘기는</b> 구조라서다. 여기는 모달·상세 폼처럼 <b>컨트롤을 직접 놓는</b> 자리의 것이다.
 */
export function CodeSelect(props: {
  grpCd: string;
  refFilter?: CodeRefFilter;
  value?: string | string[];
  onChange?: (value: string | string[]) => void;
  /** 다중 선택 — 값이 배열이 된다(frontend/search-form.md 의 `in` 과 같은 모양이다). */
  multiple?: boolean;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  style?: CSSProperties;
}) {
  const all = useCodeOptionsOf(props.refFilter ? '' : props.grpCd);
  const filtered = useCodeOptionsByRef(props.grpCd, props.refFilter);
  const options = props.refFilter ? filtered.options : all;
  const values = props.value === undefined ? [] : Array.isArray(props.value) ? props.value : [props.value];
  const unsupported = props.refFilter && filtered.status === 'ready' ? values.filter((value) => !options.some((option) => option.value === value)) : [];
  const empty = filtered.status === 'error' ? <Button type="link" onClick={filtered.retry}>{label(MSG.CODE_COMBO_RETRY)}</Button>
    : label(filtered.status === 'incomplete' ? MSG.CODE_COMBO_PARENT : filtered.status === 'loading' ? MSG.CODE_COMBO_LOADING : MSG.CODE_COMBO_EMPTY);

  return (
    <Select
      mode={props.multiple ? 'multiple' : undefined}
      value={props.value}
      onChange={props.onChange}
      placeholder={props.refFilter && filtered.status === 'incomplete' ? label(MSG.CODE_COMBO_PARENT) : props.placeholder}
      disabled={props.disabled}
      loading={!!props.refFilter && filtered.status === 'loading'}
      status={props.refFilter && filtered.status === 'error' ? 'error' : unsupported.length ? 'warning' : undefined}
      notFoundContent={props.refFilter ? empty : undefined}
      popupRender={unsupported.length && !options.length ? (menu) => <><div role="status" style={{ padding: 8 }}>{label(MSG.CODE_COMBO_EMPTY)}</div>{menu}</> : undefined}
      allowClear={props.allowClear}
      style={props.style}
      // `title: ''` — 판은 고른 항목의 글자를 그대로 브라우저 툴팁으로 단다. 보이는 글자를
      // 한 번 더 띄우는 것이고, 모양도 셸의 툴팁과 다르다(frontend/grid.md).
      options={[
        ...options.map((option) => ({ value: option.value, label: option.text, title: '' })),
        ...unsupported.map((value) => ({ value, label: `${codeLabel(props.grpCd, value)} (${label(MSG.CODE_COMBO_UNSUPPORTED)})`, title: '', disabled: true })),
      ]}
    />
  );
}
