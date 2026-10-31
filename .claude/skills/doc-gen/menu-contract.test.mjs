import{test}from'node:test';import assert from'node:assert/strict';import{parseYaml}from'../../hooks/lib.mjs';import{loadSpecSet}from'../../../aidd-guide/harness/runtime/spec-set.mjs';import{menuContractErrors,menuReviewMarkdown}from'./menu-contract.mjs';
const spec=loadSpecSet({repo:process.cwd(),name:'60-screens',parseYaml}).document;
test('admin 전체 메뉴·상속 이력·차수 목록의 연결',()=>assert.deepEqual(menuContractErrors(spec),[]));
test('메뉴 누락·중복 경로·유예 혼입·system 이력 확대를 거부한다',()=>{
 for(const change of [s=>s.menu_tree.admin_groups[0].screens.pop(),s=>{s.screens.find(x=>x.id==='SCR-004').route=s.screens.find(x=>x.id==='SCR-003').route;},s=>s.roadmap_rollout['RM-P1'].admin.push('SCR-025'),s=>s.screens.push({id:'SCR-999',module:'system',view_kind:'change_history'})]){const s=structuredClone(spec);change(s);assert.ok(menuContractErrors(s).length);}
});
test('목업 미작성과 상속을 구분해 목록에 표시한다',()=>{const text=menuReviewMarkdown(spec,()=>false);assert.ok(text.includes('요건서 작성 전'));assert.ok(text.includes('원 화면 상속'));assert.ok(text.includes('system 모듈은 이번'));});

test('관리자 요청·지식 영향 검토 메뉴 철회와 Portal 신청 메뉴 보존',()=>{
 const retired=['SCR-034','SCR-036','SCR-037','SCR-056','SCR-057','SCR-058','SCR-059','SCR-060','SCR-061'];
 const ids=spec.menu_tree.admin_groups.flatMap(g=>g.screens);
 for(const id of retired){assert.ok(!spec.screens.some(s=>s.id===id));assert.ok(!ids.includes(id));}
 for(const id of ['SCR-022','SCR-035'])assert.ok(ids.includes(id));
 assert.equal(spec.screens.filter(s=>s.view_kind==='change_request').length,0);
 assert.ok(!spec.screens.some(s=>s.request_view));
 const text=menuReviewMarkdown(spec,()=>false);assert.ok(!text.includes('자산별 변경 요청 처리 메뉴'));
 assert.ok(text.includes(spec.menu_tree.portal_requests.rule));
 for(const id of spec.menu_tree.portal_requests.screens)assert.ok(text.includes(`${id} ${spec.screens.find(s=>s.id===id).title}`));
});
test('철회된 요청 화면 연결을 다시 넣으면 메뉴 계약 검사가 실패한다',()=>{
 for(const [id,request]of[['SCR-003','SCR-056'],['SCR-039','SCR-056'],['SCR-004','SCR-057']]){
  const s=structuredClone(spec);s.screens.find(x=>x.id===id).request_view=request;
  assert.ok(menuContractErrors(s).some(e=>e.startsWith(id+': 소유 자산')));
 }
});
