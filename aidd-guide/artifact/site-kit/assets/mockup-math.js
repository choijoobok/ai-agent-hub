/** 요건서에 선언한 목업 계산만 시연한다. 서버 저장·금액 확정은 수행하지 않는다. */
function computeMockupGrid(rules, source) {
  const grids=Object.fromEntries(Object.entries(source).map(([name,rows])=>[name,rows.map(row=>({...row}))]));
  const number=value=>{
    if(value===undefined||value===null||String(value).trim()===''||!Number.isFinite(Number(value))||Number(value)<0)throw new Error('수량·단가의 미입력/미상·음수·비유한 값을 보완하세요.');
    return Number(value);
  };
  const sum=reference=>{
    const [name,field]=reference.split('::');
    if(!name||!field||!grids[name]?.length)throw new Error('산정 대상과 입력 항목을 확인하세요.');
    return grids[name].reduce((total,row)=>total+number(row[field]),0);
  };
  for(const rule of rules){
    const rows=grids[rule.area];if(!rows?.length)throw new Error('산정 결과를 표시할 대상이 없습니다.');
    const scale=number(rule.scale);
    for(const row of rows){
      let value;
      if(rule.kind==='곱셈')value=number(row[rule.a])*number(row[rule.b])*scale;
      else if(rule.kind==='합계')value=sum(rule.a)*scale;
      else if(rule.kind==='비율'||rule.kind==='버림 비율'){
        const top=sum(rule.a),bottom=sum(rule.b);
        if(top<=0||bottom<=0)throw new Error('비용과 비중복 토큰 합계가 양수여야 단가를 산정할 수 있습니다.');
        value=top/bottom*scale;
        if(rule.kind==='버림 비율')value=Math.floor(value);
      }else throw new Error('지원하지 않는 목업 계산 선언입니다.');
      if(!Number.isFinite(value))throw new Error('계산 결과를 확인하세요.');
      row[rule.target]=value;
    }
  }
  return grids;
}

/** 조회조건으로 고른 정적 참조만 사용한다. 실제 제품 조회·저장 계약이 아니다. */
function resolveMockupAddReference(rule, key, existing) {
  key=String(key??'').trim();
  if(!key)throw new Error(rule.search+'을 입력한 뒤 행 추가하세요.');
  if(existing.includes(key))throw new Error('같은 '+rule.key+'의 행이 이미 있습니다.');
  const row=rule.values[key];
  if(!row||row[rule.key]!==key)throw new Error('해당 '+rule.search+'의 저장된 유효 단가가 없습니다. 기준 단가를 먼저 저장하세요.');
  const rate=Number(row[rule.rate]);
  if(row[rule.rate]===null||String(row[rule.rate]??'').trim()===''||!Number.isFinite(rate)||rate<=0)throw new Error('참조 단가가 미완료입니다. 확인 불가를0으로 대체할 수 없습니다.');
  return {...row};
}

/** 그룹을 왕복해도 같은 하단 그리드의 선택 행을 원래 문맥으로 복원한다. */
function rememberMockupDetailSelection(state, key, current, visible) {
  if(state.key&&current)state.selected.set(state.key,current);
  state.key=key;
  const selected=state.selected.get(key);
  if(selected&&!visible.includes(selected)){state.selected.delete(key);return null;}
  return selected??null;
}

function bindMockupMath(root) {
  const screens=[...root.querySelectorAll('[data-master-link]')];
  if(!screens.length)return;
  const rowsOf=grid=>[...grid.querySelector('.grid > table > tbody')?.children||[]].filter(row=>row.hasAttribute('data-row-context'));
  const allocationInputs=new WeakMap();
  const detailSelections=new WeakMap();
  const controls=screen=>[...screen.querySelectorAll('button[data-grid-save],button[data-grid-add],button[data-edit-action],button[data-config-switch]')];
  const refresh=screen=>{
    const master=screen.querySelector('[data-grid-role="master"]'),selected=master?.querySelector('tbody tr.sel');
    if(!selected)return;
    const key=selected.dataset.masterClientKey||selected.querySelector(`[data-row-field="${screen.dataset.masterLink}"]`)?.dataset.rowValue;
    const locked=selected.dataset.masterLocked==='true';
    if(screen.dataset.allocationValidity){
      const rule=JSON.parse(screen.dataset.allocationValidity),cell=name=>[...selected.querySelectorAll('[data-row-field]')].find(node=>node.dataset.rowField===name);
      if(!allocationInputs.has(selected))allocationInputs.set(selected,{values:rule.inputs.map(name=>cell(name)?.dataset.rowValue),flag:cell(rule.field)?.dataset.rowValue});
      const initial=allocationInputs.get(selected),same=rule.inputs.every((name,index)=>cell(name)?.dataset.rowValue===initial.values[index]),field=cell(rule.field);
      const value=same?initial.flag:'false';
      if(field&&['true','false','Y','N','1','0'].includes(String(initial.flag))){field.dataset.rowValue=value;const toggle=field.querySelector('[data-grid-switch]');if(toggle)toggle.setAttribute('aria-checked',String(['true','Y','1'].includes(value)));}
    }
    screen.dataset.activeMaster=key||'';
    for(const grid of screen.querySelectorAll('[data-grid-role="detail"]')){
      const rows=rowsOf(grid);
      const current=rows.find(row=>row.classList.contains('sel')&&!row.hidden);
      if(!detailSelections.has(grid))detailSelections.set(grid,{key,selected:new Map()});
      for(const row of rows){
        if(row.dataset.gridRowState==='C'&&!row.dataset.masterAssigned){row.dataset.rowContext=JSON.stringify([key]);row.dataset.masterAssigned='true';}
        row.hidden=!JSON.parse(row.dataset.rowContext).includes(key);
      }
      const selected=rememberMockupDetailSelection(detailSelections.get(grid),key,current,rows.filter(row=>!row.hidden));
      for(const row of rows)row.classList.toggle('sel',row===selected);
      const shown=rows.filter(row=>!row.hidden).length;
      const count=grid.querySelector('.cnt');if(count)count.textContent=shown+'건';
      const footer=grid.querySelector('.pg');if(footer)footer.textContent=`${shown}건 · 선택 대상의 입력 유지`;
      rows.filter(row=>!row.hidden).forEach((row,index)=>{const n=row.querySelector('[data-grid-row-number]');if(n)n.textContent=String(index+1);});
    }
    const dirty=[selected,...screen.querySelectorAll('[data-grid-role="detail"] tr[data-grid-row-state]:not([hidden])')].some(row=>row.dataset.gridRowState);
    for(const button of controls(screen))button.disabled=button.dataset.baseDisabled==='true'||(locked&&!button.hasAttribute('data-selection-independent'))||(button.hasAttribute('data-config-switch')&&!button.closest('[data-row-field]').classList.contains('edit'))||(button.hasAttribute('data-saved-action')&&dirty);
    const status=screen.querySelector('[data-calculation-status]');if(status){status.hidden=true;status.textContent='';}
    if(locked||screen.hasAttribute('data-calculation-readonly')||!screen.dataset.calculationRules)return;
    const rules=JSON.parse(screen.dataset.calculationRules),grids={},elements={};
    for(const grid of screen.querySelectorAll('[data-grid-view]')){
      const rows=rowsOf(grid).filter(row=>!row.hidden&&row.dataset.gridRowState!=='D'&&(grid.dataset.gridRole!=='master'||row===selected));
      elements[grid.dataset.gridName]=rows;
      grids[grid.dataset.gridName]=rows.map(row=>Object.fromEntries([...row.querySelectorAll('[data-row-field]')].map(cell=>[cell.dataset.rowField,cell.dataset.rowValue])));
    }
    try{
      const results=computeMockupGrid(rules,grids);
      for(const rule of rules)elements[rule.area].forEach((row,index)=>{
        const cell=[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===rule.target);
        const value=results[rule.area][index][rule.target];
        cell.dataset.rowValue=String(value);cell.textContent=value.toLocaleString('ko-KR',{maximumFractionDigits:6});
      });
    }catch(error){
      if(status){status.hidden=false;status.textContent='목업 산정 미완료 — '+error.message;}
      for(const rule of rules)for(const row of elements[rule.area]||[]){const cell=[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===rule.target);if(cell){cell.textContent='산정 불가';cell.dataset.rowValue='';}}
      const guarded=JSON.parse(screen.dataset.calculationGuard||'[]');
      controls(screen).filter(button=>guarded.includes(button.textContent.trim())).forEach(button=>button.disabled=true);
    }
  };
  for(const screen of screens){
    controls(screen).forEach(button=>button.dataset.baseDisabled=String(button.hasAttribute('data-config-switch')?button.dataset.switchReadonly==='true':button.disabled));
    const initial=screen.dataset.masterInitial;
    if(initial)for(const row of screen.querySelectorAll('[data-grid-role="master"] tbody tr[data-row-context]'))row.classList.toggle('sel',row.querySelector(`[data-row-field="${screen.dataset.masterLink}"]`)?.dataset.rowValue===initial);
    refresh(screen);
  }
  root.addEventListener('click',event=>{
    const screen=event.target.closest('[data-master-link]');if(!screen)return;
    const row=event.target.closest('[data-grid-role="master"] tbody tr[data-row-context]');
    if(row){row.parentElement.querySelectorAll('tr.sel').forEach(node=>node.classList.remove('sel'));row.classList.add('sel');}
    queueMicrotask(()=>refresh(screen));
  });
  for(const name of ['keydown','focusout','change'])root.addEventListener(name,event=>{const screen=event.target.closest('[data-master-link]');if(screen)queueMicrotask(()=>refresh(screen));});
}
