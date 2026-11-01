import assert from 'node:assert/strict';
import { test } from 'node:test';
import {stateHeads,stateCells,deleteHead,deleteCell,systemWidth,switchCell,switchState} from './grid-chrome.mjs';

const order=html=>[...html.matchAll(/data-grid-system="([^"]+)"/g)].map(match=>match[1]);
test('여부 스위치는 원래 참거짓 값 쌍을 유지하고 미상을 거짓으로 만들지 않는다',()=>{
 for(const pair of [['Y','N'],['true','false'],['1','0'],['예','아니오'],['사용','미사용']]){
  assert.equal(switchState(pair[0]),true);assert.equal(switchState(pair[1]),false);
  assert.match(switchCell(pair[0],'사용',{configurable:true,editable:true}),new RegExp(`data-switch-on="${pair[0]}" data-switch-off="${pair[1]}"`));
 }
 assert.equal(switchState('미확인'),null);assert.doesNotMatch(switchCell('미확인','사용'),/aria-checked/);
});
test('다중 편집 그리드의 번호·선택·상태와 맨 오른쪽 삭제 순서/고정 폭을 유지한다',()=>{
  const head=stateHeads(true,true)+'<th data-grid-system="business">모델</th>'+deleteHead(true);
  const row=stateCells(true,false,true,4)+'<td data-grid-system="business">A</td>'+deleteCell(true,false);
  assert.deepEqual(order(head),['number','select','state','business','delete']);
  assert.deepEqual(order(row),order(head));
  assert.match(head,/data-grid-system="number"[^>]*left:0px;width:60px/);
  assert.match(head,/data-grid-system="select"[^>]*left:60px;width:44px/);
  assert.match(head,/data-grid-system="state"[^>]*left:104px;width:84px/);
  assert.match(head,/data-grid-system="delete"[^>]*right:0px;width:84px/);
  assert.match(row,/data-grid-row-number[^>]*>5<\/td>/);
  assert.equal(systemWidth(true,true,true),272);
});
test('단일/조회 전용/삭제 미지원은 실제 DataGrid의 조건에 따라 시스템 컬럼을 생략한다',()=>{
  assert.deepEqual(order(stateHeads(false,false)+'<th data-grid-system="business">값</th>'+deleteHead(false)),['number','business']);
  assert.deepEqual(order(stateHeads(false,true)),['number','select']);
  assert.deepEqual(order(stateHeads(true,false)),['number','state']);
  assert.doesNotMatch(deleteCell(true,true,'참조 중'),/<button|data-grid-delete(?:\s|>)/);
  assert.match(deleteCell(true,true,'참조 중'),/data-grid-delete-blocked title="참조 중">—/);
});
