import { useEffect, useMemo } from 'react';
import { useCodeStore } from '@/shared/code/codeStore';
import { codeLabel } from '@/shared/i18n/codeLabel';
import type { SelectOption } from '@/shared/grid/types';

/** 그룹코드에 쉼표가 없다는 보장이 없다 — 이을 때는 코드값에 못 나오는 것을 쓴다. */
const SEP = ' ';

/**
 * 그룹코드 → 콤보 후보 — sys/code.md.
 *
 * <p><b>여기서 이름이 붙는다.</b> 서버가 주는 것은 코드값뿐이고 화면에 보일 글자는
 * `code.{GRP_CD}.{DTL_CD}` 메시지다 — 그 조인이 <b>메모리에서</b> 일어나는 자리가 여기다.
 * 등록을 빠뜨린 코드는 키 문자열이 그대로 보인다(sys/code.md).
 *
 * <p><b>아직 못 받은 그룹은 빈 배열이다.</b> `undefined` 와 가르지 않는다 — 부르는 쪽이
 * *로딩 중* 을 그려야 한다면 콤보가 잠깐 비는 것보다 요란하고, 캐시가 있으면 첫 렌더에
 * 이미 차 있다.
 *
 * @param grpCds 이 화면이 쓰는 그룹 전부. <b>배열이 매 렌더 새것이어도 된다</b> — 의존은
 *     정체성이 아니라 내용이다
 */
export function useCodeOptions(grpCds: string[]): Record<string, SelectOption[]> {
  const groups = useCodeStore((state) => state.groups);
  const version = useCodeStore((state) => state.version);
  const key = [...new Set(grpCds.filter(Boolean))].sort().join(SEP);

  useEffect(() => {
    if (key === '') return;
    useCodeStore.getState().ensure(key.split(SEP));
  }, [key, version]);

  return useMemo(() => {
    const result: Record<string, SelectOption[]> = {};
    if (key === '') return result;

    for (const grpCd of key.split(SEP)) {
      result[grpCd] = (groups[grpCd] ?? []).map((dtlCd) => ({
        value: dtlCd,
        text: codeLabel(grpCd, dtlCd),
      }));
    }
    return result;
  }, [key, groups]);
}

/** 한 그룹만 쓰는 자리 — 콤보 컴포넌트가 이것을 쓴다. */
export function useCodeOptionsOf(grpCd: string): SelectOption[] {
  const grpCds = useMemo(() => [grpCd], [grpCd]);
  return useCodeOptions(grpCds)[grpCd] ?? [];
}
