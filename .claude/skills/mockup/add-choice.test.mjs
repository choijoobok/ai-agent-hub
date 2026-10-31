import{test}from'node:test';import assert from'node:assert/strict';import fs from'node:fs';import vm from'node:vm';
import{addChoiceFor,lookupAddDialog}from'./add-choice.mjs';
const runtime=vm.createContext({});vm.runInContext(fs.readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-controls.js',import.meta.url),'utf8'),runtime);
const rule={mode:'조회 팝업',source:'사용자',parent:'그룹',fixed:'그룹',key:'사용자',active:'활성',state:'정상',records:[{사용자:'u1'},{사용자:'u2','선택 가능':false}],defaults:{활성:true,메모:''}};
const resolve=(parent,key,rows=[],r=rule)=>runtime.resolveMockupLookupAdd(r,parent,key,rows);
test('조회 선택은 선택 부모와 안정 키를 고정하며 다른 부모의 같은 대상은 중복이 아니다',()=>{
 const result=resolve('g1','u1',[{그룹:'g2',사용자:'u1',활성:true}]);assert.equal(result.operation,'insert');assert.equal(result.values.그룹,'g1');assert.equal(result.values.사용자,'u1');assert.equal(result.values.활성,true);assert.deepEqual(rule.defaults,{활성:true,메모:''});
});
test('조회 실패·미선택·권한 밖 후보·상위 미선택을 임의 새 행으로 바꾸지 않는다',()=>{
 assert.throws(()=>resolve('','u1'),/상위/);assert.throws(()=>resolve('g1',''),/기준정보/);assert.throws(()=>resolve('g1','u2'),/기준정보/);assert.throws(()=>resolve('g1','unknown'),/기준정보/);assert.throws(()=>resolve('g1','u1',[],{...rule,state:'실패'}),/조회 실패/);
});
test('같은 부모의 활성/미저장 중복은 거부하고 팝업의 중지 소속은 기존 행 재활성화로 반환한다',()=>{
 assert.throws(()=>resolve('g1','u1',[{그룹:'g1',사용자:'u1',활성:true}]),/이미/);
 assert.equal(resolve('g1','u1',[{그룹:'g1',사용자:'u1',활성:false}]).operation,'reactivate');
 assert.throws(()=>resolve('g1','u1',[{그룹:'g1',사용자:'u1',활성:false,__editable:false}]),/변경 권한/);
 assert.throws(()=>resolve('g1','u1',[{그룹:'g1',사용자:'u1',활성:false}],{...rule,mode:'기준정보 콤보'}),/이미/);
});
const table={header:['추가 방식','방식','원천','상위 필드','고정 필드','선택 키','활성 필드','조회 조건','표시 컬럼','후보 예시(JSON)','기본값(JSON)'],rows:[['사용자','조회 팝업','Portal 사용자','그룹','그룹','사용자','활성','사용자·이름','사용자·이름',JSON.stringify([{사용자:'u1',이름:'<script>'}]),'{"활성":true}']]};
test('추가 선언의 키/원천/후보를 검사하고 실패 상태와 안전한 조회 팝업을 생성한다',()=>{
 const model={addChoices:table},parsed=addChoiceFor(model,'사용자',{examples:{'추가 후보 상태':'실패'}});assert.equal(parsed.state,'실패');assert.equal(parsed.records[0].이름,'<script>');
 const html=lookupAddDialog(parsed,()=>'' );assert.match(html,/data-lookup-search/);assert.match(html,/data-lookup-apply disabled/);assert.doesNotMatch(html,/<script>/);
 const bad=structuredClone(table);bad.rows[0][9]='[{"사용자":"u1"},{"사용자":"u1"}]';assert.throws(()=>addChoiceFor({addChoices:bad},'사용자',{}),/중복/);
 assert.equal(addChoiceFor(model,'없는 탭',{}),null);
});
