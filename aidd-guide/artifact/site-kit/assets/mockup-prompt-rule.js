/** 자연어 공통 규칙의 로컬 편집·Markdown 표시·검토 조합 시연. 실제 모델을 호출하지 않는다. */
function mockupPromptOrder(records,config){
  const id=record=>/^\d+$/.test(String(record[config.id]??''))?BigInt(record[config.id]):null;
  return records.map((record,index)=>({record,index})).sort((a,b)=>{
    const left=id(a.record),right=id(b.record);
    return left!==null&&right!==null?(left<right?-1:left>right?1:a.index-b.index):left!==null?-1:right!==null?1:(a.record.__clientOrder??a.index)-(b.record.__clientOrder??b.index);
  }).map(item=>item.record);
}
function mockupPromptEnabled(value){return ['true','Y','1','예','사용','켜짐'].includes(String(value));}
function mockupPromptReview(records,config){
  return mockupPromptOrder(records,config).filter(record=>record.__deleted!==true&&mockupPromptEnabled(record[config.enabled])).map(record=>`## ${String(record[config.title]??'').replace(/\s+/g,' ').trim()||'제목 미입력'}\n\n${String(record[config.body]??'')}`).join('\n\n---\n\n');
}
function mockupPromptIncomplete(records,config){return records.filter(record=>record.__deleted!==true&&mockupPromptEnabled(record[config.enabled])&&(!String(record[config.title]??'').trim()||!String(record[config.body]??'').trim()));}
function mockupPromptFingerprint(records,config){
  return JSON.stringify(mockupPromptOrder(records,config).map(record=>[record[config.id]??null,record.__clientOrder??null,record.__deleted===true,String(record[config.title]??''),String(record[config.enabled]??''),String(record[config.body]??'')]));
}
function bindPromptRuleMockup(root,api){
  const doc=root.ownerDocument||root;
  for(const board of root.querySelectorAll('[data-prompt-board]')){
    const config=JSON.parse(board.dataset.promptConfig),screen=board.closest('.screen'),grid=board.querySelector('[data-grid-view]'),form=board.querySelector('[data-prompt-form]');
    const fields=[...form.querySelectorAll('[data-form-value]')],status=board.querySelector('[data-prompt-status]'),reviewStatus=board.querySelector('[data-prompt-review-status]');
    const dialogs=Object.fromEntries([...screen.querySelectorAll('[data-prompt-dialog]')].map(dialog=>[dialog.dataset.promptDialog,dialog]));
    if(!grid||!dialogs.edit||!dialogs.preview||!dialogs.review)continue;
    const rows=()=>[...grid.querySelector('.grid > table > tbody').children].filter(row=>row.hasAttribute('data-grid-row-state'));
    const cell=(row,key)=>[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===key),value=(row,key)=>cell(row,key)?.dataset.rowValue??'';
    const record=row=>JSON.parse(row.dataset.promptRecord||'{}'),live=row=>row?.isConnected&&row.dataset.gridRowState!=='D';
    const editable=row=>board.dataset.promptEditable==='true'&&live(row)&&row.dataset.masterLocked!=='true';
    const known=new WeakSet(),clientOrder=new WeakMap();let sequence=0,selected=null,editing=null,snapshot=null,lastRequested=null,initialSelection=true;
    const all=()=>rows().map(row=>({...record(row),[config.id]:value(row,config.id),[config.title]:value(row,config.title),[config.enabled]:value(row,config.enabled),__clientOrder:clientOrder.get(row),__deleted:row.dataset.gridRowState==='D',__row:row}));
    const fingerprint=()=>mockupPromptFingerprint(all(),config);
    const persist=(row,key,text)=>{row.dataset.promptRecord=JSON.stringify({...record(row),[key]:text});api.markEdited(row);};
    for(const row of rows())known.add(row);
    function show(){
      for(const row of rows())if(!known.has(row)){
        known.add(row);clientOrder.set(row,++sequence);
        row.dataset.promptRecord=JSON.stringify({...record(row),[config.id]:null,[config.description]:'',[config.body]:'',clientRowKey:'LOCAL-PROMPT-'+sequence});
        api.setCell(cell(row,config.id),'');const toggle=cell(row,config.enabled)?.querySelector('[data-config-switch]');
        if(toggle&&!value(row,config.enabled))api.setCell(cell(row,config.enabled),toggle.dataset.switchOff||'false');
        selected=row;
      }
      const ordered=mockupPromptOrder(all(),config),current=rows();
      if(ordered.some((item,index)=>item.__row!==current[index]))for(const item of ordered)grid.querySelector('tbody').append(item.__row);
      if(initialSelection){selected=rows().find(row=>live(row)&&!row.hidden)||null;initialSelection=false;}
      if(!live(selected)||selected.hidden)selected=rows().find(row=>live(row)&&!row.hidden&&(row.classList.contains('sel')))||rows().find(row=>live(row)&&!row.hidden)||null;
      for(const row of rows())row.classList.toggle('sel',row===selected);
      for(const field of fields){const text=selected?String(record(selected)[field.dataset.formValue]??''):'';if(field.value!==text)field.value=text;field.disabled=!selected;field.readOnly=!editable(selected);field.rows=field.dataset.formValue===config.body?18:5;if(field.dataset.formValue===config.body)field.style.fontFamily='monospace';}
      const editButton=board.querySelector('[data-prompt-action="edit"]'),previewButton=board.querySelector('[data-prompt-action="preview"]');if(editButton)editButton.disabled=editButton.dataset.promptBaseDisabled==='true'||!editable(selected);if(previewButton)previewButton.disabled=previewButton.dataset.promptBaseDisabled==='true'||!selected;
      status.textContent=selected?'선택 규칙: '+(value(selected,config.title)||'제목 미입력')+' · 현재 입력은 해당 행의 편집본에 유지됩니다.':'규칙을 추가하거나 선택하세요.';
      const incomplete=mockupPromptIncomplete(all(),config).length;
      reviewStatus.textContent=incomplete?`사용 ON 규칙 ${incomplete}건의 제목 또는 본문이 비어 있습니다. 보완 전에는 전체 검토를 실행할 수 없습니다.`:lastRequested===null?'전체 규칙 검토는 보유 예시 전체의 사용 중인 공통 본문을 조합합니다. 실제 검토 호출은 구현 후 수행합니다.':fingerprint()!==lastRequested?'본문·제목·사용 여부 또는 추가·삭제가 바뀌어 이전 검토 요청이 낡았습니다. 다시 전체 규칙을 검토하세요.':'이전 검토 요청과 전송할 내용이 같습니다. 실제 호출·모순 판정은 수행하지 않았습니다.';
      api.refreshGrid(grid);
    }
    for(const button of board.querySelectorAll('[data-prompt-action]'))button.dataset.promptBaseDisabled=String(button.disabled);
    const schedule=()=>queueMicrotask(show),belongs=event=>screen.contains(event.target)||event.composedPath().includes(screen);
    for(const field of fields)field.addEventListener('input',()=>{if(!field.readOnly&&!field.disabled&&editable(selected)){persist(selected,field.dataset.formValue,field.value);schedule();}});
    const close=dialog=>{dialog.close();if(dialog===dialogs.edit)editing=null;};
    for(const dialog of Object.values(dialogs)){
      dialog.addEventListener('cancel',()=>{if(dialog===dialogs.edit)editing=null;});
      dialog.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();close(dialog);}});
    }
    dialogs.preview.addEventListener('click',event=>{if(event.target.closest('a'))event.preventDefault();});
    root.addEventListener('click',event=>{
      if(!belongs(event))return;
      const row=event.target.closest('tbody tr[data-grid-row-state]');if(row&&grid.contains(row)&&live(row))selected=row;
      const button=event.target.closest('[data-prompt-action]');if(button&&!button.disabled){
        const action=button.dataset.promptAction;
        if(action==='edit'&&editable(selected)){
          editing=selected;dialogs.edit.querySelector('[data-prompt-buffer]').value=record(selected)[config.body]||'';dialogs.edit.querySelector('[data-prompt-modal-status]').textContent='오른쪽 아래 모서리를 끌어 창 크기를 조절할 수 있습니다. 반영 전까지 선택 규칙의 본문을 바꾸지 않습니다.';dialogs.edit.querySelector('[data-prompt-action="apply"]').disabled=false;dialogs.edit.showModal();
        }else if(action==='apply'){
          if(editing===selected&&editable(editing)){persist(editing,config.body,dialogs.edit.querySelector('[data-prompt-buffer]').value);close(dialogs.edit);}else dialogs.edit.querySelector('[data-prompt-modal-status]').textContent='선택 또는 변경 권한이 바뀌었습니다. 기존 본문을 유지합니다.';
        }else if(action==='cancel'||action==='close')close(button.closest('dialog'));
        else if(action==='preview'&&selected){const preview=dialogs.preview.querySelector('[data-prompt-preview]');preview.replaceChildren(mockupRichTextPreview(record(selected)[config.body]||'','MARKDOWN',doc));dialogs.preview.showModal();}
        else if(action==='review'){
          snapshot={fingerprint:fingerprint(),body:mockupPromptReview(all(),config),incomplete:mockupPromptIncomplete(all(),config).length};dialogs.review.querySelector('[data-prompt-review-input]').value=snapshot.body;
          dialogs.review.querySelector('[data-prompt-review-result]').textContent=snapshot.incomplete?'사용 ON 규칙의 제목 또는 본문이 비어 있어 전체 검토를 실행할 수 없습니다.':snapshot.body?'실제 호출 전의 조합 예시입니다.':'사용 ON인 규칙이 없습니다.';
          dialogs.review.querySelector('[data-prompt-action="execute"]').disabled=board.dataset.promptEditable!=='true'||!snapshot.body||snapshot.incomplete>0;dialogs.review.showModal();
        }else if(action==='execute'&&board.dataset.promptEditable==='true'&&snapshot){
          const result=dialogs.review.querySelector('[data-prompt-review-result]');
          if(snapshot.incomplete||mockupPromptIncomplete(all(),config).length)result.textContent='사용 ON 규칙의 제목과 본문을 먼저 보완하세요.';
          else if(fingerprint()!==snapshot.fingerprint)result.textContent='현재 입력이 바뀌어 검토 요청이 낡았습니다. 창을 닫고 전체 규칙 검토를 다시 여세요.';
          else {lastRequested=snapshot.fingerprint;result.textContent='목업: 가공된 검토 요청, 실제 호출은 구현 후';}
        }
      }
      schedule();
    });
    for(const type of ['change','focusout','keydown','mockup:row-restored'])root.addEventListener(type,event=>{if(belongs(event))schedule();});
    const split=board.querySelector('[data-prompt-split]'),handle=board.querySelector('[data-prompt-splitter]');let pointer=null;
    const resize=number=>{const width=Math.max(25,Math.min(65,number));board.querySelector('[data-prompt-list]').style.flex=`${width} 1 0`;form.style.flex=`${100-width} 1 0`;handle.setAttribute('aria-valuenow',String(Math.round(width)));};
    handle.addEventListener('pointerdown',event=>{if(event.button!==0)return;event.preventDefault();pointer=event.pointerId;handle.setPointerCapture(pointer);});
    handle.addEventListener('pointermove',event=>{if(event.pointerId!==pointer)return;const rect=split.getBoundingClientRect();resize((event.clientX-rect.left)/rect.width*100);});
    for(const type of ['pointerup','pointercancel','lostpointercapture'])handle.addEventListener(type,()=>pointer=null);
    handle.addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();resize(Number(handle.getAttribute('aria-valuenow'))+(event.key==='ArrowLeft'?-5:5));});
    show();
  }
}
