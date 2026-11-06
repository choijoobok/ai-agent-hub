/** Portal 고정 구분별 콘텐츠의 로컬 편집본. 서버 저장·버전 발급·업무 조회는 수행하지 않는다. */
function mockupHelpBodyError(draft,doc){
  if(!draft.body.trim())return '공백이 아닌 본문을 입력하세요.';
  if(draft.format==='HTML'&&typeof DOMPurify==='undefined')return 'HTML 표시 검증 도구를 불러오지 못했습니다. 기존 원문을 유지합니다.';
  if(draft.format==='HTML'&&!mockupRichTextPreview(draft.body,'HTML',doc).textContent.trim())return '안전하지 않은 HTML을 제외한 뒤 표시할 본문이 없습니다.';
  return '';
}
function bindHelpMockup(root){
  const doc=root.ownerDocument||root;
  for(const board of root.querySelectorAll('[data-help-board]')){
    const screen=board.closest('.screen'),config=JSON.parse(board.dataset.helpConfig),editable=board.dataset.helpEditable==='true';
    const kind=board.querySelector('[data-help-kind]'),format=board.querySelector('[data-help-format]'),body=board.querySelector('[data-help-body]'),status=board.querySelector('[data-help-status]');
    const initial=new Map(config.types.map(type=>[type.code,{...type}])),drafts=new Map(config.types.map(type=>[type.code,{...type,localVersion:0}]));
    const dialogs=Object.fromEntries([...board.querySelectorAll('[data-help-dialog]')].map(dialog=>[dialog.dataset.helpDialog,dialog]));
    const buttons=[...board.querySelectorAll('[data-help-action]')];buttons.forEach(button=>button.dataset.helpBaseDisabled=String(button.disabled));
    let selected=board.dataset.helpInitial||config.types[0].code,selectionVersion=0,editing=null,pendingSave=null,message='구분별 원문과 작성 형식을 편집본에 유지합니다. 형식 변경은 원문을 변환하지 않습니다.';
    const current=()=>drafts.get(selected),belongs=event=>screen.contains(event.target)||event.composedPath().includes(screen);
    const saveDialog=()=>[...screen.querySelectorAll('[data-command]')].find(dialog=>dialog.dataset.command==='저장');
    const sameContext=snapshot=>snapshot&&snapshot.code===selected&&snapshot.selectionVersion===selectionVersion&&snapshot.localVersion===current().localVersion&&snapshot.body===current().body&&snapshot.format===current().format&&snapshot.revision===current().revision;
    const change=(field,value)=>{if(!editable||current()[field]===value)return;current()[field]=value;current().localVersion++;};
    function show(){
      const draft=current();kind.value=selected;format.value=draft.format;format.disabled=!editable;body.readOnly=!editable;
      if(body.value!==draft.body)body.value=draft.body;
      board.querySelector('[data-help-identity]').textContent=draft.code+' / '+draft.name;board.querySelector('[data-help-placement]').textContent=draft.position+' / '+draft.timing;board.querySelector('[data-help-revision]').textContent=draft.revision||'미적용';
      for(const button of buttons){const write=['save','edit','apply'].includes(button.dataset.helpAction);button.disabled=button.dataset.helpBaseDisabled==='true'||write&&!editable;}
      board.dataset.helpSelected=selected;status.textContent=message;
    }
    const close=dialog=>{dialog.close();if(dialog===dialogs.edit)editing=null;};
    for(const dialog of Object.values(dialogs)){
      dialog.addEventListener('cancel',()=>{if(dialog===dialogs.edit)editing=null;});
      dialog.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();close(dialog);}});
    }
    root.addEventListener('input',event=>{if(!belongs(event)||event.target!==body||!editable)return;change('body',body.value);message='선택한 구분의 본문 편집본입니다. 실제 저장 전이며 다른 구분의 입력도 유지됩니다.';show();});
    root.addEventListener('change',event=>{
      if(!belongs(event))return;
      if(event.target===kind&&drafts.has(kind.value)){if(kind.value!==selected){selected=kind.value;selectionVersion++;}message='선택 구분: '+current().name+' · 기존 편집본을 불러왔습니다.';show();}
      else if(event.target===format&&editable){if(['MARKDOWN','HTML','TEXT'].includes(format.value))change('format',format.value);message='작성 형식만 변경했습니다. 원문은 자동 변환하지 않고 그대로 유지합니다.';show();}
    });
    root.addEventListener('click',event=>{
      if(!belongs(event))return;
      const save=event.target.closest('[data-help-action="save"]');if(!save)return;
      const error=!editable?'현재 콘텐츠를 저장할 권한이 없습니다.':mockupHelpBodyError(current(),doc);
      if(error){event.preventDefault();event.stopPropagation();message=error;show();pendingSave=null;return;}
      pendingSave={...current(),selectionVersion};
    },true);
    root.addEventListener('click',event=>{
      if(!belongs(event))return;
      const button=event.target.closest('[data-help-action]');if(!button||button.disabled)return;
      const action=button.dataset.helpAction;
      if(action==='save'){
        const dialog=saveDialog();if(!dialog||!pendingSave)return;
        dialog.dataset.helpSaveContext=JSON.stringify(pendingSave);dialog.dataset.saveTarget=pendingSave.code;
        const target=dialog.querySelector('[aria-label="변경 대상"]');if(target)target.value=pendingSave.name+' ('+pendingSave.code+') · 기준 버전 '+(pendingSave.revision||'미적용');
      }else if(action==='read'){
        const saved=initial.get(selected),dirty=current().body!==saved.body||current().format!==saved.format;
        if(dirty&&!doc.defaultView.confirm('선택한 구분의 미저장 입력을 버리고 보유 조회 예시로 되돌리시겠습니까? 다른 구분의 입력은 유지됩니다.'))message='조회 취소 — 현재 본문·작성 형식과 다른 구분의 입력을 유지합니다.';
        else {if(dirty){current().body=saved.body;current().format=saved.format;current().localVersion++;}message='목업 조회 — 선택한 구분의 보유 예시를 확인했습니다. 실제 서버 조회는 수행하지 않았습니다.';}
      }else if(action==='edit'&&editable){editing={code:selected,localVersion:current().localVersion,selectionVersion};dialogs.edit.querySelector('[data-help-buffer]').value=current().body;dialogs.edit.querySelector('[data-help-modal-status]').textContent='반영 전까지 원문을 바꾸지 않습니다. 오른쪽 아래 모서리로 가로·세로 크기를 조절할 수 있습니다.';dialogs.edit.showModal();}
      else if(action==='apply'&&editable){
        if(editing?.code===selected&&editing.localVersion===current().localVersion&&editing.selectionVersion===selectionVersion){change('body',dialogs.edit.querySelector('[data-help-buffer]').value);close(dialogs.edit);message='큰 편집창의 원문을 선택한 구분의 편집본에 반영했습니다. 실제 저장 전입니다.';}
        else dialogs.edit.querySelector('[data-help-modal-status]').textContent='구분 또는 원문이 바뀌었습니다. 기존 입력을 유지하므로 창을 다시 여세요.';
      }else if(action==='preview'){dialogs.preview.querySelector('[data-rich-text-preview]').replaceChildren(mockupRichTextPreview(current().body,current().format,doc));dialogs.preview.showModal();}
      else if(action==='cancel'||action==='close')close(button.closest('dialog'));
      show();
    });
    root.addEventListener('submit',event=>{
      if(!belongs(event)||!event.target.matches('[data-command-form]')||event.target.closest('[data-command]')!==saveDialog())return;
      const dialog=saveDialog(),snapshot=dialog.dataset.helpSaveContext?JSON.parse(dialog.dataset.helpSaveContext):null;
      const error=!editable?'현재 콘텐츠를 저장할 권한이 없습니다.':!sameContext(snapshot)?'구분 또는 입력이 바뀌어 저장 확인이 낡았습니다. 창을 닫고 선택한 구분에서 다시 저장하세요.':mockupHelpBodyError(current(),doc);
      if(error){event.preventDefault();event.stopPropagation();dialog.dataset.commandConfirmed='false';dialog.querySelector('[data-command-result]').textContent=error;}
    },true);
    root.addEventListener('submit',event=>{
      if(!belongs(event)||event.target.closest('[data-command]')!==saveDialog())return;
      const dialog=saveDialog();if(dialog?.dataset.commandConfirmed==='true'){dialog.querySelector('[data-command-result]').textContent='목업 입력 확인 — '+current().name+' ('+selected+')의 형식·본문·사유만 확인했습니다. 실제 서버 저장이나 버전 발급은 수행하지 않았습니다.';message='선택 구분의 목업 입력만 확인했습니다. 적용 버전과 다른 구분의 편집본은 유지합니다.';show();}
    });
    show();
  }
}
