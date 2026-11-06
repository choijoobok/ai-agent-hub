/** 정확한 MISSING 정리의 정적 미리보기·로컬 제거 시연. 보안 판정·서버 삭제를 수행하지 않는다. */
function mockupMismatchDeletePlan(scope,parent,query){
  if(scope.parent&&!parent)throw new Error('먼저 상위 대상을 선택하세요.');
  if(query.__unavailable)throw new Error('현재 조회조건 입력을 확인할 수 없습니다. 기존 목록을 유지합니다.');
  if(scope.previewState!=='정상')throw new Error('미리보기 조회 실패 — 기존 목록과 입력을 유지합니다.');
  const inRange=scope.records.filter(record=>(!scope.parent||record.parent===parent)&&scope.filters.every(filter=>{
    const expected=String(query[filter.label]??'').trim();if(!expected||expected==='전체'||expected==='미지정')return true;
    const actual=String(record[filter.field]??'');return filter.match==='eq'?actual===expected:actual.toLowerCase().includes(expected.toLowerCase());
  }));
  const candidates=inRange.filter(record=>record.definition==='MISSING'&&record.kind===scope.kind),excluded=inRange.filter(record=>!candidates.includes(record));
  const blockers=[];
  if(scope.permission!==true)blockers.push('현재 SAVE 권한이 없습니다.');
  if(scope.completeCurrent!==true)blockers.push('완전한 현재 설정 근거를 확인할 수 없습니다.');
  if(scope.safeDeployment!==true)blockers.push('허용 구세대·실행/대기 작업의 미사용 또는 배출 완료를 확인할 수 없습니다.');
  const decisions=candidates.map(record=>{
    const reasons=[];if(record.confirmed!==true)reasons.push('정확한 누락 판정 확인 불가');if(record.permission!==true)reasons.push('현재 SAVE 권한 확인 불가');
    if(record.references===null)reasons.push('현재 참조 조회 불가');else if(record.references.length)reasons.push('현재 참조가 남음 — 소유 화면에서 참조 해소 필요');
    if(reasons.length)blockers.push(record.id+': '+reasons.join(' / '));
    return {...record,allowed:reasons.length===0,rejection:reasons.join(' / ')};
  });
  return {candidates:decisions,excluded,blockers,allowed:decisions.length>0&&blockers.length===0};
}
function bindMismatchDeleteMockup(root,api){
  const doc=root.ownerDocument||root;
  for(const marker of root.querySelectorAll('[data-mismatch-delete-config]')){
    const screen=marker.closest('.screen'),dialog=screen.querySelector('[data-mismatch-delete-dialog]');if(!dialog)continue;
    const config=()=>JSON.parse(marker.dataset.mismatchDeleteConfig),grids=()=>[...screen.querySelectorAll('[data-grid-name]')],gridFor=scope=>grids().find(grid=>grid.dataset.gridName===scope.area);
    const rows=grid=>[...grid.querySelector('.grid > table > tbody')?.children||[]].filter(row=>row.hasAttribute('data-grid-row-state'));
    const values=row=>Object.fromEntries([...row.querySelectorAll('[data-row-field]')].map(cell=>[cell.dataset.rowField,cell.dataset.rowValue]));
    const selectedMaster=()=>screen.querySelector('[data-grid-role="master"] tbody tr.sel'),parent=scope=>scope.parent?values(selectedMaster()||doc.createElement('tr'))[scope.parent]||'':null;
    const query=scope=>{
      const area=scope.parent?gridFor(scope)?.closest('[data-detail-panel]'):screen.querySelector('.body'),form=scope.parent?area?.querySelector('.sf'):[...area?.children||[]].find(element=>element.matches('.sf'));
      const inputs=[...form?.querySelectorAll('input,select')||[]],unavailable=scope.filters.some(filter=>!inputs.some(input=>input.getAttribute('aria-label')===filter.label));
      return {...Object.fromEntries(scope.filters.map(filter=>[filter.label,inputs.find(input=>input.getAttribute('aria-label')===filter.label)?.value||''])),...(unavailable?{__unavailable:true}:{})};
    };
    const dirty=()=>[...screen.querySelectorAll('tbody tr[data-grid-row-state]')].some(row=>Boolean(row.dataset.gridRowState))||[...screen.querySelectorAll('[data-form-original]')].some(field=>field.value!==field.dataset.formOriginal)||[...screen.querySelectorAll('[data-cell-editor][open] [data-edit-value]')].some(input=>input.value!==input.closest('[data-row-field]')?.dataset.rowValue);
    const assertReady=scope=>{if(marker.dataset.mismatchDeleteEditable!=='true'||scope.permission!==true)throw new Error('현재 SAVE 권한으로 일괄 삭제할 수 없습니다.');if(['saving','unknown','conflict','denied'].includes(marker.dataset.mismatchDeleteState))throw new Error('저장 중·결과 불명·충돌 상태에서는 삭제를 시작할 수 없습니다.');if(dirty())throw new Error('현재 화면에 미저장 편집이 있습니다. 저장 또는 행 원복 후 다시 확인하세요.');if(scope.parent&&!parent(scope))throw new Error('먼저 상위 대상을 선택하세요.');};
    const signature=index=>JSON.stringify({config:config(),index,parent:parent(config().scopes[index]),query:query(config().scopes[index]),rows:grids().map(grid=>[grid.dataset.gridName,rows(grid).map(row=>[values(row),row.dataset.gridRowState,row.dataset.rowContext])]),state:marker.dataset.mismatchDeleteState,editable:marker.dataset.mismatchDeleteEditable});
    const body=dialog.querySelector('[data-mismatch-delete-results]'),status=dialog.querySelector('[data-mismatch-delete-status]'),reason=dialog.querySelector('[data-mismatch-delete-reason]'),confirm=dialog.querySelector('[data-mismatch-delete-confirm]'),execute=dialog.querySelector('[data-mismatch-delete-action="execute"]');let snapshot=null;
    const belongs=event=>screen.contains(event.target)||event.composedPath().includes(screen);
    const footer=scope=>gridFor(scope)?.querySelector('.pg');
    const notice=message=>{const note=screen.querySelector('[data-mismatch-delete-notice]');note.hidden=false;note.textContent=message;};
    function open(index){
      const scope=config().scopes[index];try{assertReady(scope);}catch(error){const note=footer(scope);if(note)note.textContent=error.message;notice(error.message);return;}
      snapshot=null;body.replaceChildren();reason.value='';reason.setCustomValidity('');confirm.checked=false;execute.disabled=true;
      dialog.querySelector('[data-mismatch-delete-scope]').textContent=scope.area+' · 현재 조회조건 전체'+(scope.parent?' · 상위 '+parent(scope):' · 선택행/페이지와 무관');
      dialog.querySelector('[data-mismatch-delete-evidence]').textContent=`설정 ${scope.configurationVersion} / 판정 ${scope.diagnosisVersion} / 참조 ${scope.referenceVersion} · 완전한 현재 근거 ${scope.completeCurrent?'확인 예시':'확인 불가'} · 안전 배포 ${scope.safeDeployment?'확인 예시':'확인 불가'}${scope.deploymentEvidence?' · '+scope.deploymentEvidence:''}`;
      try{
        const plan=mockupMismatchDeletePlan(scope,parent(scope),query(scope));snapshot={index,signature:signature(index),plan};
        for(const record of [...plan.candidates,...plan.excluded]){
          const row=doc.createElement('tr'),candidate=plan.candidates.includes(record);for(const value of [record.id,record.name,record.kind,record.definition,record.reason,record.references===null?'확인 불가':record.references.join(' / ')||'없음',candidate?(record.allowed?'삭제 가능':record.rejection):'대상 제외 — 정확한 MISSING 아님']){const cell=doc.createElement('td');cell.textContent=value;row.append(cell);}body.append(row);
        }
        status.textContent=`삭제 후보 ${plan.candidates.length}건 · 제외 ${plan.excluded.length}건. `+(plan.blockers.length?'전체 실행 차단: '+plan.blockers.join(' / '):plan.candidates.length?'새 삭제 사유와 명시 확인 후 전체를 로컬 제거합니다.':'정확한 누락 정의가 없어 실행하지 않습니다.');execute.disabled=!plan.allowed;
      }catch(error){status.textContent=error.message;}
      dialog.showModal();
    }
    const invalidate=()=>{if(dialog.open&&snapshot&&signature(snapshot.index)!==snapshot.signature){snapshot.stale=true;execute.disabled=true;status.textContent='미리보기 이후 상위 대상·조회조건·구성·대상·버전·판정·참조가 바뀌어 낡았습니다. 취소하고 다시 미리보기를 여세요.';}};
    root.addEventListener('click',event=>{
      if(!belongs(event))return;
      const button=event.target.closest('[data-mismatch-delete-open]');if(button&&!button.disabled)open(Number(button.dataset.mismatchDeleteOpen));
      if(event.target.closest('[data-mismatch-delete-action="cancel"]')){snapshot=null;dialog.close();}
      queueMicrotask(invalidate);
    });
    for(const type of ['change','input','focusout','mockup:row-restored'])root.addEventListener(type,event=>{if(belongs(event))queueMicrotask(invalidate);});
    reason.addEventListener('input',()=>reason.setCustomValidity(''));
    dialog.addEventListener('cancel',()=>snapshot=null);
    root.addEventListener('submit',event=>{
      if(event.target!==dialog.querySelector('form'))return;event.preventDefault();
      reason.setCustomValidity(reason.value.trim()?'':'공백이 아닌 삭제 사유를 입력하세요.');if(!event.target.reportValidity())return;
      if(!snapshot){status.textContent='유효한 미리보기를 다시 여세요.';return;}
      const current=config(),scope=current.scopes[snapshot.index];
      try{
        assertReady(scope);if(snapshot.stale||signature(snapshot.index)!==snapshot.signature)throw new Error('미리보기의 범위 또는 근거가 바뀌어 낡았습니다. 다시 확인하세요.');
        const plan=mockupMismatchDeletePlan(scope,parent(scope),query(scope));if(!plan.allowed)throw new Error('모든 후보의 현재 참조·판정·권한을 확인해야 합니다.');if(scope.executionState!=='정상')throw new Error('삭제 요청 실패 예시 — 기존 목록과 사유를 유지합니다. 실제 API는 호출하지 않았습니다.');
        const ids=new Set(plan.candidates.map(record=>record.id)),grid=gridFor(scope),master=grid.dataset.gridRole==='master';
        const targetKey=record=>JSON.stringify([scope.kind,scope.parent?record.parent:null,record.id]),keys=new Set(plan.candidates.map(targetKey));
        for(const row of rows(grid)){
          const context=JSON.parse(row.dataset.rowContext||'[]'),rowParent=scope.parent&&context.length===1?context[0]:null;
          if(keys.has(targetKey({id:values(row)[scope.key],parent:rowParent})))row.remove();
        }
        scope.records=scope.records.filter(record=>!keys.has(targetKey(record)));
        if(master){
          for(const child of grids().filter(child=>child.dataset.gridRole==='detail'))for(const row of rows(child))if(JSON.parse(row.dataset.rowContext||'[]').some(key=>ids.has(key)))row.remove();
          for(const childScope of current.scopes)if(childScope.parent)childScope.records=childScope.records.filter(record=>!ids.has(record.parent));
          const remaining=rows(grid),selected=remaining.find(row=>row.classList.contains('sel'))||remaining[0];remaining.forEach(row=>row.classList.toggle('sel',row===selected));
          if(selected)selected.click();else {screen.dataset.activeMaster='';for(const child of grids().filter(child=>child.dataset.gridRole==='detail')){for(const row of rows(child))row.hidden=true;child.querySelectorAll('[data-edit-action],[data-grid-save],[data-grid-add]').forEach(button=>button.disabled=true);}}
        }else {const shown=rows(grid).filter(row=>!row.hidden),selected=shown.find(row=>row.classList.contains('sel'))||shown[0];shown.forEach(row=>row.classList.toggle('sel',row===selected));}
        marker.dataset.mismatchDeleteConfig=JSON.stringify(current);snapshot=null;dialog.close();
        for(const area of grids()){api.refreshGrid(area);const count=rows(area).filter(row=>!row.hidden).length;area.querySelector('.cnt').textContent=count+'건';}
        const message=`목업: ${plan.candidates.length}건을 로컬 목록에서 제거했습니다. 실제 DB/API 삭제·과거 이력 제거는 수행하지 않았습니다.`;footer(scope).textContent=message;notice(message);
      }catch(error){status.textContent=error.message;}
    });
  }
}
