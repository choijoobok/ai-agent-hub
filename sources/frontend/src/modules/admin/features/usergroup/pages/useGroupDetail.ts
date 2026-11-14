// @feat FEAT-006
import { useCallback, useEffect, useState } from 'react';
import { fetchGroup, fetchGroupProjection } from '../api/userGroupApi';
import type { GroupProjection, GroupScope, UserGroupRow } from '../types';

export type ReferenceChange = (groupId: string, state: UserGroupRow['useState']) => void;
const scopes: GroupScope[] = ['membership', 'knowledge', 'catalog', 'api', 'tool'];

/** 하단 조회가 끝난 선택 행만 삭제를 연다. 늦게 도착한 이전 그룹/탭 응답은 버린다. */
export function useGroupDetail(groupId: string | null, onReference: ReferenceChange) {
  const [loaded, setLoaded] = useState<{ groupId: string; details: Partial<Record<GroupScope, GroupProjection>> } | null>(null);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let current = true;
    if (groupId) {
      onReference(groupId, 'UNKNOWN');
      void Promise.all([Promise.all(scopes.map((scope) => fetchGroupProjection(groupId, scope))), fetchGroup(groupId)]).then(([details, group]) => {
        if (!current) return;
        setLoaded({ groupId, details: Object.fromEntries(scopes.map((scope, index) => [scope, details[index]])) });
        const unavailable = details.some((detail) => detail.state === 'UNAVAILABLE' || detail.state === 'FORBIDDEN');
        onReference(groupId, unavailable ? 'UNKNOWN' : details.some((detail) => detail.rows.length > 0) ? 'USED' : group.useState);
      }).catch(() => {
        if (current) {
          setLoaded({ groupId, details: Object.fromEntries(scopes.map((scope) => [scope, { state: 'UNAVAILABLE', rows: [] }])) });
          onReference(groupId, 'UNKNOWN');
        }
      });
    }
    return () => { current = false; };
  }, [groupId, onReference, refresh]);
  const onRead = useCallback(() => {
    if (groupId) onReference(groupId, 'UNKNOWN');
    setLoaded(null); setRefresh((value) => value + 1);
  }, [groupId, onReference]);
  return { details: loaded?.groupId === groupId ? loaded.details : {}, onRead };
}
