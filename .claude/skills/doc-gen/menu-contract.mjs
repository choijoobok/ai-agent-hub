/** admin 메뉴 검토 목록의 누락·중복·범위·상속 연결을 검사한다. */
export function menuContractErrors(spec){
 if(!spec.menu_tree)return[];
 const errors=[],screens=spec.screens??[],byId=new Map(screens.map(s=>[s.id,s]));
 const target=screens.filter(s=>s.module==='admin'&&s.entry==='menu'&&s.roadmap==='RM-P1');
 const listed=(spec.menu_tree.admin_groups??[]).flatMap(g=>g.screens??[]);
 for(const s of target){if(listed.filter(id=>id===s.id).length!==1)errors.push(s.id+': 현재 메뉴는 트리에 한 번 있어야 한다.');if(!s.route?.startsWith('/admin/hub/')||!s.menu_code)errors.push(s.id+': 경로/메뉴 코드가 필요하다.');}
 for(const g of spec.menu_tree.admin_groups??[])for(const id of g.screens??[]){const s=byId.get(id);if(!s||!target.includes(s))errors.push(id+': admin 현재 메뉴 범위를 벗어났다.');else if(s.group!==g.title||s.menu_parent!==g.code)errors.push(id+': 메뉴 그룹 연결이 다르다.');}
 for(const key of ['route','menu_code']){const seen=new Set();for(const s of target){if(seen.has(s[key]))errors.push(s.id+': 중복 '+key);seen.add(s[key]);}}
 for(const s of target){if(s.view_kind==='change_history'){const p=byId.get(s.source_screen);if(!p||p.module!=='admin'||p.view_kind==='change_history'||p.history_view!==s.id)errors.push(s.id+': 같은 admin 원 화면의 이력 연결이 필요하다.');if(s.target_doc||s.current_doc)errors.push(s.id+': 상속 이력의 독립 문서를 만들지 않는다.');}else if(!s.target_doc)errors.push(s.id+': 목업 요건서의 설계 경로가 필요하다.');}
 for(const s of target){
  if(s.request_view){const r=byId.get(s.request_view),owner=s.request_owner??s.id;if(!r||r.view_kind!=='change_request'||r.source_screen!==owner)errors.push(s.id+': 소유 자산의 전용 요청 화면 연결이 필요하다.');}
  if(s.view_kind==='change_request'){
   const p=byId.get(s.source_screen),index=byId.get(s.request_index);
   if(!p||p.module!=='admin'||p.view_kind||p.request_view!==s.id||s.screen_role!=='request_approval')errors.push(s.id+': 원 관리 화면과 전용 요청 화면이 서로 연결되어야 한다.');
   if(!index||index.screen_role!=='request_index')errors.push(s.id+': 통합 요청 조회 진입이 필요하다.');
   if(p&&s.target_doc===p.target_doc)errors.push(s.id+': 요청 화면의 독립 요건/목업 경로가 필요하다.');
  }
  if(s.screen_role==='request_index'&&(s.allowed_actions??[]).some(a=>!['READ','EXPORT','OPEN_REQUEST'].includes(a)))errors.push(s.id+': 통합 요청 조회에서 상태 변경·승인/반려를 처리하지 않는다.');
 }
 const rollout=spec.roadmap_rollout?.['RM-P1']?.admin??[];
 for(const s of target)if(!rollout.includes(s.id))errors.push(s.id+': 차수별 목록에 없다.');
 for(const id of rollout)if(!target.some(s=>s.id===id))errors.push(id+': 현재 차수 메뉴 목록에 유예/후속 화면이 섞였다.');
 if(screens.some(s=>s.module==='system'&&s.view_kind==='change_history'))errors.push('system은 새 변경 이력 저장 대상이 아니다.');
 return errors;
}
export function menuReviewMarkdown(spec,exists){
 if(!spec.menu_tree)return'';
 const tree=spec.menu_tree,byId=new Map(spec.screens.map(s=>[s.id,s]));
 const all=tree.admin_groups.flatMap(g=>g.screens.map(id=>byId.get(id))),history=all.filter(s=>s.view_kind==='change_history'),base=all.filter(s=>s.view_kind!=='change_history');
 const ready=base.filter(s=>exists(s.target_doc)&&exists(s.target_doc.replace(/requirements\.md$/,'mockup.html'))).length;
 const lines=['## admin 메뉴 트리와 목업 작성 대상','',tree.scope,'',`검토 상태: ${tree.status}. 메뉴 ${all.length}개 = 원 화면 ${base.length}개 + 상속 이력 ${history.length}개. 원 화면 목업 ${ready}개, 작성 대상 ${base.length-ready}개. 사용자 승인 완료를 뜻하지 않습니다.`,''];
 for(const g of tree.admin_groups){lines.push(`### ${g.title} · ${g.code} (${g.screens.length}개)`,'','| 화면 ID | 메뉴 | 화면 역할 | 설계 경로 | 목업 준비 | 이력 연결 |','|---|---|---|---|---|---|');for(const id of g.screens){const s=byId.get(id),inherited=s.view_kind==='change_history',has=s.target_doc&&exists(s.target_doc),mockup=has&&exists(s.target_doc.replace(/requirements\.md$/,'mockup.html'));lines.push(`| ${id} | ${s.title} | ${s.screen_role} | ${s.route} | ${inherited?'원 화면 상속':mockup?'검토용 목업 있음':has?'목업 생성 전':'요건서 작성 전'} | ${inherited?'원 화면 '+s.source_screen:s.history_view??'업무 기록'} |`);}lines.push('');}
 const requests=base.filter(s=>s.view_kind==='change_request');
 if(requests.length){lines.push('### 자산별 변경 요청 처리 메뉴','','통합 목록은 조회와 상세 이동을 맡고, 작성·검토·승인/반려는 자산별 전용 화면에서 처리합니다. 복합 요청은 같은 요청번호의 전체 대상으로 한 번 판정합니다.','','| 원 관리 화면 | 전용 변경 요청 | 상세 경로 |','|---|---|---|',...requests.map(s=>`| ${s.source_screen} ${byId.get(s.source_screen)?.title??''} | ${s.id} ${s.title} | ${s.route} |`),'');}
 if(tree.request_workflow)lines.push(`통합 조회 화면: ${tree.request_workflow.index_screen}`,'',tree.request_workflow.source,'',tree.request_workflow.rule,'');
 if(tree.portal_requests)lines.push('### Portal 신청 승인','',tree.portal_requests.rule,'',...(tree.portal_requests.screens??[]).map(id=>`- ${id} ${byId.get(id)?.title??''}`),'');
 lines.push('### 이번 대상에서 제외','',...tree.excluded.map(x=>`- ${x.screen}: ${x.reason}`),'','### 적용 규칙','',...tree.rules.map(x=>'- '+x));
 return lines.join('\n');
}

/** 설계 메뉴의 배열 순서를 투영하되, 범위 밖 항목의 위치와 상대 순서는 보존한다. */
export function orderByAdminMenu(spec, items, idOf = (item) => item.id) {
 const ids = (spec.menu_tree?.admin_groups ?? []).flatMap((group) => group.screens ?? []);
 const ranks = new Map(ids.map((id, index) => [id, index]));
 const ordered = items.filter((item) => ranks.has(idOf(item)))
  .sort((a, b) => ranks.get(idOf(a)) - ranks.get(idOf(b)));
 let index = 0;
 return items.map((item) => ranks.has(idOf(item)) ? ordered[index++] : item);
}
