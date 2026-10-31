import{test}from'node:test';import assert from'node:assert/strict';
import{parseYaml}from'../../hooks/lib.mjs';import{loadSpecSet}from'../../../aidd-guide/harness/runtime/spec-set.mjs';import{approvalContractErrors}from'./approval-contract.mjs';
const domain=loadSpecSet({repo:process.cwd(),name:'40-domain',parseYaml}).document;
test('전체 변경 이력 명세의 소유·열·마스터 리비전 계약',()=>assert.deepEqual(approvalContractErrors(domain),[]));
test('본문 열 누락·이력 누락·자식 시각·다른 문맥 부모를 거부한다',()=>{
 for(const change of [d=>{const s=d.entities.find(e=>e.id==='ENT-051');s.attributes=s.attributes.filter(a=>a.physical_name!=='CATALOG_ID');},d=>{d.entities=d.entities.filter(e=>e.id!=='ENT-051');},d=>d.entities.find(e=>e.id==='ENT-053').attributes.push({physical_name:'HIST_DTM'}),d=>d.entities.find(e=>e.id==='ENT-053').history_parent='ENT-005']){
  const d=structuredClone(domain);change(d);assert.ok(approvalContractErrors(d).length);
 }
});

const attrs=(...names)=>names.map(physical_name=>({physical_name,pk:physical_name==='SNP_ID'||physical_name==='HIST_ID'}));
const historyOnly=()=>({entities:[
 {id:'MASTER',attributes:attrs('ID','REVISION'),copy_mapping:{history:'HISTORY'}},
 {id:'HISTORY',storage_role:'history',history_copy_of:'MASTER',attributes:attrs('HIST_ID','HISTORY_NO','ID','REVISION','HIST_DTM')}
]});
test('이력만 선언한 직접 저장 계약에는 승인 스냅샷을 요구하지 않는다',()=>{
 assert.deepEqual(approvalContractErrors(historyOnly()),[]);
 const missing=historyOnly();delete missing.entities[0].copy_mapping.history;
 assert.ok(approvalContractErrors(missing).some(e=>e.includes('/history: 복사 모델이 없다.')));
});
test('선택 스냅샷을 선언하면 존재·원본 열·결과 리비전 검사를 생략하지 않는다',()=>{
 const d=historyOnly();d.entities[0].copy_mapping.snapshot='SNAPSHOT';
 assert.ok(approvalContractErrors(d).some(e=>e.includes('/snapshot: 복사 모델이 없다.')));
 d.entities.push({id:'SNAPSHOT',storage_role:'snapshot',snapshot_copy_of:'MASTER',attributes:attrs('SNP_ID','REQUEST_NO','ID','REVISION','BASE_REVISION','APPLIED_REVISION')});
 assert.deepEqual(approvalContractErrors(d),[]);
 for(const field of ['ID','APPLIED_REVISION']){
  const invalid=structuredClone(d);invalid.entities[2].attributes=invalid.entities[2].attributes.filter(a=>a.physical_name!==field);
  assert.ok(approvalContractErrors(invalid).some(e=>e.includes('/snapshot:')));
 }
});
