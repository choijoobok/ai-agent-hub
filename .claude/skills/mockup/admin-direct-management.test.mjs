/** 사용자 결정: 관리자 직접 변경·이력, Portal 신청 승인, 지식 의미 검토의 제품 범위 제외. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const repo=path.resolve(import.meta.dirname,'../../..');
const root=path.join(repo,'documents/screen/admin/hub');
const read=name=>fs.readFileSync(path.join(root,name,'requirements.md'),'utf8');
const removed=['asset-change-request','user-access-request','knowledge-review',...['catalog','business-api','business-tool','knowledge-tag','knowledge','query-example'].map(x=>x+'/change-request')];
const assets=['catalog','catalog-relation','catalog-group','business-api','api-group','business-tool','knowledge','knowledge-tag','query-example','user-group','portal-user'];
const controls=md=>{
 let header=[];const names=[];
 for(const line of md.split(/\r?\n/)){
  if(!line.startsWith('|')){header=[];continue;}
  const cells=line.split('|').slice(1,-1).map(x=>x.trim());
  if(!header.length){header=cells;continue;}
  if(cells.every(x=>/^[-: ]+$/.test(x)))continue;
  for(const name of ['버튼','허용 버튼','목업 이동']){const at=header.indexOf(name);if(at>=0)names.push(...(cells[at]??'').split('·'));}
 }
 return names;
};
const assertDirect=md=>assert.ok(!controls(md).some(x=>/승인|전결|반려|변경 요청 조회|그룹 추가 요청/.test(x)),'관리자 변경은 승인/전결 동작을 제공하지 않는다');
test('철회된 관리자 요청·지식 검토 화면의 요건서가 남지 않는다',()=>{
 for(const name of removed)assert.equal(fs.existsSync(path.join(root,name,'requirements.md')),false,name);
});
test('모든 자산·그룹 변경 진입은 직접 저장하며 승인 절차를 다시 넣으면 검사에 실패한다',()=>{
 for(const name of assets){const md=read(name);assertDirect(md);assert.ok(controls(md).includes('저장'),name+' 직접 저장');}
 assert.throws(()=>assertDirect('| 버튼 | 액션 |\n|---|---|\n| 승인 요청 | SAVE |'));
});
test('Portal 추가 토큰·승급 신청의 관리자 판정은 유지한다',()=>{
 for(const [name,approve,reject] of [['extra-token-request','승인','반려'],['quota-upgrade-request','승급 승인','승급 반려']]){
  const names=controls(read(name));assert.ok(names.includes(approve));assert.ok(names.includes(reject));
 }
});
test('연결 지식은 참조 조회이며 본문 일괄 수집·보완 판정 컬럼을 제공하지 않는다',()=>{
 for(const name of ['business-api','business-tool','knowledge-tag','query-example','catalog-relation']){
  const md=read(name);const section=md.match(/### 4-\d+\. 연결 지식\r?\n([\s\S]*?)(?=\r?\n### |\r?\n## )/)?.[1];
  assert.ok(section,name+' 사용처');
  assert.doesNotMatch(section,/^\| (지식 본문|보완 판단|보완 조치 연결|요청번호|리비전) \|/m,name);
  assert.equal((section.match(/^\| 지식 리비전 \|/gm)||[]).length,1,name+' 현재 리비전 하나');
 }
});
