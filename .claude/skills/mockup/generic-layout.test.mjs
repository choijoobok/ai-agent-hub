import assert from 'node:assert/strict';
import {test} from 'node:test';
import {genericScreen} from './generic-layout.mjs';

const columns=(heading,value)=>({heading,header:['컬럼','입력방식','편집','예시'],rows:[['값','텍스트','가능',value]]});
const surfaces=[{name:'목록',kind:'목록',buttons:[]},{name:'역할',kind:'탭',buttons:[]},{name:'설정',kind:'탭 폼',buttons:[]}];
const model={columns:[columns('목록','사용자'),columns('역할','운영자'),columns('설정','유지할 입력')],surfaces,search:[],actions:[],actionTables:[],vertical:true};
const options={annotator:()=>({badge:()=>''})};
const render=(fixture=model,extra={})=>genericScreen('사용자',fixture,{key:'main',caption:'초기 화면'},{...options,...extra});

test('일반 목업도 초기 화면의 모든 탭 패널과 실제 버튼을 생성한다',()=>{
  const html=render();
  assert.match(html,/data-interactive-tabs/);
  assert.match(html,/role="tablist"/);
  assert.match(html,/aria-selected="true" data-detail-tab="역할"/);
  assert.match(html,/data-detail-panel="역할"><div/);
  assert.match(html,/data-detail-panel="설정" hidden[\s\S]*value="유지할 입력"/);
  assert.equal([...html.matchAll(/data-detail-panel=/g)].length,2);
  assert.equal([...html.matchAll(/data-grid-state-column/g)].length,2);
  assert.equal([...html.matchAll(/data-grid-delete-column/g)].length,0);
});
test('일반 목업도 하단 그리드 하나는 스플리터 아래 직접 표시한다',()=>{
 const html=render({...model,columns:model.columns.slice(0,2),surfaces:surfaces.slice(0,2)});
 assert.match(html,/class="split"/);assert.match(html,/data-grid-role="master"[\s\S]*data-grid-role="detail"/);
 assert.doesNotMatch(html,/class="tabs"|role="tablist"|data-detail-tab=/);
});

test('탭별 설명 그림에는 탭바를 반복하지 않고 지정한 영역만 보여준다',()=>{
  const html=render(model,{compact:true,surfaceName:'설정'});
  assert.match(html,/value="유지할 입력"/);
  assert.doesNotMatch(html,/data-interactive-tabs|data-detail-tab=|운영자/);
});

test('최상단이 탭인 화면과 탭 없는 단일 목록을 구분한다',()=>{
  const tabOnly={...model,columns:model.columns.slice(1),surfaces:surfaces.slice(1)};
  const html=render(tabOnly);
  assert.match(html,/data-detail-panel="역할"/);
  assert.equal([...html.matchAll(/data-detail-panel=/g)].length,2);
  assert.doesNotMatch(render({...model,columns:model.columns.slice(0,1),surfaces:surfaces.slice(0,1)}),/data-interactive-tabs|role="tablist"/);
  assert.throws(()=>render({...model,columns:model.columns.slice(0,2)}),/컬럼 표가 §4에 없다/);
});


test('기존 식별자는 잠그고 신규 행만 편집하며 미선언 행 추가는 만들지 않는다',()=>{
 const fixture={...model,columns:[{heading:'기준',header:['컬럼','입력방식','편집','예시'],rows:[['식별자','텍스트','신규행만','existing.id'],['이름','텍스트','가능','기존']]}],surfaces:[{name:'기준',kind:'목록',buttons:['조회','행 추가','저장']}],search:[]};
 const html=render(fixture);
 const body=html.split('</template>')[1];
 assert.doesNotMatch(body,/<input[^>]*value="existing.id"/);
 assert.match(html.split('</template>')[0],/<input[^>]*value="existing.id"/);
 const fixed=render({...fixture,surfaces:[{...fixture.surfaces[0],buttons:['조회','저장']}]});
 assert.doesNotMatch(fixed,/data-grid-add/);
});

test('일반 그리드의 여부형은 직접 편집·조회 전용·미상을 구분한 스위치다',()=>{
 const table={heading:'기준',header:['컬럼','입력방식','편집','예시'],rows:[['사용 여부','설정 스위치','가능','Y'],['시스템','스위치','읽기전용','N'],['확인 여부','스위치','읽기전용','확인 필요'],['처리 상태','선택','읽기전용','대기']]};
 const fixture={...model,columns:[table],surfaces:[{name:'기준',kind:'목록',buttons:['조회','저장']}]};
 const html=render(fixture);
 assert.match(html,/aria-label="사용 여부" aria-checked="true"/);assert.match(html,/data-config-switch data-switch-readonly="false"/);
 assert.match(html,/aria-label="시스템" aria-checked="false" disabled/);assert.match(html,/data-switch-unknown>확인 필요/);
 assert.doesNotMatch(html,/data-cell-editor|type="checkbox"/);assert.match(html,/data-row-field="처리 상태"[^>]*>대기/);
 assert.match(genericScreen('기준',fixture,{key:'readonly'},options),/data-switch-readonly="true"[^>]+aria-label="사용 여부" aria-checked="true" disabled/);
});

test('기존 Y/N 유형과 설정 폼도 같은 스위치 표현을 사용한다',()=>{
 const table={heading:'설정',header:['컬럼','입력방식','편집','예시'],rows:[['사용','Y/N','가능','true']]};
 const fixture={...model,columns:[table],surfaces:[{name:'설정',kind:'설정',buttons:['저장']}]};
 const html=render(fixture);assert.match(html,/data-row-field="사용" data-row-value="true"/);assert.match(html,/role="switch"[^>]+aria-checked="true"/);assert.doesNotMatch(html,/<select|<input/);
 assert.match(render({...fixture,columns:[{...table,readonly:true}]}),/aria-checked="true" disabled/);
});
