/** 이력 화면은 원 관리 화면을 상속한다. 요건서와 목업을 복제하지 않는다. */
export function historyViewMarkdown(screen, source) {
  if (screen.view_kind !== 'change_history' || !source || source.id !== screen.source_screen
    || source.view_kind === 'change_history' || source.module !== screen.module) {
    throw new Error(`${screen.id}: 원 관리 화면의 이력 상속 연결이 잘못됐다.`);
  }
  if (screen.target_doc || screen.current_doc) throw new Error(`${screen.id}: 상속 이력의 독립 요건서/목업을 만들지 않는다.`);
  return `# ${screen.id} ${screen.title}

이 화면은 **${source.id} ${source.title}**의 조회조건·마스터 컬럼·배치·하단 탭을 읽기 전용으로 재사용합니다. 별도 이력 목업과 요건서를 복사하지 않습니다.

| 구분 | 상속·추가 규칙 |
|---|---|
| 메뉴 | ${screen.menu_code} |
| 경로 | ${screen.route} |
| 원 화면 | ${source.id} · ${source.route} |
| 조회조건 | 원 관리 화면과 동일 + 이력 저장일시 범위 |
| 상단 그리드 | 당시 관리 데이터의 동일 컬럼 + 변경 요약 |
| 행 선택 | 원 ID와 HISTORY_NO로 같은 시점의 하단 세트 조회 |
| 하단 탭 | 같은 이력 번호로 복사한 당시 행만 표시 |
| 기본 감사 컬럼 | 추가 전용 행의 등록자·등록일시 두 컬럼을 항상 표시. 원본 등록/수정 감사값은 스냅샷에 별도로 보존 |
| 저장·실행 | 편집·추가·삭제·승인·실행·복원 동작 없음 |
| 키 | 각 _HIST 표의 HIST_ID는 서로게이트 PK, HISTORY_NO는 세트 연결 키 |
| 시각 | 원 REG_DTM/MOD_DTM 보존, HIST_DTM은 마스터에만 저장하고 자식은 HISTORY_NO로 참조 |

${screen.access}

${screen.empty_state}

원 화면에 있던 실시간 관측·타 소유 사용처 등 비저장 영역에는 현재값을 대신 채우지 않습니다. 저장·요약·권한·보존의 정본은 REQ-601.change_history와 ADR-035입니다.
`;
}
