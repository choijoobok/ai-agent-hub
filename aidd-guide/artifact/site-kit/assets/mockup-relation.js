/* 관계 작업대의 로컬 시연. 실제 조회·저장·인가 성공으로 해석하지 않는다. */
function resolveMockupRelationTables(source,left,right,owner='',saved=false) {
  if(!source||!left||!right)throw new Error('데이터소스와 양쪽 테이블을 선택하세요.');
  if([left,right].some(row=>!row.catalogId||row.dataSourceKey!==source||row.selectable===false))throw new Error('선택한 데이터소스의 현재 허용 카탈로그만 선택할 수 있습니다.');
  if(saved&&(!owner||![left.catalogId,right.catalogId].includes(owner)))throw new Error('기존 관계의 저장 소유 카탈로그는 보존해야 합니다.');
  return {dataSourceKey:source,leftCatalogId:left.catalogId,rightCatalogId:right.catalogId,ownerCatalogId:saved?owner:left.catalogId};
}
function resolveMockupRelationPair(relation,left,right,pairs) {
  if(!relation?.leftCatalogId||!relation.rightCatalogId||!left||!right)throw new Error('양쪽 테이블과 컬럼을 먼저 선택하세요.');
  if(!left.columnId||!right.columnId||left.catalogId!==relation.leftCatalogId||right.catalogId!==relation.rightCatalogId||left.selectable===false||right.selectable===false)throw new Error('선택한 테이블의 현재 허용 컬럼만 연결할 수 있습니다.');
  if(!left.typeGroup||left.typeGroup!==right.typeGroup)throw new Error('컬럼 타입의 호환성을 확인하세요.');
  if(pairs.some(pair=>pair.leftColumnId===left.columnId&&pair.rightColumnId===right.columnId))throw new Error('이미 추가한 컬럼 쌍입니다.');
  return {leftColumnId:left.columnId,rightColumnId:right.columnId,order:Math.max(0,...pairs.map(pair=>Number(pair.order)||0))+1};
}
function resolveMockupRelationOrder(pairs) {
  const values=pairs.map(pair=>Number(pair.order));
  if(values.some(value=>!Number.isSafeInteger(value)||value<=0)||new Set(values).size!==values.length)throw new Error('순서는 관계 내에서 중복 없는 양의 정수여야 합니다.');
  return pairs.map((pair,index)=>({...pair,order:values[index]})).sort((a,b)=>a.order-b.order);
}
function resolveMockupRelatedKnowledge(item,leftCatalogId,rightCatalogId) {
  if(!leftCatalogId||!rightCatalogId||item.selectable===false)return false;
  const ids=new Set((item.direct||[]).filter(link=>link.active===true).map(link=>link.catalogId));
  for(const example of item.examples||[])if(example.active===true&&example.complete===true)for(const id of example.catalogIds||[])ids.add(id);
  return ids.has(leftCatalogId)&&ids.has(rightCatalogId);
}
function bindRelationWorkspace(root,{rowValues,setCell,markEdited,appendRow,refreshGrid}) {
  const node=root.ownerDocument||root;
  for(const screen of root.querySelectorAll('[data-relation-workspace]')){
    const rule=JSON.parse(screen.dataset.relationWorkspace),grid=name=>[...screen.querySelectorAll('[data-grid-name]')].find(item=>item.dataset.gridName===name);
    const master=grid(rule.master),dialog=screen.querySelector('[data-relation-picker]');if(!master||!dialog||![rule.leftColumns,rule.pairs,rule.rightColumns,rule.knowledge].every(name=>grid(name)))continue;
    const record=row=>{try{return JSON.parse(row?.dataset.relationRecord||'{}');}catch{return{};}};
    const rows=name=>[...grid(name).querySelectorAll('tbody tr[data-grid-row-state]')];
    const active=()=>master.querySelector('tbody tr.sel:not([data-grid-row-state="D"])');
    const relation=()=>record(active()),idOf=row=>record(row).clientRowKey||rowValues(row)['관계 식별자'];
    const state=()=>active()&&active().dataset.masterLocked!=='true'&&screen.dataset.relationEditable==='true';
    const currentPairs=()=>rows(rule.pairs).filter(row=>record(row).relationId===idOf(active())&&row.dataset.gridRowState!=='D');
    const pairValues=()=>currentPairs().map(row=>({...record(row),order:rowValues(row)['순서']}));
    const notice=message=>{grid(rule.pairs).querySelector('.pg').textContent=message;};
    const refresh=name=>{const count=rows(name).filter(row=>!row.hidden).length;refreshGrid(grid(name));grid(name).querySelector('.cnt').textContent=count+'건';grid(name).querySelector('.pg').textContent='총 '+count+'건 · 1 페이지';};
    let sequence=0,lastContext='';const beforePairs=new WeakMap();
    const update=()=>{
      const selected=active(),data=relation(),id=selected?idOf(selected):'';
      const context=[id,data.leftCatalogId,data.rightCatalogId].join('|');
      for(const[name,catalogId]of[[rule.leftColumns,data.leftCatalogId],[rule.rightColumns,data.rightCatalogId]])for(const row of rows(name)){row.hidden=!catalogId||record(row).catalogId!==catalogId;if(context!==lastContext)row.classList.remove('sel');}
      for(const row of rows(rule.pairs))row.hidden=!id||record(row).relationId!==id;
      for(const row of rows(rule.knowledge))row.hidden=!resolveMockupRelatedKnowledge(record(row),data.leftCatalogId,data.rightCatalogId);
      for(const action of screen.querySelectorAll('[data-relation-open]')){action.dataset.relationAllowed??=String(!action.disabled);action.disabled=action.dataset.relationAllowed!=='true'||!state()||!rowValues(selected)[rule.source];}
      for(const name of[rule.leftColumns,rule.pairs,rule.rightColumns,rule.knowledge])refresh(name);
      lastContext=context;
    };
    const filteredTables=()=>{
      for(const[index,name]of[rule.leftTables,rule.rightTables].entries()){
        const keyword=dialog.querySelector('[data-relation-filter="'+index+'"]').value.trim().toLowerCase();
        for(const row of rows(name)){const data=record(row);row.hidden=data.dataSourceKey!==dialog.dataset.source||!Object.values(rowValues(row)).join(' ').toLowerCase().includes(keyword)||data.selectable===false;if(row.hidden)row.classList.remove('sel');}
        refresh(name);
      }
      dialog.querySelector('[data-relation-apply]').disabled=![rule.leftTables,rule.rightTables].every(name=>grid(name).querySelector('tbody tr.sel:not([hidden])'));
    };
    screen.addEventListener('click',event=>{
      const target=event.target.closest('button');if(!target||target.disabled)return;
      const table=target.closest('[data-grid-name]');
      if(target.matches('[data-grid-add]')&&table===master){
        event.preventDefault();event.stopImmediatePropagation();const id='REL-NEW-'+(++sequence),row=appendRow(master,{'관계 식별자':'',[rule.source]:'',[rule.left]:'',[rule.right]:''});
        if(row){master.querySelectorAll('tbody tr.sel').forEach(item=>item.classList.remove('sel'));row.classList.add('sel');row.dataset.relationRecord=JSON.stringify({clientRowKey:id});update();}return;
      }
      if(target.matches('[data-relation-open]')){
        event.preventDefault();event.stopImmediatePropagation();if(!state())return;
        dialog.dataset.parent=idOf(active());dialog.dataset.source=rowValues(active())[rule.source];dialog.querySelector('[data-relation-source]').textContent='데이터소스: '+dialog.dataset.source;
        dialog.querySelectorAll('tr.sel').forEach(row=>row.classList.remove('sel'));dialog.querySelectorAll('[data-relation-filter]').forEach(input=>input.value='');
        dialog.querySelector('[data-relation-reset]').checked=false;dialog.querySelector('[data-relation-reset-label]').hidden=currentPairs().length===0;
        dialog.querySelector('[data-relation-status]').textContent='양쪽 테이블을 선택하세요. 취소하면 기존 입력을 보존합니다.';filteredTables();dialog.showModal();return;
      }
      if(target.matches('[data-relation-close]')){event.preventDefault();event.stopImmediatePropagation();dialog.close();return;}
      if(target.matches('[data-relation-apply]')){
        event.preventDefault();event.stopImmediatePropagation();
        try{
          if(!state()||idOf(active())!==dialog.dataset.parent||rowValues(active())[rule.source]!==dialog.dataset.source)throw new Error('상위 관계/데이터소스가 변경되었습니다. 다시 선택하세요.');
          const left=record(grid(rule.leftTables).querySelector('tbody tr.sel:not([hidden])')),right=record(grid(rule.rightTables).querySelector('tbody tr.sel:not([hidden])'));
          const current=relation(),value=resolveMockupRelationTables(dialog.dataset.source,left,right,current.ownerCatalogId,active().dataset.gridRowState!=='C');
          const changed=current.leftCatalogId!==value.leftCatalogId||current.rightCatalogId!==value.rightCatalogId;
          if(changed&&currentPairs().length&&!dialog.querySelector('[data-relation-reset]').checked)throw new Error('기존 컬럼 매핑 초기화에 동의하세요.');
          if(changed){
            if(!beforePairs.has(active()))beforePairs.set(active(),rows(rule.pairs).filter(row=>record(row).relationId===idOf(active())));
            for(const row of rows(rule.pairs).filter(row=>record(row).relationId===idOf(active())))row.remove();
            const review=active().querySelector('[data-row-field="검수 상태"]');if(review)setCell(review,'UNKNOWN');
          }
          active().dataset.relationRecord=JSON.stringify({...current,...value});
          for(const[field,text]of[[rule.left,left['객체명']],[rule.right,right['객체명']]])setCell(active().querySelector('[data-row-field="'+field+'"]'),text);
          markEdited(active());dialog.close();update();
        }catch(error){dialog.querySelector('[data-relation-status]').textContent=error.message;}return;
      }
      if(target.matches('[data-grid-add]')&&table===grid(rule.pairs)){
        event.preventDefault();event.stopImmediatePropagation();if(!state())return;
        try{
          if(rowValues(active())[rule.source]!==relation().dataSourceKey)throw new Error('변경한 데이터소스의 테이블을 다시 선택하세요.');
          const left=record(grid(rule.leftColumns).querySelector('tbody tr.sel:not([hidden])')),right=record(grid(rule.rightColumns).querySelector('tbody tr.sel:not([hidden])'));
          const value=resolveMockupRelationPair(relation(),left,right,pairValues()),row=appendRow(grid(rule.pairs),{'순서':String(value.order),'왼쪽 컬럼':left['컬럼명'],'오른쪽 컬럼':right['컬럼명']});
          row.dataset.relationRecord=JSON.stringify({...value,relationId:idOf(active())});update();notice('선택한 두 컬럼을 편집본에 추가했습니다. 저장 전입니다.');
        }catch(error){notice(error.message);}return;
      }
      if(target.matches('[data-grid-save]')){
        try{if(!state()||!relation().leftCatalogId||!relation().rightCatalogId||rowValues(active())[rule.source]!==relation().dataSourceKey)throw new Error('데이터소스와 양쪽 테이블을 확인하세요.');resolveMockupRelationOrder(pairValues());if(currentPairs().length===0)throw new Error('컬럼 쌍을 한 개 이상 추가하세요.');}
        catch(error){event.preventDefault();event.stopImmediatePropagation();notice(error.message);return;}
      }
    },true);
    screen.addEventListener('input',event=>{if(event.target.matches('[data-relation-filter]'))filteredTables();});
    root.addEventListener('click',event=>{
      const path=event.composedPath();if(!path.includes(screen))return;
      const row=path.find(item=>item.matches?.('tbody tr')),revert=path.some(item=>item.matches?.('[data-revert-row]')),inPicker=path.includes(dialog);
      // 같은 루트의 공통 행 선택/원복 리스너 다음에 실행한다.
        if(revert&&row?.closest('[data-grid-name]')===master&&beforePairs.has(row)){
          for(const pair of rows(rule.pairs).filter(pair=>record(pair).relationId===idOf(row)))pair.remove();
          for(const pair of beforePairs.get(row))grid(rule.pairs).querySelector('tbody').append(pair);
          beforePairs.delete(row);
        }
        if(row){if(inPicker)filteredTables();else update();}
    });
    for(const name of['change','focusout'])root.addEventListener(name,event=>{if(event.composedPath().includes(screen))update();});
    update();
  }
}
