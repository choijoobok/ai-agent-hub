/** 조회 정책 편집본과 토큰 추출 시연. SQL 허용 여부·서버 저장은 판정하지 않는다. */
function mockupPolicyParameterNames(sql){
  const names=[],seen=new Set();let i=0;
  while(i<sql.length){
    if(sql.startsWith('--',i)){const end=sql.indexOf('\n',i+2);i=end<0?sql.length:end+1;continue;}
    if(sql.startsWith('/*',i)){let depth=1;i+=2;while(i<sql.length&&depth){if(sql.startsWith('/*',i)){depth++;i+=2;}else if(sql.startsWith('*/',i)){depth--;i+=2;}else i++;}continue;}
    const quote=sql[i];
    if(quote==="'"||quote==='"'||quote==='`'||quote==='['){
      const end=quote==='['?']':quote;i++;
      while(i<sql.length){if(sql[i]==='\\'){i+=2;continue;}if(sql[i]===end){if(sql[i+1]===end){i+=2;continue;}i++;break;}i++;}continue;
    }
    const dollar=sql.slice(i).match(/^\$(?:[A-Za-z_][A-Za-z0-9_]*)?\$/);
    if(dollar){const end=sql.indexOf(dollar[0],i+dollar[0].length);i=end<0?sql.length:end+dollar[0].length;continue;}
    const match=sql.slice(i).match(/^#\{([A-Za-z_][A-Za-z0-9_]*)\}/);
    if(match){if(!seen.has(match[1])){seen.add(match[1]);names.push(match[1]);}i+=match[0].length;}else i++;
  }
  return names;
}
function mockupPolicyParameters(sql,previous){
  const reserved={userId:'현재 세션 사용자의 대상 데이터소스 사용자 ID(서버 신원 매핑). 매핑 부재는 오류.',langCd:'현재 세션 언어 코드(서버 공급)'};
  return mockupPolicyParameterNames(sql).map(name=>({'파라미터명':name,'값 출처':Object.hasOwn(reserved,name)?'세션':'LLM','값 설명':reserved[name]??previous.find(row=>row['파라미터명']===name)?.['값 설명']??''}));
}
function mockupPolicyColumns(config,catalog){return catalog?config.columns.filter(column=>String(column[config.catalogKey]??'')===String(catalog)):[];}
function mockupPolicyJoinSelection(ids,columns,key){
  if(!Array.isArray(ids)||!ids.length)throw new Error('조인 컬럼을 하나 이상 선택하세요.');
  if(ids.some(id=>typeof id!=='string'||!id)||new Set(ids).size!==ids.length)throw new Error('조인 컬럼의 정확한 식별자가 없거나 중복입니다.');
  const selected=ids.map(id=>columns.find(column=>column[key]===id));
  if(selected.some(column=>!column))throw new Error('현재 카탈로그에 없는 조인 컬럼입니다. 다시 선택하세요.');
  return selected;
}
function mockupPolicyConstraint(values,columns,selection){
  const field=Object.hasOwn(values,'조건 유형')?'조건 유형':'유형',join=values[field]==='조인',column=selection?selection.ids.length===1?columns.find(column=>column[selection.key]===selection.ids[0]):null:columns.find(column=>column['컬럼명']===values['컬럼명']);
  const type=column?.['자료형']||'확인 필요',range=type==='날짜'||type==='숫자',allowed=['동일 값 매칭',...(range||!values['컬럼명']||join?['범위']:[]),'조인'];
  if(join){let error='';try{if(selection?.confirmed===false)throw new Error('조인 컬럼을 팝업에서 선택 적용하세요.');mockupPolicyJoinSelection(selection?.ids??values['선택 컬럼 식별자']??[],columns,selection?.key??'컬럼 식별자');}catch(problem){error=problem.message;}return {values:{...values,'자료형':null,[field]:'조인','최대 차이':null},field,allowed,error};}
  const kind=allowed.includes(values[field])?values[field]:'',max=kind==='범위'?String(values['최대 차이']??''):'';
  const error=!column?'현재 카탈로그의 단일 컬럼을 선택하세요.':!kind?'조건 유형을 선택하세요.':kind==='범위'&&(max.trim()===''||!Number.isFinite(Number(max))||Number(max)<0||(type==='날짜'&&!Number.isInteger(Number(max))))?'최대 차이는 0 이상이며 날짜는 정수 일수여야 합니다.':'';
  return {values:{...values,'자료형':type,[field]:kind,'최대 차이':max},field,allowed,error};
}
function bindQueryPolicyMockup(root,api){
  const doc=root.ownerDocument||root;
  for(const screen of root.querySelectorAll('[data-query-policy-workspace]')){
    const config=JSON.parse(screen.dataset.queryPolicyWorkspace),grid=name=>[...screen.querySelectorAll('[data-grid-name]')].find(grid=>grid.dataset.gridName===name);
    const columnKey=config.columnKey||'컬럼 식별자',selectionKey=config.selectionKey||'선택 컬럼 식별자';
    const scope=grid(config.scopeList),parameters=grid(config.parameters),constraints=grid(config.constraints),filters=grid(config.filters),master=grid(config.master);
    const board=screen.querySelector('[data-policy-scope-board]'),expression=board?.querySelector('[data-form-value="조건식"]'),status=board?.querySelector('[data-policy-status]');
    if(!scope||!parameters||!constraints||!filters||!master||!expression)continue;
    const rows=grid=>[...grid.querySelector('.grid > table > tbody').children].filter(row=>row.hasAttribute('data-grid-row-state')),cell=(row,key)=>[...row?.querySelectorAll('[data-row-field]')||[]].find(cell=>cell.dataset.rowField===key);
    const value=(row,key)=>cell(row,key)?.dataset.rowValue??'',values=row=>Object.fromEntries([...row.querySelectorAll('[data-row-field]')].map(cell=>[cell.dataset.rowField,cell.dataset.rowValue]));
    const currentMaster=()=>rows(master).find(row=>row.classList.contains('sel')&&row.dataset.gridRowState!=='D'),context=()=>currentMaster()?.dataset.masterClientKey||value(currentMaster(),screen.dataset.masterLink);
    const editable=()=>screen.dataset.policyEditable==='true'&&Boolean(currentMaster())&&currentMaster().dataset.masterLocked!=='true';
    const visible=row=>!row.hidden&&row.isConnected&&row.dataset.gridRowState!=='D';
    const drafts=new WeakMap(),selections=new Map();let selected=null,shown=null;
    const constraintDrafts=new WeakMap(),activeColumns=()=>mockupPolicyColumns(JSON.parse(screen.dataset.queryPolicyWorkspace),value(currentMaster(),config.catalogKey));
    const schedule=()=>queueMicrotask(()=>queueMicrotask(show));
    const draft=row=>{
      const raw=row.dataset.policyRecord||'{}';
      if(!drafts.has(row)||drafts.get(row).raw!==raw){
        const record=JSON.parse(raw),sql=String(record['조건식']??'');
        const params=Array.isArray(record['파라미터'])?record['파라미터']:Array.isArray(record['파라미터 배열'])?record['파라미터 배열']:[];
        drafts.set(row,{raw,record,sql,params:mockupPolicyParameters(sql,params),extracted:String(record.extractedExpression??sql)});
      }
      return drafts.get(row);
    };
    const persist=row=>{
      const state=drafts.get(row);state.record={...state.record,'조건식':state.sql,'파라미터':state.params,extractedExpression:state.extracted};
      state.raw=JSON.stringify(state.record);row.dataset.policyRecord=state.raw;api.markEdited(row);
    };
    const extract=[...screen.querySelectorAll('[data-demo-action]')].find(button=>button.dataset.demoAction==='파라미터 추출');
    const paramHeaders=[...parameters.querySelectorAll('.grid > table > thead > tr > th')];
    const paramFields=[...parameters.querySelectorAll('tbody [data-row-field]')].map(cell=>cell.dataset.rowField);
    const paramTemplate=parameters.querySelector('tbody tr[data-grid-row-state]')?.cloneNode(true);
    function parameterRow(record,index){
      const row=paramTemplate?paramTemplate.cloneNode(true):doc.createElement('tr');
      row.removeAttribute('data-grid-row-state');row.className='';row.removeAttribute('data-policy-record');row.hidden=false;row.removeAttribute('data-row-context');row.removeAttribute('data-master-assigned');
      if(!paramTemplate)for(const header of paramHeaders){const td=doc.createElement('td');if(header.dataset.gridSystem)td.dataset.gridSystem=header.dataset.gridSystem;else {const name=paramFields[Number(header.dataset.gridCol)]||['파라미터명','값 출처','값 설명'][Number(header.dataset.gridCol)];if(name)td.dataset.rowField=name;}row.append(td);}
      row.querySelectorAll('[data-grid-system]').forEach(cell=>{cell.replaceChildren();if(cell.dataset.gridSystem==='number')cell.textContent=String(index+1);});
      row.querySelectorAll('[data-row-field]').forEach(target=>{
        const key=target.dataset.rowField;target.classList.remove('edit');target.removeAttribute('data-edit-rule');target.replaceChildren();target.dataset.rowValue=String(record[key]??'');
        if(key==='값 설명'&&record['값 출처']==='LLM'&&editable()&&selected&&visible(selected)){
          const input=doc.createElement('input');input.value=record[key]||'';input.setAttribute('aria-label',record['파라미터명']+' 값 설명');input.style.width='100%';input.style.boxSizing='border-box';input.dataset.policyParameter=record['파라미터명'];
          input.addEventListener('input',()=>{if(!editable()||!selected||!visible(selected))return;const state=draft(selected),param=state.params.find(param=>param['파라미터명']===input.dataset.policyParameter);if(param?.['값 출처']==='LLM'){param['값 설명']=input.value;target.dataset.rowValue=input.value;persist(selected);}});target.append(input);target.classList.add('edit');
        }else target.textContent=String(record[key]??'—');
      });return row;
    }
    function showParameters(){
      const state=selected?draft(selected):null,body=parameters.querySelector('tbody');body.replaceChildren();
      for(const [index,param]of(state?.params??[]).entries())body.append(parameterRow(param,index));
    }
    function choiceEditor(target,options,locked){
      if(!target)return;
      const signature=JSON.stringify([options,locked]);if(target.dataset.policyEditor===signature)return;
      target.dataset.policyEditor=signature;target.replaceChildren();target.classList.toggle('edit',!locked);
      if(locked){target.textContent=target.dataset.rowValue||'—';return;}
      const details=doc.createElement('details'),summary=doc.createElement('summary'),select=doc.createElement('select');details.dataset.cellEditor='';details.dataset.cellInline='';summary.textContent=target.dataset.rowValue||'—';summary.title='더블 클릭으로 셀 편집';select.dataset.editValue='';select.setAttribute('aria-label',target.dataset.rowField);
      for(const text of ['',...options]){const option=doc.createElement('option');option.value=text;option.textContent=text||'미지정';select.append(option);}select.value=target.dataset.rowValue;details.append(summary,select);target.append(details);
    }
    function constraintState(row,columns=activeColumns()){
      const raw=row.dataset.policyRecord||'{}';if(constraintDrafts.has(row)&&constraintDrafts.get(row).raw===raw)return constraintDrafts.get(row);
      const record=JSON.parse(raw),field=cell(row,'조건 유형')?'조건 유형':'유형',kind=value(row,field),column=columns.find(column=>column['컬럼명']===value(row,'컬럼명'));
      const ids=Array.isArray(record[selectionKey])?[...record[selectionKey]]:kind!=='조인'&&typeof column?.[columnKey]==='string'?[column[columnKey]]:[];
      const state={raw,record,field,kind,ids,pendingIds:Array.isArray(record.joinSelectionDraftIds)?[...record.joinSelectionDraftIds]:[],confirmed:record.joinSelectionConfirmed??(kind==='조인'&&ids.length>0),display:value(row,'컬럼명'),max:value(row,'최대 차이')};constraintDrafts.set(row,state);return state;
    }
    function persistConstraint(row,state){
      state.record={...state.record,...values(row),[selectionKey]:[...state.ids],joinSelectionConfirmed:state.kind==='조인'?state.confirmed:false,joinSelectionDraftIds:state.kind==='조인'?[...state.pendingIds]:[],...(state.kind==='조인'?{'자료형':null,'최대 차이':null}:{'최대 차이':state.kind==='범위'?value(row,'최대 차이'):null})};state.raw=JSON.stringify(state.record);row.dataset.policyRecord=state.raw;api.markEdited(row);
    }
    const joinDialog=doc.createElement('dialog');joinDialog.className='command-dialog';joinDialog.dataset.policyJoinDialog='';joinDialog.style.width='min(900px,94vw)';
    joinDialog.innerHTML='<h3>조인 컬럼 선택</h3><p data-policy-join-context></p><p>선택한 모든 컬럼이 실제 조인에 참여해야 이 항목을 충족합니다. 여러 관계에서 각각 충족할 수 있으며 복합 관계의 전체 키 보호는 유지합니다. 다른 동일 값·범위·조인 항목과는 OR(하나 이상 충족)입니다. 실제 SQL 판정은 서버 구현 후 검증하며 목업에서는 검증 성공으로 표시하지 않습니다.</p><div class="grid" style="max-height:50vh;overflow:auto"><table><thead><tr><th>선택</th><th>컬럼 식별자</th><th>컬럼명</th><th>자료형</th></tr></thead><tbody data-policy-join-results></tbody></table></div><p role="status" data-policy-join-status></p><div class="row actions"><button type="button" class="btn" data-policy-join-apply>적용</button><button type="button" class="btn ghost" data-policy-join-cancel>취소</button></div>';screen.append(joinDialog);let joinSnapshot=null;
    const joinStatus=joinDialog.querySelector('[data-policy-join-status]'),joinApply=joinDialog.querySelector('[data-policy-join-apply]');
    const joinSignature=row=>JSON.stringify([context(),value(currentMaster(),config.catalogKey),screen.dataset.queryPolicyWorkspace,row.dataset.policyRecord,values(row),row.dataset.gridRowState,visible(row),row.closest('[data-detail-panel]')?.hidden,editable(),rows(constraints).find(row=>row.classList.contains('sel'))===row]);
    function openJoin(row){
      if(!editable()||!visible(row))return;const state=constraintState(row);if(state.kind!=='조인')return;
      const columns=activeColumns().filter(column=>typeof column[columnKey]==='string'&&column[columnKey]),selectedIds=state.confirmed?state.ids:state.pendingIds,body=joinDialog.querySelector('[data-policy-join-results]');body.replaceChildren();
      for(const column of columns){const tr=doc.createElement('tr'),first=doc.createElement('td'),input=doc.createElement('input');input.type='checkbox';input.value=column[columnKey];input.checked=selectedIds.includes(input.value);input.dataset.policyJoinChoice='';input.setAttribute('aria-label',column['컬럼명']);first.append(input);tr.append(first);for(const text of [column[columnKey],column['컬럼명'],column['자료형']]){const td=doc.createElement('td');td.textContent=text;tr.append(td);}body.append(tr);}
      joinDialog.querySelector('[data-policy-join-context]').textContent='현재 카탈로그: '+value(currentMaster(),config.catalogKey);joinStatus.textContent=columns.length?'하나 이상 선택한 뒤 한 번에 적용하세요. 취소는 기존 선택과 다른 행을 유지합니다.':'정확한 식별자를 가진 현재 카탈로그 컬럼이 없습니다.';joinApply.disabled=!columns.length;
      joinSnapshot={row,signature:joinSignature(row),stale:false};joinDialog.showModal();
    }
    const invalidateJoin=()=>{if(joinDialog.open&&joinSnapshot&&joinSignature(joinSnapshot.row)!==joinSnapshot.signature){joinSnapshot.stale=true;joinApply.disabled=true;joinStatus.textContent='부모·유형·행·컬럼 후보 또는 권한이 바뀌어 선택 창이 낡았습니다. 취소하고 다시 여세요.';}};
    const closeJoin=()=>{joinSnapshot=null;joinDialog.close();};joinDialog.addEventListener('cancel',()=>joinSnapshot=null);joinDialog.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();closeJoin();}});
    const constraintHelp=doc.createElement('p');constraintHelp.className='hint';constraintHelp.dataset.policyConstraintHelp='';constraintHelp.textContent='필수 조건 제약은 동일 값·범위·조인 항목 중 하나 이상 충족하는 OR입니다. 조인 항목은 선택한 컬럼 모두가 실제 조인에 참여해야 하며 여러 관계를 통해 충족할 수 있습니다. 복합 관계의 전체 키 보호를 유지하며 실제 SQL 검증은 서버 구현 후 수행합니다.';constraints.querySelector('.pg').before(constraintHelp);
    function constraintRows(){
      const columns=activeColumns();
      for(const row of rows(filters))if(!row.hidden)choiceEditor(cell(row,'컬럼명'),columns.map(column=>column['컬럼명']),!editable()||row.dataset.gridRowState==='D');
      for(const row of rows(constraints)){
        if(row.hidden)continue;const state=constraintState(row,columns),locked=!editable()||row.dataset.gridRowState==='D',kind=value(row,state.field);let changed=false;
        if(!locked&&kind!==state.kind){
          if(kind==='조인'){state.pendingIds=[...state.ids];state.ids=[];state.confirmed=false;}
          else if(state.kind==='조인'){state.ids=[];state.pendingIds=[];state.confirmed=false;api.setCell(cell(row,'컬럼명'),'');}
          state.kind=kind;state.display=value(row,'컬럼명');changed=true;
        }
        if(!locked&&state.kind!=='조인'&&value(row,'컬럼명')!==state.display){const column=columns.find(column=>column['컬럼명']===value(row,'컬럼명'));state.ids=typeof column?.[columnKey]==='string'?[column[columnKey]]:[];state.display=value(row,'컬럼명');changed=true;}
        if(!locked&&value(row,'최대 차이')!==state.max){state.max=value(row,'최대 차이');changed=true;}
        const normalized=mockupPolicyConstraint(values(row),columns,{ids:state.ids,key:columnKey,confirmed:state.confirmed}),join=normalized.values[state.field]==='조인';
        for(const key of ['자료형',normalized.field,'최대 차이'])if(value(row,key)!==String(normalized.values[key]??'')){api.setCell(cell(row,key),normalized.values[key]);if(!locked)api.markEdited(row);}
        state.kind=normalized.values[state.field];state.max=value(row,'최대 차이');
        const type=cell(row,'자료형');if(type){type.replaceChildren(doc.createTextNode(join?'해당 없음':value(row,'자료형')||'확인 필요'));type.classList.remove('edit');}
        const target=cell(row,'컬럼명');
        if(join){
          const text=state.confirmed?state.ids.map(id=>columns.find(column=>column[columnKey]===id)?.['컬럼명']||'확인 필요').join(', '):'';
          if(value(row,'컬럼명')!==text)api.setCell(target,text);state.display=text;target.dataset.policyEditor='';
          const signature=JSON.stringify([locked,text,state.confirmed]);if(target.dataset.policyJoinEditor!==signature||!target.querySelector('[data-policy-join-open]')){target.dataset.policyJoinEditor=signature;target.replaceChildren();target.classList.toggle('edit',!locked);if(locked)target.textContent=text||'미선택';else {const button=doc.createElement('button');button.type='button';button.className='btn ghost sm';button.dataset.policyJoinOpen='';button.textContent=text||'컬럼 선택';button.title='현재 카탈로그의 조인 컬럼을 여러 개 선택';target.append(button);}}
        }else {target.dataset.policyJoinEditor='';choiceEditor(target,columns.map(column=>column['컬럼명']),locked);}
        choiceEditor(cell(row,normalized.field),normalized.allowed,locked);
        const max=cell(row,'최대 차이'),range=state.kind==='범위',signature=JSON.stringify([locked,range,normalized.values['자료형'],join]);
        if(max&&max.dataset.policyNumber!==signature){max.dataset.policyNumber=signature;max.replaceChildren();max.classList.toggle('edit',range&&!locked);if(range&&!locked){const details=doc.createElement('details'),summary=doc.createElement('summary'),input=doc.createElement('input');details.dataset.cellEditor='';details.dataset.cellInline='';summary.textContent=max.dataset.rowValue||'—';summary.title='더블 클릭으로 셀 편집';input.dataset.editValue='';input.type='number';input.min='0';input.step=normalized.values['자료형']==='날짜'?'1':'any';input.required=true;input.value=max.dataset.rowValue;input.setAttribute('aria-label','최대 차이');details.append(summary,input);max.append(details);}else max.textContent=join?'해당 없음':max.dataset.rowValue||'—';}
        if(max)max.title=normalized.error||(normalized.values['자료형']==='날짜'?'예: 30일 이하로 제한하려면 최대 차이 30을 입력합니다.':'범위의 최대 차이는 0 이상입니다.');
        if(changed&&!locked)persistConstraint(row,state);
      }
    }
    function newSwitches(){
      for(const grid of [scope,filters,constraints])for(const row of rows(grid))if(row.dataset.gridRowState==='C')for(const field of ['사용 여부','해제 허용']){
        const target=cell(row,field),toggle=target?.querySelector('[data-config-switch]');if(!target||!toggle)continue;
        if(!target.dataset.rowValue)api.setCell(target,toggle.dataset.switchOff||'false');
      }
    }
    function show(){
      const key=context(),current=rows(scope).filter(visible);
      if(selected&&shown===key&&visible(selected))selections.set(key,selected);
      selected=current.find(row=>row.classList.contains('sel'))||selections.get(key);
      if(!current.includes(selected))selected=current[0]||null;
      shown=key;for(const row of rows(scope))row.classList.toggle('sel',row===selected);
      if(selected)selections.set(key,selected);
      const state=selected?draft(selected):null;
      if(expression.value!==(state?.sql??''))expression.value=state?.sql??'';
      expression.readOnly=!editable()||!selected;expression.disabled=!selected;expression.style.fontFamily='monospace';expression.rows=12;
      if(extract)extract.disabled=!editable()||!selected;
      status.textContent=!selected?'스코프를 추가하거나 선택하세요.':state.sql!==state.extracted?'조건식 변경 — 파라미터 추출이 낡았습니다. 다시 추출하세요. 기존 값 설명은 보존됩니다.':'선택 스코프의 편집본입니다. SQL 허용 여부·권한은 서버 검증이 필요합니다.';
      newSwitches();constraintRows();
      // 설명 입력의 커서와 미완성 입력을 유지한다. 행/권한/추출이 바뀔 때만 다시 그린다.
      const stamp=JSON.stringify([selected?rows(scope).indexOf(selected):-1,key,editable(),state?.params]);
      if(parameters.dataset.policyStamp!==stamp){parameters.dataset.policyStamp=stamp;showParameters();}
      parameters.querySelector('.cnt').textContent=(state?.params.length||0)+'건';parameters.querySelector('.pg').textContent=state?.params.length?'선택 스코프의 파라미터 · 서버 검증 전 편집본':'조건식을 입력하고 파라미터를 추출하세요.';
    }
    expression.addEventListener('input',()=>{if(expression.readOnly||!selected||!visible(selected))return;const state=draft(selected);state.sql=expression.value;persist(selected);status.textContent='조건식 변경 — 파라미터 추출이 낡았습니다. 다시 추출하세요.';});
    root.addEventListener('click',event=>{
      if(!screen.contains(event.target)&&!event.composedPath().includes(screen))return;
      const row=event.target.closest('tbody tr[data-grid-row-state]');if(row&&scope.contains(row)&&visible(row)){selected=row;selections.set(context(),row);}
      if(event.target.closest('[data-demo-action="파라미터 추출"]')&&editable()&&selected){const state=draft(selected);state.params=mockupPolicyParameters(state.sql,state.params);state.extracted=state.sql;persist(selected);parameters.dataset.policyStamp='';}
      if(event.target.closest('[data-policy-join-open]'))openJoin(event.target.closest('tr'));
      if(event.target.closest('[data-policy-join-cancel]'))closeJoin();
      if(event.target.closest('[data-policy-join-apply]')){
        try{
          if(!joinSnapshot||joinSnapshot.stale||joinSignature(joinSnapshot.row)!==joinSnapshot.signature||!editable()||!visible(joinSnapshot.row)||joinSnapshot.row.closest('[data-detail-panel]')?.hidden)throw new Error('부모·유형·행·컬럼 후보 또는 권한이 바뀌어 선택 창이 낡았습니다. 다시 여세요.');
          const row=joinSnapshot.row,state=constraintState(row),checked=[...joinDialog.querySelectorAll('[data-policy-join-choice]:checked')].map(input=>input.value),ids=state.confirmed?[...state.ids.filter(id=>checked.includes(id)),...checked.filter(id=>!state.ids.includes(id))]:checked,columns=mockupPolicyJoinSelection(ids,activeColumns(),columnKey);
          if(state.kind!=='조인')throw new Error('현재 조인 유형이 아닙니다.');
          if(!state.confirmed||JSON.stringify(state.ids)!==JSON.stringify(ids)){state.ids=ids;state.pendingIds=[];state.confirmed=true;state.display=columns.map(column=>column['컬럼명']).join(', ');api.setCell(cell(row,'컬럼명'),state.display);api.setCell(cell(row,'자료형'),null);api.setCell(cell(row,'최대 차이'),null);persistConstraint(row,state);}closeJoin();
        }catch(error){joinStatus.textContent=error.message;}
      }
      schedule();queueMicrotask(invalidateJoin);
    });
    for(const type of ['change','focusout','keydown'])root.addEventListener(type,event=>{if(screen.contains(event.target)||event.composedPath().includes(screen)){schedule();queueMicrotask(invalidateJoin);}});
    screen.addEventListener('mockup:row-restored',event=>{drafts.delete(event.target);constraintDrafts.delete(event.target);parameters.dataset.policyStamp='';schedule();});
    screen.addEventListener('click',event=>{
      const save=event.target.closest('[data-open-command="저장"],[data-grid-save]');if(!save||!editable())return;
      const invalid=rows(constraints).filter(visible).map(row=>mockupPolicyConstraint(values(row),activeColumns(),{ids:constraintState(row).ids,key:columnKey,confirmed:constraintState(row).confirmed})).find(result=>result.error);
      const stale=rows(scope).filter(visible).find(row=>draft(row).sql!==draft(row).extracted);
      if(invalid||stale){event.preventDefault();event.stopPropagation();const footer=master.querySelector('.pg');footer.textContent=invalid?'필수 조건 제약을 확인하세요: '+invalid.error:'조건식 변경 후 파라미터를 다시 추출하세요.';}
    },true);
    show();
  }
}
