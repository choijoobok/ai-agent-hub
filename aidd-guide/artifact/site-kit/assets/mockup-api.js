/** 업무 API 목업의 JSON 구조 가져오기. 값은 보관하지 않고 구조만 편집본에 반영한다. */
function mockupJsonFields(source) {
  let i=0;const space=()=>{while(/[ \t\r\n]/.test(source[i]||'')&&i<source.length)i++;};
  const fail=message=>{throw new Error(message+' (위치 '+(i+1)+')');};
  function string(){const begin=i++;while(i<source.length){if(source[i]==='\\'){i+=2;continue;}if(source[i++]==='"'){try{return JSON.parse(source.slice(begin,i));}catch{fail('문자열 형식 오류');}}}fail('닫히지 않은 문자열');}
  function value(depth=0){
    if(depth>64)fail('목업의 구조 미리보기 깊이를 넘었습니다');space();const c=source[i];
    if(c==='{'){
      i++;space();const props=[],seen=new Set();if(source[i]==='}'){i++;return {type:'object',props};}
      while(i<source.length){space();if(source[i]!=='"')fail('객체 키가 필요합니다');const key=string();if(seen.has(key))fail('중복 객체 키: '+key);seen.add(key);space();if(source[i++]!==':')fail('콜론이 필요합니다');props.push([key,value(depth+1)]);space();if(source[i]==='}'){i++;return {type:'object',props};}if(source[i++]!==',')fail('쉼표가 필요합니다');}fail('닫히지 않은 객체');
    }
    if(c==='['){i++;space();const items=[];if(source[i]===']'){i++;return {type:'array',items};}while(i<source.length){items.push(value(depth+1));space();if(source[i]===']'){i++;return {type:'array',items};}if(source[i++]!==',')fail('쉼표가 필요합니다');}fail('닫히지 않은 배열');}
    if(c==='"'){string();return {type:'string'};}
    for(const [literal,type] of [['true','boolean'],['false','boolean'],['null','null']])if(source.slice(i,i+literal.length)===literal){i+=literal.length;return {type};}
    const number=source.slice(i).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);if(number){i+=number[0].length;return {type:/[.eE]/.test(number[0])?'number':'integer'};}
    fail('JSON 값이 필요합니다');
  }
  if(!source.trim())throw new Error('JSON 샘플을 입력하세요.');
  const tree=value();space();if(i!==source.length)fail('JSON 뒤에 다른 값이 있습니다');
  const fields=new Map();
  const name=(parent,key)=>key!=='$'&&/^[A-Za-z_$][\w$]*$/.test(key)?(parent&&parent!=='$'?parent+'.':'')+key:(parent==='$'?'':parent)+'['+JSON.stringify(key)+']';
  const normalize=types=>{const list=[...types].filter(t=>t!=='null');if(list.includes('number')&&list.includes('integer'))list.splice(list.indexOf('integer'),1);return list.length===1?list[0]:'확인 필요';};
  function add(path,node,tokens){let f=fields.get(path);if(!f){f={path,tokens,types:new Set(),itemTypes:new Set()};fields.set(path,f);}f.types.add(node.type);if(node.type==='array')node.items.forEach(x=>f.itemTypes.add(x.type));return f;}
  function visit(node,path,tokens){
    if(node.type==='array'){
      const arrayPath=(path==='$'?'':path)+'[]';add(arrayPath,node,tokens);
      for(const item of node.items){if(item.type==='object')for(const [key,child] of item.props)visit(child,name(arrayPath,key),[...tokens,{array:true},{property:key}]);else if(item.type==='array')visit(item,arrayPath,[...tokens,{array:true}]);}
    }else{if(path!=='$'||node.type!=='object')add(path,node,tokens);if(node.type==='object')for(const [key,child] of node.props)visit(child,name(path,key),[...tokens,{property:key}]);}
  }
  visit(tree,'$',[]);
  return [...fields.values()].map(f=>({path:f.path,tokens:f.tokens,type:normalize(f.types),itemType:f.itemTypes.size?normalize(f.itemTypes):'',conflict:[...f.types].filter(t=>!['null','number','integer'].includes(t)).length+(f.types.has('number')||f.types.has('integer')?1:0)>1||([...f.itemTypes].filter(t=>!['null','number','integer'].includes(t)).length+(f.itemTypes.has('number')||f.itemTypes.has('integer')?1:0)>1)}));
}

function bindApiMockup(root, api) {
  if(root.__apiBound)return;root.__apiBound=true;
  const doc=root.ownerDocument||root;
  const editable=screen=>screen?.dataset.apiEditable==='true';
  const dialogFor=(screen,name)=>[...screen.querySelectorAll('[data-api-dialog]')].find(d=>d.dataset.apiDialog===name);
  bindApiMeaningMockup(root,api);
  bindToolContractMockup(root,api);
  root.querySelectorAll('[data-api-workspace][data-api-editable="true"]').forEach(screen=>{
    const previous=new WeakMap();let scheduled=false;
    const sync=()=>{scheduled=false;for(const row of screen.querySelectorAll('tr[data-grid-row-state]')){
      const source=row.querySelector('[data-row-field="값 출처"]'),lookup=row.querySelector('[data-row-field="조회 API"]');if(!source||!lookup)continue;
      const kind=source.dataset.rowValue,old=previous.get(row);previous.set(row,kind);
      if(old==='API 조회'&&kind!=='API 조회'&&lookup.dataset.rowValue){api.setCell(lookup,'');api.markEdited(row);}
      if(old!==kind)api.refreshRowConditions(row);
      const select=lookup.querySelector('select[data-edit-value]'),summary=lookup.querySelector('[data-cell-editor] summary'),locked=lookup.querySelector('[data-locked-value]');
      const label=lookup.dataset.rowValue?([...select?.options||[]].find(o=>o.value===lookup.dataset.rowValue)?.textContent||lookup.dataset.rowValue):'—';
      if(summary)summary.textContent=label;if(locked)locked.textContent=label;
    }};
    new MutationObserver(changes=>{if(changes.some(c=>c.type==='attributes'||['TBODY','TR'].includes(c.target.tagName))&&!scheduled){scheduled=true;queueMicrotask(sync);}}).observe(screen,{subtree:true,childList:true,attributes:true,attributeFilter:['data-row-value']});sync();
  });
  root.addEventListener('click',event=>{
    const close=event.target.closest('[data-api-close]');if(close){close.closest('dialog').close();return;}
    const button=event.target.closest('[data-api-action]');if(!button||button.disabled)return;
    const screen=button.closest('[data-api-workspace]');if(!screen)return;
    const action=button.dataset.apiAction;
    if(action==='구현 계약 다시 읽기'){screen.__readToolContract?.();return;}
    if(action!=='JSON 샘플 가져오기'){const dialog=dialogFor(screen,action);dialog?.showModal();return;}
    if(!editable(screen))return;
    const grid=button.closest('[data-grid-view]');if(!grid)return;
    const dialog=doc.createElement('dialog');dialog.className='command-dialog';dialog.setAttribute('data-api-import','');dialog.setAttribute('aria-label','JSON 샘플 가져오기');dialog.style.cssText='width:min(1000px,92vw);max-height:85vh;overflow:auto';
    dialog.append(screen.querySelector('template[data-api-import-template]').content.cloneNode(true));
    screen.append(dialog);dialog.showModal();let parsed=null;
    const input=dialog.querySelector('textarea'),status=dialog.querySelector('[data-import-status]'),apply=dialog.querySelector('[data-import-apply]');
    const compareOnly=Boolean(screen.dataset.toolContracts);
    if(compareOnly){apply.hidden=true;dialog.querySelector('h3').textContent='JSON 샘플과 구현 계약 비교';dialog.querySelector('p').textContent='구조 차이만 확인합니다. 구현 계약의 필드는 변경하지 않습니다.';}
    const existing=()=>new Map([...grid.querySelectorAll('tbody tr[data-grid-row-state]')].map(row=>[row.querySelector('[data-row-field="필드 경로"]')?.dataset.rowValue,row]));
    input.addEventListener('input',()=>{parsed=null;apply.disabled=true;status.textContent='입력이 변경되었습니다. 다시 파싱하세요.';});
    dialog.querySelector('[data-import-preview]').addEventListener('click',()=>{
      parsed=null;apply.disabled=true;dialog.querySelector('tbody').replaceChildren();
      try{
        const fields=mockupJsonFields(input.value),current=existing();let conflicts=0;
        for(const f of fields){const old=current.get(f.path),oldType=old?.querySelector('[data-row-field="자료형"]')?.dataset.rowValue;f.existing=Boolean(old);f.conflict=f.conflict||Boolean(oldType&&oldType!==f.type);if(f.conflict)conflicts++;
          const tr=doc.createElement('tr');for(const text of [f.path,f.type,f.itemType||'—',f.conflict?'타입 충돌 — 보완 필요':old?'기존 설정 보존':'신규 — 검수 필요']){const td=doc.createElement('td');td.textContent=text;tr.append(td);}dialog.querySelector('tbody').append(tr);
        }
        parsed=fields;apply.disabled=compareOnly||conflicts>0||fields.length===0;status.textContent=compareOnly?'비교 '+fields.length+'개 경로 · 타입 차이 '+conflicts+'건. 샘플로 구현 계약을 변경하지 않습니다.':conflicts?'타입 충돌 '+conflicts+'건. 기존 입력을 유지하며 샘플/명세를 보완한 뒤 다시 확인하세요.':fields.length+'개 경로. 신규만 추가하고 기존 가이드·개인정보·마스킹·코드명 설정은 보존합니다.';
      }catch(error){status.textContent=error.message+' 기존 입력은 유지됩니다.';}
    });
    apply.addEventListener('click',()=>{
      if(apply.disabled||!parsed||!editable(screen)||!grid.isConnected)return;
      const current=existing();let count=0;
      for(const f of [...parsed].reverse())if(!current.has(f.path)){
        const row=api.appendRow(grid,{'필드 경로':f.path,'자료형':f.type,'배열 항목 자료형':f.itemType||'확인 필요','필수 여부':'미확인','NULL 허용':'미확인','개인·민감정보 여부':'미확인','검수 상태':'미확인','실행 결과 관련 여부':'false','결과 역할':'해당 없음'});if(row){row.dataset.jsonTokens=JSON.stringify(f.tokens);count++;}
      }
      grid.querySelector('.pg').textContent='구조 '+count+'개 추가. 필수·NULL·개인정보를 확인한 뒤 선택 대상 전체를 저장하세요.';input.value='';dialog.close();
    });
    dialog.querySelector('[data-import-cancel]').addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>dialog.remove(),{once:true});
  });
  root.addEventListener('input',event=>{
    const input=event.target;if(!input.matches('[data-form-value]')||input.readOnly)return;
    const screen=input.closest('[data-api-workspace]');if(!editable(screen))return;
    const master=screen.querySelector('[data-grid-role="master"] tbody tr[data-grid-row-state]');if(master)api.markEdited(master);
  });
}

/** 가상 공개 레지스트리로 빈 선택/계약 재조회 흐름만 시연한다. 자바 빈은 실행하지 않는다. */
function bindToolContractMockup(root,api){
  const doc=root.ownerDocument||root;
  root.querySelectorAll('[data-tool-contracts]').forEach(screen=>{
    if(screen.dataset.schemaWorkspace||screen.dataset.apiEditable!=='true')return;
    const contracts=JSON.parse(screen.dataset.toolContracts),master=screen.querySelector('[data-grid-role="master"] tbody tr[data-grid-row-state]');
    const cell=name=>master?.querySelector('[data-row-field="'+name+'"]'),bean=cell('서비스 빈');if(!bean)return;
    let appliedBean=bean.dataset.rowValue,observed=appliedBean,dialog=null;
    const open=()=>{
      if(dialog)return;
      const selected=bean.dataset.rowValue,contract=contracts[selected];if(!contract)return;
      dialog=doc.createElement('dialog');dialog.className='command-dialog';dialog.setAttribute('data-tool-contract-preview','');dialog.setAttribute('aria-label','구현 계약 차이 확인');dialog.style.cssText='width:min(1000px,92vw);max-height:85vh;overflow:auto';
      const title=doc.createElement('h3');title.textContent='구현 계약 차이 확인 — '+selected+' / '+contract.version;dialog.append(title);
      const help=doc.createElement('p');help.textContent='가상 레지스트리의 자동 분석 예시입니다. 같은 경로의 설명·가이드·정책을 보존하고 구조만 갱신합니다. 변경/삭제 필드의 코드·판정 참조는 저장 전 확인하세요.';dialog.append(help);
      const table=doc.createElement('table');table.style.width='100%';const tbody=doc.createElement('tbody');table.append(tbody);dialog.append(table);
      const changes=[];
      for(const [name,fields]of [['입력 JSON 스키마',contract.input],['응답 JSON 스키마',contract.output]]){
        const grid=[...screen.querySelectorAll('[data-grid-name]')].find(g=>g.dataset.gridName===name);if(!grid)continue;
        const current=new Map([...grid.querySelectorAll('tbody tr[data-grid-row-state]')].filter(r=>r.dataset.gridRowState!=='D').map(r=>[r.querySelector('[data-row-field="필드 경로"]')?.dataset.rowValue,r]));
        const names=new Set(fields.map(f=>f['필드 경로']));
        for(const f of fields){const old=current.get(f['필드 경로']);changes.push({grid,f,old});const tr=doc.createElement('tr');for(const value of [name,f['필드 경로'],f['자료형'],old?'기존 보충 설정 보존':'자동 분석 신규']){const td=doc.createElement('td');td.textContent=value;tr.append(td);}tbody.append(tr);}
        for(const [path,old]of current)if(!names.has(path)){changes.push({grid,old,remove:true});const tr=doc.createElement('tr');tr.textContent=name+' / '+path+' — 계약에서 제거됨: 연결 참조 확인 필요';tbody.append(tr);}
      }
      const apply=doc.createElement('button');apply.type='button';apply.className='btn';apply.textContent='계약 반영';apply.setAttribute('data-tool-contract-apply','');
      const cancel=doc.createElement('button');cancel.type='button';cancel.className='btn ghost';cancel.textContent='취소';cancel.setAttribute('data-tool-contract-cancel','');dialog.append(apply,cancel);
      let accepted=false;
      apply.addEventListener('click',()=>{
        const structural=['필드 경로','자료형','배열 항목 자료형','필수 여부','NULL 허용'];
        for(const change of changes){const {grid,f,old,remove}=change;if(remove){old.dataset.gridRowState='D';old.classList.add('deleted');}
          else if(old){for(const key of structural){const td=old.querySelector('[data-row-field="'+key+'"]');if(td)api.setCell(td,f[key]??'');}api.markEdited(old);}
          else api.appendRow(grid,{...f,'개인·민감정보 여부':f['개인·민감정보 여부']??'미확인','검수 상태':'미확인'});
          api.refreshGrid(grid);
        }
        appliedBean=selected;observed=selected;accepted=true;api.setCell(cell('계약 버전'),contract.version);api.setCell(cell('업무 유형'),contract.operation);api.setCell(cell('구현 상태'),'정상');api.markEdited(master);dialog.close();
      });
      cancel.addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>{if(!accepted){observed=appliedBean;api.setCell(bean,appliedBean);api.markEdited(master);}dialog.remove();dialog=null;},{once:true});
      screen.append(dialog);dialog.showModal();
    };
    screen.__readToolContract=open;
    new MutationObserver(()=>{const next=bean.dataset.rowValue;if(next!==observed){observed=next;open();}}).observe(bean,{attributes:true,attributeFilter:['data-row-value']});
  });
}

/** 필드 자료형에 따라 값의 앞자리 0·정확 문자열을 보존한다. */
function mockupCodeValue(text,type) {
  if(type==='string')return text;
  if(type==='boolean'&&/^(true|false)$/.test(text))return text==='true';
  if(['integer','number'].includes(type)&&/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(text)){
    const value=Number(text);if(Number.isFinite(value)&&(type!=='integer'||Number.isSafeInteger(value)))return value;
  }
  throw new Error('선택 필드의 자료형에 맞는 코드값을 입력하세요.');
}

/** 의미 매핑의 필드별 편집 문맥을 보존한다. 실제 조회·저장은 수행하지 않는다. */
function mockupMeaningType(type,itemType){
  const scalar=['string','integer','number','boolean'];
  return scalar.includes(type)?type:type==='array'&&scalar.includes(itemType)?itemType:'';
}
function bindApiMeaningMockup(root,api) {
  const doc=root.ownerDocument||root;let sequence=0;
  const cells=(row,name)=>[...row.querySelectorAll('[data-row-field]')].find(c=>c.dataset.rowField===name);
  const val=(row,name)=>cells(row,name)?.dataset.rowValue||'';
  const rows=grid=>[...(grid?.querySelectorAll('tbody tr[data-grid-row-state]')||[])];
  const live=row=>row.dataset.gridRowState!=='D';
  const put=(row,name,value)=>{const cell=cells(row,name);if(cell&&cell.dataset.rowValue!==String(value??''))api.setCell(cell,value);};
  const record=row=>{try{return JSON.parse(row.dataset.apiRecord||'{}');}catch{return {};}};
  const options=(select,items,current)=>{if(!select)return;const signature=JSON.stringify([items,current]);if(select.dataset.meaningOptions===signature)return;select.dataset.meaningOptions=signature;select.replaceChildren();for(const [value,text] of [['','미지정'],...items]){const option=doc.createElement('option');option.value=value;option.textContent=text;select.append(option);}select.value=current;};
  for(const board of root.querySelectorAll('[data-api-meaning]')){
    const screen=board.closest('.screen'),list=board.querySelector('[data-meaning-part="필드 목록"] [data-grid-view]'),codes=board.querySelector('[data-meaning-part="직접 등록"] [data-grid-view]'),guidePart=board.querySelector('[data-meaning-part="참조 가이드"]'),codePart=board.querySelector('[data-meaning-part="직접 등록"]'),guide=guidePart?.querySelector('textarea'),status=board.querySelector('[data-meaning-status]');
    if(!list||!codes||!guide)continue;
    const split=board.querySelector('[data-meaning-split]'),handle=board.querySelector('[data-meaning-splitter]');
    if(split&&handle){
      const left=board.querySelector('[data-meaning-part="필드 목록"]'),right=board.querySelector('[data-meaning-detail]');let pointer=null;
      const resize=value=>{const ratio=Math.max(20,Math.min(80,value));left.style.flex=ratio+' 1 0';right.style.flex=(100-ratio)+' 1 0';handle.setAttribute('aria-valuenow',String(Math.round(ratio)));};
      handle.addEventListener('pointerdown',event=>{if(event.button!==0)return;event.preventDefault();pointer=event.pointerId;handle.setPointerCapture(pointer);});
      handle.addEventListener('pointermove',event=>{if(event.pointerId!==pointer)return;const rect=split.getBoundingClientRect();resize((event.clientX-rect.left)/rect.width*100);});
      const stop=()=>{pointer=null;};handle.addEventListener('pointerup',stop);handle.addEventListener('pointercancel',stop);handle.addEventListener('lostpointercapture',stop);
      handle.addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();resize(Number(handle.getAttribute('aria-valuenow'))+(event.key==='ArrowLeft'?-5:5));});
    }
    const states=new Map();let selected=null,scheduled=false;
    const fieldSource={ '경로 변수':['경로 변수','순서'],'쿼리':['쿼리 파라미터','파라미터명'],'헤더':['헤더 파라미터','파라미터명'],'바디':['바디 JSON 스키마','필드 경로'],'입력':['입력 JSON 스키마','필드 경로'],'응답':['응답 JSON 스키마','필드 경로'] };
    const fields=area=>{const source=fieldSource[area];if(!source)return [];const grid=[...screen.querySelectorAll('[data-grid-name]')].find(g=>g.dataset.gridName===source[0]);return rows(grid).filter(live).filter(r=>!cells(r,'스키마 일치 여부')||val(r,'스키마 일치 여부')==='true').map(r=>({key:val(r,source[1]),label:area==='경로 변수'?val(r,'순서')+' — '+val(r,'설명'):val(r,source[1]),type:mockupMeaningType(val(r,'자료형'),val(r,'배열 항목 자료형')),result:val(r,'자료형')!=='array'&&val(r,'실행 결과 관련 여부')==='true'})).filter(f=>f.type);};
    const state=row=>{if(!states.has(row)){const r=record(row);states.set(row,{id:r.mappingId||'M-NEW-'+(++sequence),guide:row.dataset.gridRowState==='C'?'':r['참조 가이드']||'',original:r['참조 가이드']||'',area:val(row,'필드 구분')});}return states.get(row);};
    const dirty=row=>{api.markEdited(row);if(state(row).guide!==state(row).original&&row.dataset.gridRowState===''){row.dataset.gridRowState='U';const label=row.querySelector('[data-row-state]');if(label)label.textContent='수정';const revert=row.querySelector('[data-revert-row]');if(revert)revert.hidden=false;}};
    function sync(){
      scheduled=false;
      for(const row of rows(list)){const fresh=!states.has(row);const st=state(row);if(fresh&&row.dataset.gridRowState==='C')selected=row;const area=val(row,'필드 구분');if(st.area!==area){put(row,'대상 필드','');st.area=area;}
        options(cells(row,'대상 필드')?.querySelector('select'),fields(area).map(f=>[f.key,f.label]),val(row,'대상 필드'));
      }
      if(!selected?.isConnected||!live(selected))selected=rows(list).find(live)||null;
      const current=selected?state(selected):null,mode=selected?val(selected,'설정 방식'):'';
      guidePart.hidden=!current||mode!=='참조 가이드';codePart.hidden=!current||mode!=='직접 등록';
      guide.readOnly=screen.dataset.apiEditable!=='true'||!current;const text=current?.guide||'';if(guide.value!==text)guide.value=text;
      for(const row of rows(codes)){if(!row.dataset.meaningOwner){const rec=record(row);row.dataset.meaningOwner=row.dataset.gridRowState==='C'?(current?.id||''):rec.mappingId||'';row.dataset.meaningValueId=rec.valueId||'V-NEW-'+(++sequence);}row.hidden=!current||row.dataset.meaningOwner!==current.id;}
      api.refreshGrid(codes);const count=rows(codes).filter(r=>!r.hidden&&live(r)).length;codes.querySelector('.cnt').textContent=count+'건';codes.querySelector('.pg').textContent='선택 필드의 코드 '+count+'건 · 선택 대상 전체 저장';
      let message=current?'현재 필드: '+val(selected,'필드 구분')+' / '+val(selected,'대상 필드')+' · '+mode:'의미 매핑할 필드를 추가하세요.';
      if(current){const field=fields(val(selected,'필드 구분')).find(f=>f.key===val(selected,'대상 필드'));const duplicates=rows(list).filter(live).filter(r=>val(r,'필드 구분')===val(selected,'필드 구분')&&val(r,'대상 필드')===val(selected,'대상 필드')).length;
        if(!field)message+=' — 등록 값 필드 또는 단순값 배열 원소를 선택하세요.';else if(duplicates>1)message+=' — 같은 필드의 중복 매핑입니다.';else if(mode==='직접 등록'){
          const seen=new Set();try{for(const row of rows(codes).filter(r=>!r.hidden&&live(r))){const value=mockupCodeValue(val(row,'코드값'),field.type),key=typeof value+':'+JSON.stringify(value);if(seen.has(key))throw Error('중복 코드값입니다.');seen.add(key);if(!val(row,'명칭').trim())throw Error('코드 명칭이 필요합니다.');}if(!seen.size)throw Error('코드값을 등록하세요.');}catch(error){message+=' — '+error.message;}
        }else if(mode==='참조 가이드'&&!text.trim())message+=' — 참조 가이드를 입력하세요.';
      }
      status.textContent=message;
      // 응답 실행 결과의 직접 등록 항목만 판정 후보로 제공한다.
      const resultGrid=[...screen.querySelectorAll('[data-grid-name]')].find(g=>g.dataset.gridName==='결과 판정 설정');
      for(const row of rows(resultGrid)){
        const field=val(row,'결과 필드 경로');const owner=rows(list).find(r=>live(r)&&val(r,'필드 구분')==='응답'&&val(r,'대상 필드')===field&&val(r,'설정 방식')==='직접 등록'&&fields('응답').some(f=>f.key===field&&f.result));
        const candidates=owner?rows(codes).filter(r=>live(r)&&r.dataset.meaningOwner===state(owner).id):[];
        const label=r=>val(r,'코드값')+' — '+val(r,'명칭');const selection=val(row,'코드 매핑 항목');
        if(!row.dataset.meaningResultId||selection!==row.dataset.meaningResultLabel)row.dataset.meaningResultId=candidates.find(r=>label(r)===selection)?.dataset.meaningValueId||'';
        const chosen=candidates.find(r=>r.dataset.meaningValueId===row.dataset.meaningResultId);
        if(chosen){put(row,'코드 매핑 항목',label(chosen));put(row,'판정 값',val(chosen,'코드값'));put(row,'코드 명칭·의미',val(chosen,'명칭')+' / '+val(chosen,'의미·사용 조건'));}
        else {put(row,'판정 값','확인 필요');put(row,'코드 명칭·의미','유효한 응답 코드 매핑을 선택하세요.');}
        row.dataset.meaningResultLabel=val(row,'코드 매핑 항목');options(cells(row,'코드 매핑 항목')?.querySelector('select'),candidates.map(r=>[label(r),label(r)]),val(row,'코드 매핑 항목'));
      }
    }
    const schedule=()=>{if(!scheduled){scheduled=true;queueMicrotask(sync);}};
    board.addEventListener('click',event=>{const row=event.target.closest('tr[data-grid-row-state]');if(row?.closest('[data-grid-view]')===list){selected=row;if(event.target.closest('[data-revert-row]'))state(row).guide=state(row).original;}schedule();});
    guide.addEventListener('input',()=>{if(!selected||guide.readOnly)return;state(selected).guide=guide.value;dirty(selected);schedule();});
    const observer=new MutationObserver(changes=>{if(changes.some(change=>change.type==='attributes'||['TBODY','TR'].includes(change.target.tagName)))schedule();});observer.observe(screen,{subtree:true,childList:true,attributes:true,attributeFilter:['data-row-value','data-grid-row-state']});
    sync();
  }
}
