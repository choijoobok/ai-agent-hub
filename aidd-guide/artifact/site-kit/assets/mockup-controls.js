/** 정본에서 생성한 목업의 탐색·선택 시연. 서버 저장이나 권한 판정을 수행하지 않는다. */
function applyRequestContext(root, query) {
  const params=new URLSearchParams(query), requestNo=params.get('requestNo');
  const targetId=params.get('targetId');
  if(targetId&&/^[a-zA-Z0-9_-]+$/.test(targetId))root.querySelectorAll('[data-master-link="시스템 식별자"]').forEach(screen=>{
    const rows=[...screen.querySelectorAll('[data-grid-role="master"] tbody tr')],row=rows.find(row=>row.querySelector('[data-row-field="시스템 식별자"]')?.dataset.rowValue===targetId);
    if(row){screen.dataset.masterInitial=targetId;row.click();}
  });
  if(!requestNo||!/^[a-zA-Z0-9_-]+$/.test(requestNo))return;
  root.querySelectorAll('[data-request-number]').forEach(e=>{e.textContent=requestNo;});
  root.querySelectorAll('[data-request-navigation]').forEach(a=>{
    const parts=a.getAttribute('href').split('?'), values=new URLSearchParams((parts[1]??'').split('#')[0]);
    values.set('requestNo',requestNo);a.setAttribute('href',parts[0]+'?'+values.toString()+'#main');
  });
}
function resolveMockupLookupAdd(rule,parent,key,existing) {
  if(!parent)throw new Error('먼저 상위 대상을 선택하세요.');
  if(rule.state!=='정상')throw new Error('기준정보 조회 실패 — 기존 입력을 유지합니다.');
  const record=rule.records.find(row=>row[rule.key]===key);
  if(!record||record['선택 가능']===false)throw new Error('현재 선택 가능한 기준정보가 아닙니다.');
  const current=existing.find(row=>row[rule.fixed]===parent&&row[rule.key]===key);
  if(current){
    if(rule.mode==='조회 팝업'&&rule.active&&['false','N','0'].includes(String(current[rule.active]))){if(current.__editable===false)throw new Error('현재 소속의 변경 권한이 없습니다.');return {operation:'reactivate',key};}
    throw new Error('이미 추가된 대상입니다. 기존 행을 확인하세요.');
  }
  return {operation:'insert',values:{...rule.defaults,[rule.fixed]:parent,[rule.key]:key}};
}
function bindDeclaredMockup(root) {
  applyRequestContext(root,location.search);
  const rowValues=row=>Object.fromEntries([...row.querySelectorAll('[data-row-field]:not([data-calculated-value])')].map(cell=>[cell.dataset.rowField,cell.dataset.rowValue]));
  const originals=new WeakMap();
  root.querySelectorAll('tbody tr[data-grid-row-state]').forEach(row=>originals.set(row,{values:rowValues(row),html:row.innerHTML}));
  const refreshGrid=grid=>{
    if(!grid)return;
    const header=grid.querySelector(':scope > .grid > table > thead > tr');
    const business=header?[...header.querySelectorAll('[data-grid-base-width]')]:[];
    if(business.length){
      const shown=business.filter(cell=>!cell.hidden);
      shown.forEach((cell,index)=>cell.style.width=index===shown.length-1?'auto':cell.dataset.gridBaseWidth+'px');
      const fixed=[...header.querySelectorAll('[data-grid-system]')].reduce((sum,cell)=>sum+Number.parseFloat(cell.style.width),0);
      const table=header.closest('table');
      // 업무 열을 모두 감춰도 AG Grid의 빈 중앙 영역과 오른쪽 고정 영역을 유지한다.
      for(const row of [header,...table.querySelectorAll(':scope > tbody > tr[data-grid-row-state]')]){
        const gap=row.querySelector(':scope > [data-grid-empty-space]');
        if(shown.length){gap?.remove();continue;}
        if(!gap){
          const space=(root.ownerDocument||root).createElement(row===header?'th':'td');
          space.setAttribute('data-grid-empty-space','');space.setAttribute('aria-hidden','true');
          space.style.width='auto';
          row.insertBefore(space,row.querySelector(':scope > [data-grid-system="delete"]'));
        }
      }
      table.style.minWidth=(fixed+shown.reduce((sum,cell)=>sum+Number(cell.dataset.gridBaseWidth),0))+'px';
      table.style.width='100%';
    }
    const rows=[...grid.querySelectorAll('tbody tr[data-grid-row-state]')].filter(row=>!row.hidden);
    rows.forEach((row,index)=>{
      const number=row.querySelector('[data-grid-row-number]');
      if(number)number.textContent=String(Number(grid.dataset.gridRowOffset||0)+index+1);
      const select=row.querySelector('[data-grid-select-row]');
      if(select){select.setAttribute('aria-label',`${index+1}번 행 선택`);row.classList.toggle('sel',select.checked);}
    });
    const selectAll=grid.querySelector('[data-grid-select-all]');
    if(selectAll){
      const inputs=rows.flatMap(row=>[...row.querySelectorAll('[data-grid-select-row]:not(:disabled)')]);
      const checked=inputs.filter(input=>input.checked).length;
      selectAll.checked=inputs.length>0&&checked===inputs.length;
      selectAll.indeterminate=checked>0&&checked<inputs.length;
      selectAll.disabled=inputs.length===0;
    }
  };
  root.querySelectorAll('[data-grid-view]').forEach(refreshGrid);
  const setRowState=(row,state)=>{
    row.dataset.gridRowState=state;
    const label=row.querySelector('[data-row-state]');
    if(label)label.textContent=({C:'신규',U:'수정',D:'삭제'})[state]||'';
    const revert=row.querySelector('[data-revert-row]');if(revert)revert.hidden=state!=='U';
    row.style.textDecoration=state==='D'?'line-through':'';
    const remove=row.querySelector('[data-grid-delete]');
    if(remove&&state==='D'){remove.innerHTML='↶';remove.setAttribute('aria-label','삭제 표시 해제');}
    if(state==='D')row.querySelectorAll('td[data-row-field] input,td[data-row-field] select,td[data-row-field] textarea,td[data-row-field] [data-config-switch]').forEach(input=>input.disabled=true);
  };
  const markEdited=row=>{
    if(!row?.hasAttribute('data-grid-row-state')||['C','D'].includes(row.dataset.gridRowState))return;
    setRowState(row,JSON.stringify(rowValues(row))===JSON.stringify(originals.get(row)?.values)?'':'U');
  };
  const setCell=(cell,value)=>{
    value=String(value??'');cell.dataset.rowValue=value;
    const toggle=cell.querySelector('[data-grid-switch]'),editor=cell.querySelector('[data-cell-editor]');
    if(toggle){
      const known=[toggle.dataset.switchOn,toggle.dataset.switchOff].includes(value);
      toggle.hidden=!known;
      if(known){toggle.setAttribute('aria-checked',String(value===toggle.dataset.switchOn));cell.querySelector('[data-switch-unknown]')?.remove();}
      else {toggle.removeAttribute('aria-checked');if(!cell.querySelector('[data-switch-unknown]'))toggle.insertAdjacentHTML('afterend','<span data-switch-unknown>확인 필요</span>');}
    }
    else if(editor){editor.querySelectorAll('[data-edit-value]').forEach(input=>input.value=value);editor.querySelector('summary').textContent=value||'—';const choice=editor.querySelector('[data-combo-value]');if(choice)choice.textContent='현재 선택: '+(value||'미지정');}
    else cell.textContent=value||'—';
  };
  let newRowNo=0;
  const appendRow=(grid,seed)=>{
    const template=grid.querySelector('template[data-row-template]');if(!template)return null;
    const row=template.content.firstElementChild.cloneNode(true);newRowNo++;
    row.querySelectorAll('[id]').forEach(e=>e.id+=`-new-${newRowNo}`);
    row.querySelectorAll('[data-auto-editor]').forEach(e=>{e.removeAttribute('data-auto-editor');e.open=false;});
    row.querySelectorAll('[data-edit-value]').forEach(e=>e.value='');
    row.querySelectorAll('[data-row-value]').forEach(e=>e.dataset.rowValue='');
    row.querySelectorAll('[data-combo-value]').forEach(e=>e.textContent='현재 선택: 미지정');
    row.querySelectorAll('[data-cell-editor] summary').forEach(e=>e.textContent='선택/입력 ✎');
    if(seed){row.querySelectorAll('[data-row-field]').forEach(cell=>setCell(cell,seed[cell.dataset.rowField]));row.dataset.rowContext=JSON.stringify([grid.dataset.gridRole==='master'?seed[grid.closest('.screen').dataset.masterLink]:grid.closest('.screen').dataset.activeMaster]);row.dataset.masterLocked='false';row.dataset.masterAssigned='true';}
    grid.querySelector('tbody .empty')?.closest('tr').remove();row.classList.remove('sel');row.removeAttribute('data-remove-blocked');setRowState(row,'C');grid.querySelector('tbody').prepend(row);
    if(seed&&grid.dataset.gridRole==='master'){grid.querySelectorAll('tbody tr.sel').forEach(node=>node.classList.remove('sel'));row.classList.add('sel');}
    if(grid.dataset.addChoice){const remove=(root.ownerDocument||root).createElement('button');remove.type='button';remove.className='btn ghost sm';remove.setAttribute('data-remove-new-row','');remove.setAttribute('aria-label','추가한 행 제거');remove.title='저장 전 새 행만 제거';remove.textContent='×';row.querySelector('[data-grid-state-cell]')?.append(remove);}
    refreshGrid(grid);refreshRowConditions(row);grid.querySelector('.cnt').textContent=`${grid.querySelectorAll('tbody tr[data-grid-row-state]').length}건`;grid.querySelector('.pg').textContent='목업 새 행 — 저장 전 입력입니다.';return row;
  };
  const parentOf=(grid,rule)=>{const parent=grid.closest('.screen').querySelector('[data-grid-role="master"] tr.sel');return parent?.dataset.masterLocked==='true'?null:parent?.querySelector(`[data-row-field="${rule.parent}"]`)?.dataset.rowValue;};
  const rowsOfChoice=grid=>[...grid.querySelectorAll('tbody tr[data-grid-row-state]')];
  const choiceValues=(grid,rule)=>rowsOfChoice(grid).map(row=>{const toggle=row.querySelector(`[data-row-field="${rule.active}"] [data-config-switch]`);return {...rowValues(row),__editable:Boolean(toggle&&!toggle.disabled)};});
  const addError=(grid,message,editor)=>{grid.querySelector('.pg').textContent=message;if(editor){let note=editor.querySelector('[data-add-error]');if(!note){note=(root.ownerDocument||root).createElement('p');note.setAttribute('data-add-error','');note.setAttribute('role','alert');(editor.querySelector('.mb')??editor).append(note);}note.textContent=message;}};
  const validateChoice=(editor,value)=>{
    const grid=editor.closest('[data-add-choice]'),rule=grid?JSON.parse(grid.dataset.addChoice):null,cell=editor.closest('[data-row-field]');
    if(rule?.mode!=='기준정보 콤보'||cell?.dataset.rowField!==rule.key)return true;
    try{const row=cell.closest('tr'),parent=rowValues(row)[rule.fixed];if(parentOf(grid,rule)!==parent)throw new Error('상위 대상이 변경되었습니다.');resolveMockupLookupAdd(rule,parent,value,rowsOfChoice(grid).filter(node=>node!==row).map(rowValues));editor.querySelector('[data-add-error]')?.remove();return true;}
    catch(error){addError(grid,error.message,editor);return false;}
  };
  const searchLookup=dialog=>{
    const grid=dialog.closest('[data-add-choice]'),rule=JSON.parse(grid.dataset.addChoice),body=dialog.querySelector('[data-lookup-results]'),status=dialog.querySelector('[data-lookup-status]');body.replaceChildren();dialog.querySelector('[data-lookup-apply]').disabled=true;
    if(rule.state!=='정상'){status.textContent='Portal 사용자 조회 실패 — 다시 조회하세요. 기존 입력은 유지합니다.';return;}
    const records=rule.records.filter(record=>[...dialog.querySelectorAll('[data-lookup-filter]')].every(input=>String(record[input.dataset.lookupFilter]??'').toLowerCase().includes(input.value.trim().toLowerCase())));
    for(const record of records){const row=(root.ownerDocument||root).createElement('tr'),first=row.insertCell(),radio=(root.ownerDocument||root).createElement('input');radio.type='radio';radio.name='lookup-choice';radio.value=record[rule.key];radio.setAttribute('data-lookup-choice','');radio.setAttribute('aria-label',record[rule.key]);let reason='선택 가능';try{const result=resolveMockupLookupAdd(rule,dialog.dataset.parentKey,record[rule.key],choiceValues(grid,rule));if(result.operation==='reactivate')reason='중지된 기존 소속 재활성화';}catch(error){radio.disabled=true;reason=error.message;}first.append(radio);for(const field of rule.columns)row.insertCell().textContent=String(record[field]??'—');row.insertCell().textContent=reason;body.append(row);}
    status.textContent=records.length?`${records.length}명 조회. 선택 후 편집 행에 반영합니다.`:'조회 결과가 없습니다.';
  };
  const restoreRow=row=>{
    const selected=row.querySelector('[data-grid-select-row]')?.checked;
    row.innerHTML=originals.get(row).html;setRowState(row,'');
    const select=row.querySelector('[data-grid-select-row]');if(select)select.checked=selected;
    refreshGrid(row.closest('[data-grid-view]'));
  };
  const refreshRowConditions = row => {
    if (!row) return;
    const values = Object.fromEntries([...row.querySelectorAll('[data-row-field]')].map(cell=>[cell.dataset.rowField,cell.dataset.rowValue]));
    row.querySelectorAll('[data-edit-rule]').forEach(cell=>{
      const allowed=JSON.parse(cell.dataset.editRule).every(rule=>rule.values.includes(values[rule.field]));
      const toggle=cell.querySelector('[data-config-switch]');
      if(toggle){toggle.disabled=!allowed||toggle.dataset.switchReadonly==='true';cell.classList.toggle('edit',allowed&&toggle.dataset.switchReadonly!=='true');return;}
      const editor=cell.querySelector('[data-cell-editor]');
      if(!editor){cell.classList.remove('edit');return;}
      editor.hidden=!allowed;
      if(!allowed)editor.open=false;
      cell.querySelector('[data-locked-value]').hidden=allowed;
      cell.classList.toggle('edit',allowed);
    });
  };
  const editingValues=new WeakMap();
  const finishInline=(editor,cancel=false)=>{
    if(!editor.open)return;
    if(editor.hasAttribute('data-cell-large-view')){editor.open=false;return;}
    const input=editor.querySelector('[data-edit-value]'),cell=editor.closest('[data-row-field]');
    if(cancel)input.value=editingValues.get(editor)??input.value;
    if(!cancel&&!validateChoice(editor,input.value)){input.value=cell.dataset.rowValue;return;}
    if(cancel)editor.querySelector('[data-add-error]')?.remove();
    cell.dataset.rowValue=input.value;
    editor.querySelector('summary').textContent=(input.tagName==='SELECT'?input.selectedOptions[0]?.textContent:input.value)||'—';
    cell.title=input.value;editor.open=false;
    refreshRowConditions(cell.closest('tr'));markEdited(cell.closest('tr'));
  };
  const openCell=editor=>{
    if(editor.hidden||editor.open||editor.closest('tr[data-grid-row-state="D"]'))return;
    const input=editor.hasAttribute('data-cell-large-view')?editor.querySelector('textarea'):editor.querySelector('input:not(:disabled):not([readonly]),select:not(:disabled),textarea:not([readonly]):not(:disabled)');
    if(!input)return;
    hideTip();editingValues.set(editor,input.value);editor.open=true;input.focus();
    if(editor.hasAttribute('data-cell-large')){
      const rect=editor.closest('td').getBoundingClientRect(),width=(root.ownerDocument||root).documentElement.clientWidth;
      input.style.left=Math.max(8,Math.min(rect.left,width-Math.min(560,width-16)-8))+'px';
      input.style.top=Math.max(8,Math.min(rect.top,(root.ownerDocument||root).documentElement.clientHeight-210))+'px';
    }
    if(input.tagName==='INPUT'&&input.type!=='checkbox')input.select();
  };
  // 상세 설명 한 곳을 재사용한다. 문서와 ShadowRoot 모두 같은 탐색/툴팁 동작을 쓴다.
  const doc = root.ownerDocument || root;
  const tip = doc.createElement('div');
  tip.className = 'annotation-tooltip';
  tip.id = 'annotation-tooltip';
  tip.setAttribute('role', 'tooltip');
  tip.setAttribute('popover', 'manual');
  tip.hidden = true;
  (root.body || root).append(tip);
  let tipBadge = null, dismissTimer;
  const hideTip = () => {
    clearTimeout(dismissTimer);
    tipBadge?.removeAttribute('aria-describedby');
    tipBadge = null;
    if (tip.matches(':popover-open')) tip.hidePopover();
    tip.hidden = true;
  };
  const showTip = badge => {
    clearTimeout(dismissTimer);
    if (badge === tipBadge) return;
    const source = root.getElementById(`note-${badge.dataset.note}`)?.querySelector('div');
    if (!source) return;
    hideTip();
    const content = source.cloneNode(true);
    // 툴팁은 설명 전용이다. 링크 조작은 원래 상세 설명에서 수행한다.
    content.querySelectorAll('[id]').forEach(e => e.removeAttribute('id'));
    content.querySelectorAll('a').forEach(a => a.replaceWith(doc.createTextNode(a.textContent)));
    const tipHost = badge.closest('dialog') || root.body || root;
    if (tip.parentNode !== tipHost) tipHost.append(tip);
    tip.replaceChildren(content);
    tipBadge = badge;
    badge.setAttribute('aria-describedby', tip.id);
    tip.hidden = false;
    if (tip.showPopover) tip.showPopover();
    const anchor = badge.getBoundingClientRect(), gap = 8, margin = 12;
    const width = doc.documentElement.clientWidth, height = doc.documentElement.clientHeight;
    tip.style.left = `${Math.max(margin, Math.min(anchor.left, width - tip.offsetWidth - margin))}px`;
    const below = height - anchor.bottom - gap - margin, above = anchor.top - gap - margin;
    const downward = below >= Math.min(tip.scrollHeight, height * .65) || below >= above;
    tip.style.maxHeight = `${Math.max(40, Math.min(height * .65, downward ? below : above))}px`;
    tip.style.top = `${Math.max(margin, downward ? anchor.bottom + gap : anchor.top - tip.offsetHeight - gap)}px`;
  };
  const scheduleDismiss = () => {
    clearTimeout(dismissTimer);
    dismissTimer = setTimeout(() => {
      if (!tip.matches(':hover') && !tipBadge?.matches(':hover') && root.activeElement !== tipBadge) hideTip();
    }, 150);
  };
  root.addEventListener('pointerover', event => {
    const badge = event.target.closest('[data-note]');
    if (badge) showTip(badge);
    else if (tip.contains(event.target)) clearTimeout(dismissTimer);
  });
  root.addEventListener('pointerout', event => {
    if (event.target.closest('[data-note]') || tip.contains(event.target)) scheduleDismiss();
  });
  root.addEventListener('focusin', event => {
    const badge = event.target.closest('[data-note]');
    if (badge) showTip(badge);
  });
  root.addEventListener('focusout', scheduleDismiss);
  doc.addEventListener('keydown', event => { if (event.key === 'Escape' && tipBadge) { event.preventDefault(); hideTip(); } });
  root.addEventListener('scroll', event => { if (!tip.contains(event.target)) hideTip(); }, true);
  if (root.host) doc.addEventListener('scroll', hideTip, true);
  doc.defaultView.addEventListener('resize', hideTip);
  const reveal = destination => {
    for(let p=destination.parentElement;p;p=p.parentElement)if(p.matches('details'))p.open=true;
    destination.setAttribute('tabindex','-1');
    destination.focus({preventScroll:true});
    destination.scrollIntoView({block:'start'});
  };
  const activate = (screen, name, context) => {
    screen.querySelectorAll('[data-detail-tab]').forEach(b => {
      const active = b.dataset.detailTab === name;
      b.classList.toggle('on', active);
      b.setAttribute('aria-selected', String(active));
    });
    screen.querySelectorAll('[data-detail-panel]').forEach(p => {
      p.hidden = p.dataset.detailPanel !== name;
      if (!p.hidden && context !== undefined) {
        p.querySelectorAll('tbody tr[data-row-context]').forEach(row => {
          row.hidden = Boolean(context) && !JSON.parse(row.dataset.rowContext).includes(context);
        });
        const chip = p.querySelector('[data-context-label]');
        if (chip) { chip.hidden = !context; chip.querySelector('span').textContent = context || ''; }
        p.querySelectorAll('[data-grid-view]').forEach(grid => {
          const all = [...grid.querySelectorAll('tbody tr[data-row-context]')];
          const shown = all.filter(row=>!row.hidden).length;
          grid.querySelector('.cnt').textContent = context ? `${shown}건 / 전체 ${all.length}건` : `${all.length}건`;
          grid.querySelector('.pg').textContent = context ? `${context} 관련 ${shown}건 · 전체 보기로 해제` : `총 ${all.length}건 · 1 페이지`;
          refreshGrid(grid);
        });
      }
    });
  };
  root.addEventListener('click', event => {
    const rowAction=event.target.closest?.('[data-row-action]');
    if(rowAction){
      if(rowAction.disabled||rowAction.closest('tr')?.dataset.gridRowState==='D')return;
      const doc=root.ownerDocument||root,dialog=doc.createElement('dialog');
      dialog.className='command-dialog';dialog.setAttribute('data-row-action-dialog','');dialog.style.width='min(560px,90vw)';
      const title=doc.createElement('h3');title.textContent=rowAction.dataset.rowAction+' — 검토용 예시';dialog.append(title);
      const notice=doc.createElement('p');notice.textContent='정적 목업 결과입니다. 실제 접속 테스트나 외부 요청을 전송하지 않았습니다.';dialog.append(notice);
      const list=doc.createElement('dl');
      for(const [key,value]of Object.entries(JSON.parse(rowAction.dataset.rowActionResult||'{}'))){const term=doc.createElement('dt'),description=doc.createElement('dd');term.textContent=key;description.textContent=String(value??'확인 필요');list.append(term,description);}
      dialog.append(list);const actions=doc.createElement('div');actions.className='row actions';const close=doc.createElement('button');close.type='button';close.className='btn ghost';close.textContent='닫기';close.setAttribute('data-close-row-action','');close.addEventListener('click',()=>dialog.close());actions.append(close);dialog.append(actions);
      dialog.addEventListener('close',()=>dialog.remove(),{once:true});(root.body||root).append(dialog);dialog.showModal();return;
    }
    const target = event.target;
    const cellSummary=target.closest('[data-cell-inline] > summary,[data-cell-popup] > summary,[data-cell-large-view] > summary');
    if(cellSummary)event.preventDefault();
    if(cellSummary?.parentElement.hasAttribute('data-cell-large-view')){openCell(cellSummary.parentElement);return;}
    const deleted=target.closest('tr[data-grid-row-state="D"]');
    if(deleted&&target.closest('[data-cell-editor]')){event.preventDefault();return;}
    const resetSearch=target.closest('[data-search-reset]');
    if(resetSearch&&!resetSearch.disabled){resetSearch.closest('.sf').querySelectorAll('input,select').forEach(input=>input.value='');return;}
    const rowDelete=target.closest('[data-grid-delete]');
    if(rowDelete&&!rowDelete.disabled){
      const row=rowDelete.closest('tr'),grid=row.closest('[data-grid-view]');
      if(row.dataset.removeBlocked){grid.querySelector('.pg').textContent='삭제 차단 — 사용 중인 용도 참조를 먼저 해소하세요.';return;}
      if(row.dataset.gridRowState==='C'){row.remove();grid.querySelector('.cnt').textContent=grid.querySelectorAll('tbody tr').length+'건';refreshGrid(grid);return;}
      if(row.dataset.gridRowState==='D'){restoreRow(row);}
      else setRowState(row,'D');
      return;
    }
    const toggle=target.closest('[data-config-switch]');
    if(toggle){
      const cell=toggle.closest('[data-row-field]'),row=cell.closest('tr');
      if(toggle.disabled||toggle.dataset.switchReadonly==='true'||row?.dataset.gridRowState==='D')return;
      const old=cell.dataset.rowValue;
      const pair=[toggle.dataset.switchOn,toggle.dataset.switchOff];
      if(!pair.includes(old))return;
      const on=old===pair[0];
      cell.dataset.rowValue=pair[on?1:0];toggle.setAttribute('aria-checked',String(!on));
      refreshRowConditions(row);markEdited(row);return;
    }
    const revertRow=target.closest('[data-revert-row]');
    if(revertRow){restoreRow(revertRow.closest('tr'));return;}
    const read=target.closest('[data-grid-read]'),save=target.closest('[data-grid-save]:not([data-native-save])');
    if((read&&!read.disabled)||(save&&!save.disabled)){
      const grid=(read||save).closest('[data-grid-view]');
      if(!grid)return;
      grid.querySelector('.pg').textContent=read?'목업 조회 조작 — 대상·조건과 미저장 입력을 확인합니다. 실제 서버 조회는 하지 않습니다.':'목업 저장 조작 — 소유 저장 범위 전체를 확인합니다. 실제 서버 저장은 하지 않았습니다.';
      return;
    }
    const badge=target.closest('[data-note]');
    if(badge){hideTip();const destination=root.getElementById(`note-${badge.dataset.note}`);if(destination){event.preventDefault();event.stopPropagation();badge.closest('dialog')?.close();reveal(destination);}return;}
    const figureLink = target.closest('[data-open-figure]');
    if(figureLink){hideTip();const destination=root.getElementById(figureLink.dataset.openFigure);if(destination)reveal(destination);return;}
    const command = target.closest('[data-open-command]');
    if (command) {
      hideTip();
      const dialog=[...command.closest('.screen').querySelectorAll('[data-command]')].find(d=>d.dataset.command===command.dataset.openCommand);
      dialog?.showModal(); return;
    }
    if (target.closest('[data-close-command]')) { hideTip(); target.closest('dialog').close(); return; }
    if (target.closest('[data-add-command-target]')) {
      const group=target.closest('form').querySelector('[data-command-targets]');
      const row=group.firstElementChild.cloneNode(true);
      row.querySelectorAll('select').forEach(e=>e.selectedIndex=0);
      group.append(row);
      group.querySelectorAll('[data-remove-command-target]').forEach(e=>e.disabled=false); return;
    }
    if (target.closest('[data-remove-command-target]')) {
      const row=target.closest('[data-command-target]'), group=row.parentElement;
      if(group.children.length>1)row.remove();
      if(group.children.length===1)group.querySelector('[data-remove-command-target]').disabled=true; return;
    }
    const localLink = target.closest('a[href^="#"]');
    if (localLink) {
      let key;
      try { key = decodeURIComponent(localLink.getAttribute('href').slice(1)); } catch { return; }
      const destination = root.getElementById(key);
      if (destination) {
        event.preventDefault();
        reveal(destination);
        destination.querySelectorAll('[data-auto-editor]').forEach(editor=>editor.open=true);
        return;
      }
    }
    const remove=target.closest('[data-remove-demo-row]');
    if(remove){
      const grid=remove.closest('[data-grid-view]'),body=grid.querySelector('tbody');
      const selected=body.querySelector('tr.sel')??body.lastElementChild;
      if(selected?.dataset.removeBlocked){
        grid.querySelector('.pg').textContent=`삭제 차단 — ${selected.dataset.removeBlocked} 용도에서 사용 중입니다. 모델 용도 설정에서 참조를 해소한 뒤 삭제하세요.`;
        return;
      }
      if(selected&&!selected.querySelector('.empty'))selected.remove();
      const count=body.querySelectorAll('tr').length;
      grid.querySelector('.cnt').textContent=`${count}건`;
      grid.querySelector('.pg').textContent='목업 편집 행 제거 — 서버에는 반영하지 않았습니다.';
      refreshGrid(grid);
      return;
    }
    if(target.closest('[data-remove-new-row]')){const row=target.closest('tr'),grid=row.closest('[data-grid-view]');if(row.dataset.gridRowState==='C')row.remove();refreshGrid(grid);return;}
    if(target.closest('[data-lookup-close]')){target.closest('dialog').close();return;}
    if(target.closest('[data-lookup-search]')){searchLookup(target.closest('dialog'));return;}
    if(target.closest('[data-lookup-apply]')){
      const dialog=target.closest('dialog'),grid=dialog.closest('[data-add-choice]'),rule=JSON.parse(grid.dataset.addChoice),key=dialog.querySelector('[data-lookup-choice]:checked')?.value;
      try{if(parentOf(grid,rule)!==dialog.dataset.parentKey)throw new Error('상위 대상이 변경되었습니다. 다시 조회하세요.');const result=resolveMockupLookupAdd(rule,dialog.dataset.parentKey,key,choiceValues(grid,rule));if(result.operation==='reactivate'){const row=rowsOfChoice(grid).find(row=>rowValues(row)[rule.fixed]===dialog.dataset.parentKey&&rowValues(row)[rule.key]===key);setCell(row.querySelector(`[data-row-field="${rule.active}"]`),'true');markEdited(row);}else appendRow(grid,result.values);dialog.close();}catch(error){dialog.querySelector('[data-lookup-status]').textContent=error.message;}return;
    }
    const add = target.closest('[data-add-demo-row],[data-grid-add]:not(:disabled)');
    if (add) {
      const grid = add.closest('[data-grid-view]');
      let seed;
      if(grid.dataset.addChoice){
        const rule=JSON.parse(grid.dataset.addChoice),parent=parentOf(grid,rule);
        if(!parent){grid.querySelector('.pg').textContent='먼저 상위 대상을 선택하세요.';return;}
        if(rule.mode==='조회 팝업'){const dialog=grid.querySelector('[data-lookup-add]');dialog.dataset.parentKey=parent;dialog.querySelectorAll('[data-lookup-filter]').forEach(input=>input.value='');dialog.querySelector('[data-lookup-results]').replaceChildren();dialog.querySelector('[data-lookup-status]').textContent='조회 후 사용자를 선택하세요.';dialog.querySelector('[data-lookup-apply]').disabled=true;dialog.showModal();return;}
        if(rule.state!=='정상'){grid.querySelector('.pg').textContent='그룹 기준정보 조회 실패 — 기존 입력을 유지합니다.';return;}
        seed={...rule.defaults,[rule.fixed]:parent,[rule.key]:''};
      }
      if(grid.dataset.addReference){
        const rule=JSON.parse(grid.dataset.addReference);
        const input=[...grid.closest('.screen').querySelectorAll('.sf input,.sf select')].find(node=>node.getAttribute('aria-label')===rule.search);
        const existing=[...grid.querySelectorAll('tbody tr[data-grid-row-state]')].map(row=>[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===rule.key)?.dataset.rowValue);
        try{seed=resolveMockupAddReference(rule,input?.value,existing);}
        catch(error){grid.querySelector('.pg').textContent='행 추가 거부 — '+error.message;return;}
      }
      appendRow(grid,seed);
      return;
    }
    const close = target.closest('[data-close-editor]');
    if (close) { close.closest('[data-cell-editor]').open = false; return; }
    const tab = target.closest('[data-detail-tab],[data-open-tab]');
    if (tab) {
      event.preventDefault();
      const screen=tab.closest('.screen'), name=tab.dataset.detailTab || tab.dataset.openTab;
      if(!screen.matches('[data-interactive-tabs]')){
        const area=[...root.querySelectorAll('[data-walkthrough-area]')].find(e=>e.dataset.walkthroughArea===name);
        if(area){activate(area.querySelector('.screen'),name,tab.dataset.context);reveal(area);}
      }else activate(screen, name, tab.dataset.context);
      return;
    }
    const reset = target.closest('[data-clear-context]');
    if (reset) { activate(reset.closest('.screen'), reset.closest('[data-detail-panel]').dataset.detailPanel, ''); return; }
    const collapse = target.closest('[data-toggle-master]');
    if (collapse) {
      const pane = collapse.closest('.screen').querySelector('[data-pane="master"]');
      pane.hidden = !pane.hidden;
      collapse.textContent = pane.hidden ? '테이블 목록 펼치기' : '테이블 목록 접기';
      return;
    }
    const apply = target.closest('[data-apply-cell]');
    if (apply) {
      const editor = apply.closest('[data-cell-editor]');
      const values = [...editor.querySelectorAll('[data-edit-value]')].flatMap(e => e.multiple ? [...e.selectedOptions].map(o=>o.value) : [e.value]).filter(Boolean);
      if(!validateChoice(editor,values[0]))return;
      const labels=[...editor.querySelectorAll('[data-edit-value]')].flatMap(e=>e.tagName==='SELECT'?[...e.selectedOptions].filter(o=>o.value).map(o=>o.textContent):[e.value]).filter(Boolean);
      editor.querySelector('summary').textContent = (labels.join(' / ') || '미지정') + ' ✎';
      const cell=editor.closest('[data-row-field]');
      if(cell){
        const previous=cell.dataset.rowValue;
        cell.dataset.rowValue=values.join(' / ');
        if(previous!==cell.dataset.rowValue){
          const row=cell.closest('tr');
          row?.querySelectorAll('[data-parent-field]').forEach(box=>{
            if(box.dataset.parentField!==cell.dataset.rowField || box.closest('[data-row-field]')===cell)return;
            const child=box.closest('[data-row-field]');
            const candidates=JSON.parse(box.dataset.candidateMap||'{}')[cell.dataset.rowValue]||[];
            const references=JSON.parse(box.dataset.referenceMap||'{}');
            const select=box.querySelector('select');
            select.replaceChildren();
            ['',...candidates].forEach(label=>{const option=doc.createElement('option');option.value=String(references[label]??label);option.textContent=label||'미지정';select.append(option);});
            select.value='';
            box.querySelector('[data-combo-filter]').value='';
            box.querySelector('[data-combo-value]').textContent='현재 선택: 미지정';
            box.querySelector('[data-combo-empty]').hidden=candidates.length!==0;
            child.dataset.rowValue='';
            child.querySelector('summary').textContent='미선택 ✎';
            child.title='상위 값 변경 — 지원값을 다시 선택하세요';
          });
        }
      }
      editor.open = false;
      refreshRowConditions(editor.closest('tr'));
      markEdited(editor.closest('tr'));
    }
    const selectedRow=target.closest('tbody tr[data-row-context]');
    if(selectedRow&&selectedRow.closest('[data-grid-view]')?.dataset.gridSelection!=='multiple'){
      selectedRow.parentElement.querySelectorAll('tr.sel').forEach(row=>row.classList.remove('sel'));
      selectedRow.classList.add('sel');
    }
  });
  root.addEventListener('dblclick',event=>{
    if(event.target.closest('input,select,textarea,button,[data-note]'))return;
    const editor=event.target.closest('td')?.querySelector('[data-cell-inline],[data-cell-popup],[data-cell-large-view]');
    if(editor){event.preventDefault();openCell(editor);}
  });
  root.addEventListener('focusout',event=>{
    const editor=event.target.closest('[data-cell-inline],[data-cell-large-view]');
    if(editor&&!editor.contains(event.relatedTarget))finishInline(editor);
  });
  root.addEventListener('submit', event => {
    if (!event.target.matches('[data-command-form]')) return;
    event.preventDefault();
    event.target.querySelector('[data-command-result]').textContent='목업 입력 확인 완료 — 서버로 접수하거나 업무 데이터를 변경하지 않았습니다.';
  });
  root.querySelectorAll('[data-command-targets]').forEach(group=>{
    if(group.children.length===1)group.querySelector('[data-remove-command-target]').disabled=true;
  });
  root.addEventListener('keydown', event=>{
    if((event.key==='Enter'||event.key===' ')&&event.target.matches('[data-note]')){event.preventDefault();event.target.click();}
    const editor=event.target.closest('[data-cell-inline],[data-cell-popup],[data-cell-large-view]');
    if(!editor)return;
    if(event.target.matches('summary')&&['Enter','F2',' '].includes(event.key)){event.preventDefault();openCell(editor);}
    else if((editor.hasAttribute('data-cell-inline')||editor.hasAttribute('data-cell-large-view'))&&['Enter','Escape'].includes(event.key)&&(event.key!=='Enter'||!editor.hasAttribute('data-cell-large')||event.ctrlKey||event.metaKey)){event.preventDefault();finishInline(editor,event.key==='Escape');editor.querySelector('summary').focus();}
  });
  if(!root.host){
    const openHash=()=>{const destination=root.getElementById(location.hash.slice(1));if(destination)reveal(destination);};
    window.addEventListener('hashchange',openHash);if(location.hash)openHash();
  }
  root.addEventListener('input', event => {
    if(event.target.matches('[data-edit-value]')&&!event.target.closest('[data-cell-editor]')){
      const cell=event.target.closest('[data-row-field]');if(cell){cell.dataset.rowValue=event.target.value;markEdited(cell.closest('tr'));}
    }
    if (!event.target.matches('[data-combo-filter]') || event.target.readOnly) return;
    const box = event.target.closest('[data-search-combo]');
    const query = event.target.value.toLowerCase();
    let matches = 0;
    [...box.querySelector('select').options].forEach(o => {
      o.hidden = o.value !== '' && !o.textContent.toLowerCase().includes(query);
      if (o.value && !o.hidden && !o.disabled) matches++;
    });
    box.querySelector('[data-combo-empty]').hidden = matches !== 0;
  });
  root.addEventListener('change', event => {
    const input = event.target;
    if(input.matches('[data-grid-select-all],[data-grid-select-row]')){
      const grid=input.closest('[data-grid-view]');
      if(input.matches('[data-grid-select-all]'))grid.querySelectorAll('tbody tr[data-grid-row-state]').forEach(row=>{
        if(row.hidden)return;
        const select=row.querySelector('[data-grid-select-row]');if(select&&!select.disabled)select.checked=input.checked;
      });
      refreshGrid(grid);return;
    }
    if (input.matches('[data-grid-column]')) {
      input.closest('[data-grid-view]').querySelectorAll('[data-grid-col]').forEach(c => {
        if (c.dataset.gridCol === input.dataset.gridColumn) c.hidden = !input.checked;
      });
      refreshGrid(input.closest('[data-grid-view]'));
    }
    if(input.matches('[data-lookup-choice]')){input.closest('dialog').querySelector('[data-lookup-apply]').disabled=!input.checked;return;}
    if (!input.matches('[data-combo-select]') || input.disabled) return;
    input.closest('[data-search-combo]').querySelector('[data-combo-value]').textContent = '현재 선택: ' + ([...input.selectedOptions].filter(o=>o.value).map(o=>o.textContent).join(', ') || '미지정');
    const editor = input.closest('[data-cell-editor]');
    if (!editor) return;
    editor.querySelectorAll('[data-parent-field]').forEach(box => {
      if (box.dataset.parentField !== input.dataset.fieldName) return;
      const values = JSON.parse(box.dataset.candidateMap || '{}')[input.value] || [];
      const select = box.querySelector('select');
      select.replaceChildren();
      ['', ...values].forEach(value => {
        const option = document.createElement('option');
        option.value = value; option.textContent = value || '미지정'; select.append(option);
      });
      select.value = '';
      box.querySelector('[data-combo-filter]').value = '';
      box.querySelector('[data-combo-value]').textContent = '현재 선택: 미지정';
      box.querySelector('[data-combo-empty]').hidden = values.length !== 0;
    });
  });
  if(typeof bindMockupMath==='function')bindMockupMath(root);
}
