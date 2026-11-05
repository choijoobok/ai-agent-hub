import {test} from 'node:test';
import assert from 'node:assert/strict';
import {historyViewMarkdown} from './history-view.mjs';
import {inheritedHistoryMockup} from '../../mockup/scaffold.mjs';
import {readFileSync} from 'node:fs';
import {parseYaml} from '../../../hooks/lib.mjs';
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
test('카탈로그의 저장 버튼 대상과 작업자 사유는 별도 이력 열로 선언한다',()=>{
 const text=historyViewMarkdown({...screen,history_summary_columns:['변경 대상','변경 사유'],history_summary_rule:'saveTarget 전달값과 HIST_REASON을 그대로 조회한다.'},source);
 assert.match(text,/동일 컬럼 \+ 변경 대상 · 변경 사유/);assert.match(text,/saveTarget 전달값과 HIST_REASON/);
});

test('지식 이력은 원 화면에서 읽기 전용으로 파생하고 현재 관측·직접 저장은 제외한다',()=>{
 const repo=new URL('../../../../',import.meta.url);
 const screens=parseYaml(readFileSync(new URL('documents/spec/60-screens/010-change-history.yaml',repo),'utf8')).screens;
 const history=screens.find(s=>s.id==='SCR-048');
 const md=readFileSync(new URL('documents/screen/admin/hub/knowledge/requirements.md',repo),'utf8');
 const output=inheritedHistoryMockup(md,history).html;
 for(const key of ['지식 내용','변경 사유','복원 원본 리비전','restoreRevision=2','data-request-screen="SCR-011"'])assert.ok(output.includes(key),key);
 for(const key of ['data-open-command=','data-grid-save','data-row-field="임베딩 상태"','data-detail-tab="카탈로그 합산"'])assert.ok(!output.includes(key),key);
 assert.match(output,/<textarea[^>]*readonly/);
 assert.match(output,/<input[^>]*aria-label="제목"[^>]*>/);
 assert.ok(!/<input[^>]*aria-label="제목"[^>]*readonly/.test(output));
 assert.ok(!/<select[^>]*aria-label="사용 여부"[^>]*disabled/.test(output));
 for(const label of ['이력 저장일시 시작','이력 저장일시 종료'])assert.ok(output.includes('aria-label="'+label+'"'));
 assert.ok(output.includes('과거 영업 그룹'));assert.ok(output.includes('보류 수량을 별도로 안내'));
 assert.ok(!output.includes('textarea에서 본문을 편집'));
 assert.ok(output.includes('선택한 과거 이력에 저장된 내용을 읽기 전용으로 확인한다.'));
 assert.ok(historyViewMarkdown(history,{...source,id:'SCR-011'}).includes('현재 지식 편집으로 인계'));
});

test('인증 예제 이력은 여러 SQL의 원문과 설명을 읽기 전용으로 상속한다',()=>{
 const repo=new URL('../../../../',import.meta.url);
 const history=parseYaml(readFileSync(new URL('documents/spec/60-screens/010-change-history.yaml',repo),'utf8')).screens.find(s=>s.id==='SCR-049');
 const md=readFileSync(new URL('documents/screen/admin/hub/query-example/requirements.md',repo),'utf8');
 const output=inheritedHistoryMockup(md,history).html;
 for(const key of ['data-query-example-board','811','812','상세 설명','변경 사유'])assert.ok(output.includes(key),key);
 const reasonCell=output.match(/<td[^>]*data-row-field="변경 사유"[^>]*>/)?.[0];
 assert.ok(reasonCell);assert.match(reasonCell,/data-grid-type="text"/);assert.match(reasonCell,/data-value-origin="DB 저장값 조회"/);
 for(const key of ['data-open-command=','data-grid-add','data-grid-delete','data-detail-tab="파라미터"','data-row-field="검증 상태"','data-row-field="등록 경로"'])assert.ok(!output.includes(key),key);
 assert.match(output,/<textarea[^>]*aria-label="SQL"[^>]*readonly/);
 assert.ok(!output.includes('현재 지식 편집'));
});
