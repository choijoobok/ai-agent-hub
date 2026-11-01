import {test} from 'node:test';
import assert from 'node:assert/strict';
import {historyViewMarkdown} from './history-view.mjs';
const source={id:'SCR-001',module:'admin',title:'대상 시스템',route:'/admin/hub/target-system'};
const screen={id:'SCR-040',module:'admin',title:'대상 시스템 변경 이력',view_kind:'change_history',source_screen:'SCR-001',menu_code:'HUB_TARGET_SYSTEM_HISTORY',route:source.route+'/history',access:'READ/EXPORT',empty_state:'이력 없음'};
test('별도 이력 화면은 원 화면과 공통 차이만 제시하고 목업을 복제하지 않는다',()=>{
 const text=historyViewMarkdown(screen,source);
 for(const key of ['SCR-001','변경 요약','이력 저장일시 범위','HISTORY_NO','HIST_ID','REG_DTM/MOD_DTM','HIST_DTM'])assert.ok(text.includes(key));
 assert.ok(!text.includes('mockup.html'));
 assert.match(text,/등록자·등록일시 두 컬럼을 항상 표시/);assert.match(text,/원본 등록\/수정 감사값은 스냅샷에 별도로 보존/);
});
test('잘못된 원 화면·다른 모듈·상속 사슬과 별도 문서 복사를 거부한다',()=>{
 for(const original of [null,{...source,id:'SCR-002'},{...source,module:'system'},{...source,view_kind:'change_history'}])assert.throws(()=>historyViewMarkdown(screen,original),/상속 연결/);
 assert.throws(()=>historyViewMarkdown({...screen,target_doc:'copy.md'},source),/독립 요건서/);
});
