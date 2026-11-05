/** SQL별 편집본을 유지하는 목업. SQL 파싱·원천 조회·저장을 수행하지 않는다. */
function bindQueryExampleMockup(root,api){
  let temporary=0;
  for(const board of root.querySelectorAll('[data-query-example-board]')){
    const screen=board.closest('.screen'),list=board.querySelector('[data-grid-view]'),editor=board.querySelector('[data-query-editor]'),status=board.querySelector('[data-query-status]');
    const fields=[...editor.querySelectorAll('[data-form-value]')],initial=new Map(),known=new WeakSet();let selected=null;
    const rows=()=>[...list.querySelectorAll('tbody tr[data-grid-row-state]')];
    const record=row=>JSON.parse(row?.dataset.queryRecord||'{}');
    const value=(row,key)=>row?.querySelector(`[data-row-field="${key}"]`)?.dataset.rowValue||'';
    const live=row=>row?.isConnected&&row.dataset.gridRowState!=='D';
    const dataSource=()=>screen.querySelector('[data-grid-role="master"] [data-row-field="실행 데이터소스"]')?.dataset.rowValue;
    const sourceAtLoad=dataSource();
    for(const row of rows()){initial.set(row,record(row));known.add(row);}
    const stale=row=>dataSource()!==sourceAtLoad||!initial.has(row)||record(row).SQL!==initial.get(row).SQL;
    function show(){
      const current=rows();
      for(const row of current)if(!known.has(row)){
        known.add(row);
        row.dataset.queryRecord=JSON.stringify({sqlExampleId:null,clientRowKey:'NEW-SQL-'+(++temporary),'상세 설명':'',SQL:''});
        api.setCell(row.querySelector('[data-row-field="순번"]'),Math.max(0,...current.filter(r=>r!==row).map(r=>Number(value(r,'순번'))||0))+1);
        selected=row;
      }
      if(!live(selected))selected=current.find(live)||null;
      for(const row of current)row.classList.toggle('sel',row===selected);
      for(const field of fields){field.value=selected?(record(selected)[field.dataset.formValue]||''):'';field.readOnly=board.dataset.queryEditable!=='true'||!live(selected);field.disabled=!selected;}
      const label=selected?`${value(selected,'순번')} · ${value(selected,'간단한 제목')||'제목 미입력'}`:'선택한 SQL 없음';
      status.textContent=selected?`선택 SQL: ${label}. ${stale(selected)?'SQL 또는 실행 데이터소스 변경 — 참조 재추출·서버 검증이 필요합니다.':'설명과 SQL은 상단 전체 저장에 포함됩니다.'}`:'추가 버튼으로 SQL 예제를 작성하세요.';
      for(const name of ['참조 카탈로그','관계 참고']){
        const grid=[...screen.querySelectorAll('[data-grid-view]')].find(g=>g.dataset.gridName===name);if(!grid)continue;
        const panel=grid.closest('[data-detail-panel]');let note=panel.querySelector('[data-query-context]');
        if(!note){note=(root.ownerDocument||root).createElement('p');note.setAttribute('data-query-context','');note.className='hint';panel.prepend(note);}
        note.textContent=`선택 SQL: ${label}${selected&&stale(selected)?' — 재추출 전 이전 참조는 사용하지 않습니다.':''}`;
        for(const row of grid.querySelectorAll('tbody tr[data-grid-row-state]'))row.hidden=!selected||stale(selected)||record(row).sqlExampleId!==record(selected).sqlExampleId;
        const visible=[...grid.querySelectorAll('tbody tr[data-grid-row-state]')].filter(r=>!r.hidden).length;
        grid.querySelector('.cnt').textContent=`${visible}건`;
      }
    }
    fields.forEach(field=>field.addEventListener('input',()=>{
      if(field.readOnly||field.disabled||!live(selected))return;
      const r=record(selected);r[field.dataset.formValue]=field.value;selected.dataset.queryRecord=JSON.stringify(r);api.markEdited(selected);
      if(field.dataset.formValue==='SQL')status.textContent='SQL 변경 — 참조 재추출·서버 검증이 필요합니다. 현재 입력은 보존됩니다.';
    }));
    root.addEventListener('click',event=>{
      if(!board.contains(event.target))return;
      const row=event.target.closest('tbody tr[data-grid-row-state]');if(row&&list.contains(row)&&live(row))selected=row;
      show();
    });
    root.addEventListener('change',event=>{if(board.contains(event.target))show();});
    board.addEventListener('mockup:row-restored',()=>queueMicrotask(show));
    screen.addEventListener('click',event=>{
      if(event.target.closest('[data-detail-tab],[data-open-tab]'))queueMicrotask(show);
      const action=event.target.closest('[data-demo-action]');
      if(action?.dataset.demoAction==='참조 추출 및 검증')queueMicrotask(()=>{status.textContent='참조 추출 및 검증은 구현 후 서버에서 수행합니다. 목업에서는 현재 입력을 유지하며 검증 성공으로 바꾸지 않습니다.';});
    });
    screen.addEventListener('click',event=>{
      if(!event.target.closest('[data-open-command="저장"]'))return;
      const active=rows().filter(live),order=active.map(r=>Number(value(r,'순번')));
      const invalid=active.find(r=>!value(r,'간단한 제목').trim()||!record(r)['상세 설명']?.trim()||!record(r).SQL?.trim());
      if(!active.length||invalid||order.some(n=>!Number.isInteger(n)||n<1)||new Set(order).size!==order.length){event.preventDefault();event.stopPropagation();status.textContent='하나 이상의 SQL에 서로 다른 양의 정수 순번·제목·상세 설명·SQL을 입력하세요.';if(invalid)selected=invalid;}
    },true);
    const split=board.querySelector('[data-query-split]'),handle=board.querySelector('[data-query-splitter]');let pointer=null;
    const resize=n=>{const width=Math.max(25,Math.min(65,n));board.querySelector('[data-query-list]').style.flex=`${width} 1 0`;editor.style.flex=`${100-width} 1 0`;handle.setAttribute('aria-valuenow',String(Math.round(width)));};
    handle.addEventListener('pointerdown',e=>{if(e.button!==0)return;e.preventDefault();pointer=e.pointerId;handle.setPointerCapture(pointer);});
    handle.addEventListener('pointermove',e=>{if(e.pointerId!==pointer)return;const box=split.getBoundingClientRect();resize((e.clientX-box.left)/box.width*100);});
    for(const type of ['pointerup','pointercancel','lostpointercapture'])handle.addEventListener(type,()=>pointer=null);
    handle.addEventListener('keydown',e=>{if(!['ArrowLeft','ArrowRight'].includes(e.key))return;e.preventDefault();resize(Number(handle.getAttribute('aria-valuenow'))+(e.key==='ArrowLeft'?-5:5));});
    fields.find(field=>field.dataset.formValue==='SQL').style.fontFamily='monospace';
    show();
  }
}
