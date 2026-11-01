import assert from 'node:assert/strict';
import {test} from 'node:test';
import {rowActionCell,rowNavigationCell} from './row-actions.mjs';
import {declaredFigures,declaredScreen} from './declared-layout.mjs';
const table={heading:'연결 정보',header:['컬럼','입력방식','편집','예시','액션 조건'],rows:[['접속 테스트','버튼 액션','읽기전용','접속 테스트','테스트 설정=Y&&연결 유효=Y']]};
const values={'테스트 설정':'Y','연결 유효':'Y','접속 테스트 결과':{'결과':'성공 예시','안내':'<script>실행 금지</script>'}};
const opts={buttons:['접속 테스트']};
test('행 버튼은 명시 권한과 모든 행 조건을 만족해야 열리고 기술값을 편집하지 않는다',()=>{
 const html=rowActionCell(table,table.rows[0],values,opts);
 assert.match(html,/data-row-action="접속 테스트"/);assert.doesNotMatch(html,/ disabled|data-edit-value/);
 for(const patch of [{'연결 유효':'N'},{'테스트 설정':'N'},{'연결 유효':'확인 필요'}])assert.match(rowActionCell(table,table.rows[0],{...values,...patch},opts),/ disabled/);
 for(const patch of [{buttons:[]},{disabledButtons:['접속 테스트']},{locked:true}])assert.match(rowActionCell(table,table.rows[0],values,{...opts,...patch}),/ disabled/);
 assert.doesNotMatch(html,/<script>/);assert.match(html,/&lt;script&gt;/);
 assert.throws(()=>rowActionCell(table,table.rows[0],{...values,'접속 테스트 결과':'원문'},opts),/JSON 객체/);
});
test('불명확하거나 없는 행 조건을 실행 허용으로 추정하지 않는다',()=>{
 assert.match(rowActionCell(table,[...table.rows[0].slice(0,4),''],values,opts),/ disabled/);
 assert.throws(()=>rowActionCell(table,[...table.rows[0].slice(0,4),'유효'],values,opts),/필드=허용값/);
});
test('행 이동은 안전한 상대 목업 경로와 명시 화면 문맥만 허용한다',()=>{
 const t={header:['컬럼','입력방식','예시']},row=['관련 API','화면 이동','../../hub/business-api/mockup.html?connectionId=ORDER-AUTH#main'];
 const html=rowNavigationCell(t,row,{'관련 API 화면':'SCR-004'},{screenPath:'admin/system/dashboard'});
 assert.match(html,/data-request-screen="SCR-004"/);assert.match(html,/connectionId=ORDER-AUTH/);
 for(const href of ['javascript:alert(1)','https://evil.example/mockup.html','../../secrets.txt','../../hub/business-api/mockup.html?token=<x>'])assert.throws(()=>rowNavigationCell(t,row,{'관련 API':href},{}),/목업 상대 경로/);
 assert.match(rowNavigationCell(t,row,{'관련 API':'—'},{}),/이동 대상 없음/);
});
test('조회 전용 그림에서도 SAVE와 독립적인 EXECUTE 허용을 행별로 유지한다',()=>{
 const plain=x=>String(x??'').replace(/`|\*/g,'').trim();
 const [figure]=declaredFigures({header:['그림 키','상태','상세 영역','안내','허용 버튼','편집','예시 변경'],rows:[['readonly','조회 전용','연결 정보','','조회·접속 테스트','잠금','없음']]},plain);
 const master={heading:'시스템',header:['컬럼','입력방식','편집','예시'],rows:[['시스템','조회','읽기전용','S1']]};
 const model={columns:[master,table],actions:[['조회'],['접속 테스트']],search:[],rowExamples:{rows:[['연결 정보',JSON.stringify(values),'—']]}};
 const html=declaredScreen('시스템',model,figure,{plain,annotator:()=>({badge:()=>'',render:()=>''})});
 assert.match(html,/data-row-action="접속 테스트"[^>]*>/);assert.doesNotMatch(html,/data-row-action="접속 테스트"[^>]* disabled/);assert.doesNotMatch(html,/data-grid-save|data-edit-action[^>]*>접속 테스트/);
});
test('요약 카드는 입력 폼·편집 그리드 없이 기존 값과 경고 수를 표시한다',()=>{
 const plain=x=>String(x??'').replace(/`|\*/g,'').trim();
 const [figure]=declaredFigures({header:['그림 키','상태','상세 영역','안내','허용 버튼','편집','예시 변경'],rows:[['main','경고','경고 목록','','조회','잠금','없음']]},plain);
 const summary={heading:'요약 — 폼',header:['컬럼','입력방식','편집','예시'],rows:[['사용 가능 메뉴','요약 카드','읽기전용','24']]};
 const detail={heading:'경고 목록',header:['컬럼','입력방식','편집','예시'],rows:[['사유','조회','읽기전용','설정 없음']]};
 const html=declaredScreen('대시보드',{columns:[summary,detail],actions:[['조회']],search:[]},figure,{plain,annotator:()=>({badge:()=>'',render:()=>''})});
 assert.match(html,/data-summary-panel/);assert.match(html,/data-summary-card/);assert.match(html,/<strong[^>]*>24<\/strong>/);assert.doesNotMatch(html,/data-grid-role="master"|data-edit-value/);
});
