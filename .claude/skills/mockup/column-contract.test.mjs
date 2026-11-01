import assert from 'node:assert/strict';
import {test} from 'node:test';
import {withAuditColumns} from './column-contract.mjs';
import {tableCell} from './presentation.mjs';
const table={heading:'업무 목록',header:['컬럼','입력방식','편집','예시'],rows:[['이름','텍스트','가능','업무']]};
const names=value=>value.rows.map(row=>row[0]);
test('업무 컬럼만 지정해도 기본 감사 넷을 표시하고 읽기 전용 목록에서도 유지한다',()=>{
 for(const readonly of [false,true]){
  const result=withAuditColumns({...table,readonly});
  assert.deepEqual(names(result),['이름','등록자','등록일시','수정자','수정일시']);
  for(const row of result.rows.slice(1)){assert.equal(tableCell(result,row,'편집'),'읽기전용');assert.equal(tableCell(result,row,'기본 표시'),'기본 표시');}
 }
 assert.deepEqual(names(table),['이름']);
});
test('추가 전용 원장은 등록 감사 둘을 갖고 명시한 감사값을 중복 없이 보존한다',()=>{
 const original={...table,rows:[...table.rows,['등록자','조회','읽기전용','기록 담당'],['수정자','조회','읽기전용','원본 담당']]};
 const result=withAuditColumns(original,{kind:'추가 전용'});
 assert.deepEqual(names(result),['이름','등록자','등록일시']);assert.equal(tableCell(result,result.rows[1],'예시'),'기록 담당');
 assert.deepEqual(withAuditColumns(result),result);
});
test('명시한 감사 컬럼은 기본 숨김과 편집을 풀어 읽기 전용 기본 표시로 맞춘다',()=>{
 const result=withAuditColumns({heading:'목록',header:['컬럼','필드명','편집','기본 표시','예시'],rows:[['등록 담당','regId','가능','선택 표시','담당자'],['감사 4','—','읽기전용','—','—']]});
 assert.deepEqual(names(result),['등록자','등록일시','수정자','수정일시']);assert.equal(tableCell(result,result.rows[0],'예시'),'담당자');assert.equal(tableCell(result,result.rows[0],'편집'),'읽기전용');
});
test('감사 제외는 사용자 명시 근거가 있어야 하며 폼에는 그리드 컬럼을 붙이지 않는다',()=>{
 assert.throws(()=>withAuditColumns(table,{kind:'사용자 제외'}),/사용자 명시/);
 assert.deepEqual(names(withAuditColumns(table,{kind:'사용자 제외',reason:'사용자 명시: 이 목록의 감사 컬럼 제외'})),['이름']);
 assert.equal(withAuditColumns({...table,heading:'입력 — 폼'}).rows.length,1);
 assert.throws(()=>withAuditColumns(table,{kind:'조회 전용'}),/데이터 성격/);
});
