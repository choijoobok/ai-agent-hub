import { describe, expect, it } from 'vitest';
import { actionTargets } from '@/shared/grid/useGridController';
import type { GridRow } from '@/shared/grid/rows';
import type { ActionApi } from '@/shared/grid/types';

/**
 * 실행의 <b>대상이 무엇인가</b> — frontend/grid.md.
 *
 * <p><b>화면과 컨트롤러가 같은 함수로 센다.</b> 버튼의 비활성(그리고 그 사유 툴팁)도, 실제로
 * 나가는 페이로드도 이 함수의 결과이며 — 따로 세면 <i>버튼은 켜졌는데 눌러도 아무 일이
 * 없는</i> 상태나 <b>확인 문구의 건수와 실제로 나간 건수가 다른</b> 상태가 생긴다.
 *
 * <p><b>{@code 'all'} 이 걸러지는 것이 이 파일의 이유다</b>(playbook/INDEX.md 의 품목 재계산이 첫
 * 사용처다). <i>전체</i> 가 아니라 <b>지금 그리드에 들어 있는 행</b>이고 거기서 대상이 되는
 * 것만 남으므로, <b>조회 결과에 대상이 하나도 없는</b> 상태가 실재한다 — 그때 빈 배열을
 * 보내면 서버가 400 을 내고 화면에는 <i>입력값을 확인하세요</i> 만 뜬다.
 */
describe('실행의 대상 — 고른 뒤에 거른다(frontend/grid.md)', () => {
  interface Order {
    ordNo: string;
    stat: string;
  }

  /** 조회로 들어온 무변경 행 — {@code state} 가 {@code null} 이다({@code rows.ts}). */
  function row(ordNo: string, stat: string): GridRow<Order> {
    const data = { ordNo, stat };
    return { id: ordNo, state: null, data, original: data };
  }

  const rcv = row('SO-1', 'RCV');
  const cfm = row('SO-2', 'CFM');

  /** 접수만 — 이 화면의 품목 재계산과 같은 게이트다. */
  const action = {
    key: 'recalcItems',
    label: 'x',
    url: '/sample/service/recalcItems',
    eligible: (candidate: GridRow<Order>) => candidate.data.stat === 'RCV',
  } as ActionApi<Order>;

  it('selected 는 고른 행에서 거른다', () => {
    expect(actionTargets(action, 'selected', [rcv, cfm], [rcv, cfm])).toEqual([rcv]);
  });

  /*
    <b>all 은 전체가 아니라 그리드에 든 행이다.</b> 고른 것이 없어도 대상이 있으므로 단일
    선택 그리드에서도 여러 행을 도는 실행이 성립한다 — 행×디테일 진행률이 그것을 쓴다.
  */
  it('all 은 고르지 않아도 그리드의 행에서 거른다', () => {
    expect(actionTargets(action, 'all', [], [rcv, cfm])).toEqual([rcv]);
  });

  /*
    <b>0건이 실재한다.</b> 조회 결과가 전부 확정이면 이 실행의 대상은 없고, 그 사실을
    <i>입력값을 확인하세요</i>(서버의 400)로 말하면 사용자가 고칠 입력이 아무 데도 없다 —
    버튼이 꺼지고 `hint` 가 이유를 말해야 한다.
  */
  it('all 인데 대상이 하나도 없을 수 있다 — 버튼이 꺼지는 근거다', () => {
    expect(actionTargets(action, 'all', [], [cfm])).toEqual([]);
  });

  /*
    <b>none 은 화면의 행을 보지 않는다</b>(①의 일괄 재계산 — 대상이 기준일에 든 전량이다).
    0건인 것이 정상이므로 버튼도 끄지 않는다.
  */
  it('none 은 언제나 빈 배열이다', () => {
    expect(actionTargets(action, 'none', [rcv], [rcv, cfm])).toEqual([]);
  });

  /** 게이트가 없는 실행은 그대로 간다 — 거르는 것은 선언한 화면의 일이다. */
  it('eligible 이 없으면 풀이 그대로다', () => {
    const plain = { key: 'ship', label: 'x', url: '/u' } as ActionApi<Order>;
    expect(actionTargets(plain, 'all', [], [rcv, cfm])).toEqual([rcv, cfm]);
  });
});
