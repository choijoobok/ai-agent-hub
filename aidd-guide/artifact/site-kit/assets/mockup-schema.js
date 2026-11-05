/** API/내부 도구 목업의 원천 명세 동기화. 실제 서버·빈·DB를 호출하지 않는다. */
const schemaStructural=['필드 경로','자료형','배열 항목 자료형','필수 여부','NULL 허용'];
const schemaSupplement=['설명','조회 API','값 출처','값 확보·설정 가이드','고정값·기본값','개인·민감정보 여부','이력 마스킹 유형','이력 마스킹 수준','마스킹 방법','실행 결과 관련 여부','결과 역할','결과 설명','의미·해석 가이드','해석 가이드'];
function mockupSchemaFields(source){
  if(source===null)return [];
  if(typeof source==='string'){mockupJsonFields(source);source=JSON.parse(source);}
  const rows=[],types=['object','array','string','integer','number','boolean','null'];
  const name=(parent,key)=>key!=='$'&&/^[A-Za-z_$][\w$]*$/.test(key)?(parent&&parent!=='$'?parent+'.':'')+key:(parent==='$'?'':parent)+'['+JSON.stringify(key)+']';
  function visit(s,path,required=false,depth=0,emit=true){
    if(depth>30||!s||Array.isArray(s)||typeof s!=='object')throw Error('정적 객체 형식의 스키마가 필요합니다.');
    for(const key of Object.keys(s))if(!['$schema','type','properties','items','required','additionalProperties','title','description','enum'].includes(key))throw Error('목업에서 지원하지 않는 스키마 항목: '+key);
    const ts=Array.isArray(s.type)?s.type:[s.type],concrete=ts.filter(t=>t!=='null');
    if(ts.some(t=>!types.includes(t))||new Set(ts).size!==ts.length||concrete.length>1||!ts.length)throw Error('자료형을 하나와 선택적 null로 명시하세요.');
    const type=concrete[0]||'null',isArray=type==='array',fieldPath=isArray?(path==='$'?'':path)+'[]':path;
    if(s.properties!==undefined&&(type!=='object'||!s.properties||Array.isArray(s.properties)||typeof s.properties!=='object'))throw Error('properties는 객체 자료형의 객체여야 합니다.');
    if(s.required!==undefined&&(!Array.isArray(s.required)||s.required.some(x=>typeof x!=='string'||!Object.hasOwn(s.properties??{},x))||new Set(s.required).size!==s.required.length))throw Error('required는 등록된 프로퍼티의 중복 없는 목록이어야 합니다.');
    if(s.additionalProperties!==undefined&&typeof s.additionalProperties!=='boolean')throw Error('동적 additionalProperties는 명시 계약이 필요합니다.');
    if(isArray&&!s.items)throw Error('배열 items 구조를 명시하세요.');
    let itemType='';if(isArray){const it=Array.isArray(s.items.type)?s.items.type.filter(x=>x!=='null'): [s.items.type];itemType=it[0]||'null';}
    if(emit&&(path!=='$'||type!=='object'))rows.push({'필드 경로':fieldPath,'자료형':type,'배열 항목 자료형':itemType,'필수 여부':required?'필수':'선택','NULL 허용':ts.includes('null')?'허용':'불가'});
    if(type==='object')for(const [key,child]of Object.entries(s.properties??{}))visit(child,name(path,key),(s.required??[]).includes(key),depth+1);
    if(isArray)visit(s.items,fieldPath,false,depth+1,itemType==='array');
  }
  visit(source,'$');if(new Set(rows.map(r=>r['필드 경로'])).size!==rows.length)throw Error('스키마 경로가 중복됩니다.');return rows;
}
function mockupSchemaFromSample(text){
  mockupJsonFields(text);const value=JSON.parse(text);
  const infer=v=>v===null?{type:'null'}:Array.isArray(v)?{type:'array',items:v.length?v.map(infer).reduce(merge):{}}:typeof v==='object'?{type:'object',properties:Object.fromEntries(Object.entries(v).map(([k,x])=>[k,infer(x)])),additionalProperties:false}:{type:typeof v==='number'?(Number.isInteger(v)?'integer':'number'):typeof v};
  function merge(a,b){if(a.type!==b.type){if(['integer','number'].includes(a.type)&&['integer','number'].includes(b.type))return {type:'number'};throw Error('샘플의 혼합 자료형은 직접 스키마를 보완하세요.');}if(a.type==='object')for(const [k,x]of Object.entries(b.properties))a.properties[k]=Object.hasOwn(a.properties,k)?merge(a.properties[k],x):x;if(a.type==='array')a.items=Object.keys(a.items).length?Object.keys(b.items).length?merge(a.items,b.items):a.items:b.items;return a;}
  return infer(value);
}
function mockupSchemaSync(current,incoming){
  const old=new Map(current.map(r=>[r['필드 경로'],r])),paths=new Set(incoming.map(r=>r['필드 경로']));
  if(paths.size!==incoming.length||old.size!==current.length)throw Error('필드 경로가 중복됩니다.');
  const result=incoming.map(f=>{const previous=old.get(f['필드 경로']);const changed=previous&&schemaStructural.some(k=>String(previous[k]??'')!==String(f[k]??''));return {...previous,...f,'스키마 일치 여부':true,'동기화 안내':previous?(changed?'구조 변경 — 보충 설정 재확인':previous['동기화 안내']==='구조 변경 — 보충 설정 재확인'?previous['동기화 안내']:'일치'):'신규 — 보충 설정 확인'};});
  for(const r of current)if(!paths.has(r['필드 경로']))result.push({...r,'스키마 일치 여부':false,'동기화 안내':'현재 원천에 없음 — 설정 보존'});
  return result;
}
function mockupSchemaCopy(source,target){
  if(source['자료형']!==target['자료형']||(source['자료형']==='array'&&source['배열 항목 자료형']!==target['배열 항목 자료형']))throw Error('자료형이 같은 필드에만 붙여넣을 수 있습니다.');
  return Object.fromEntries(schemaSupplement.filter(k=>Object.hasOwn(source,k)&&Object.hasOwn(target,k)).map(k=>[k,source[k]]));
}
function bindSchemaMockup(root,api){
  const doc=root.ownerDocument||root;
  for(const screen of root.querySelectorAll('[data-schema-workspace]')){
    if(screen.__schemaBound)continue;screen.__schemaBound=true;
    const config=JSON.parse(screen.dataset.schemaWorkspace),editable=screen.dataset.apiEditable==='true',tool=config.kind==='TOOL';
    const grids=()=>config.areas.map(name=>[...screen.querySelectorAll('[data-grid-name]')].find(g=>g.dataset.gridName===name)).filter(Boolean);
    const allRows=g=>[...g.querySelectorAll('tbody tr[data-grid-row-state]')];
    const rows=g=>allRows(g).filter(r=>r.dataset.gridRowState!=='D');
    const value=(r,k)=>r?.querySelector('[data-row-field="'+k+'"]')?.dataset.rowValue??'';
    const values=r=>Object.fromEntries([...r.querySelectorAll('[data-row-field]')].map(c=>[c.dataset.rowField,c.dataset.rowValue]));
    const set=(r,k,v)=>{const c=r.querySelector('[data-row-field="'+k+'"]');if(c)api.setCell(c,v);};
    const master=()=>screen.querySelector('[data-grid-role="master"] tbody tr.sel')||screen.querySelector('[data-grid-role="master"] tbody tr[data-grid-row-state]');
    const status=(g,text)=>{let p=g.querySelector('[data-schema-status]');if(!p){p=doc.createElement('p');p.className='notes';p.setAttribute('role','status');p.dataset.schemaStatus='';g.append(p);}p.textContent=text;};
    let toolReady=true,clipboard=null,dialog=null,sourceByArea=config.sources??{},observedBean=value(master(),'서비스 빈');
    const authoritative=new Map(grids().map(g=>[g,new Map(rows(g).filter(r=>value(r,'스키마 일치 여부')==='true').map(r=>[value(r,'필드 경로'),values(r)]))]));
    function open(title,build){if(dialog)return;dialog=doc.createElement('dialog');dialog.className='command-dialog';dialog.dataset.schemaDialog='';dialog.setAttribute('aria-label',title);dialog.style.cssText='width:min(1050px,94vw);max-height:85vh;overflow:auto';const h=doc.createElement('h3');h.textContent=title;dialog.append(h);const body=doc.createElement('div');dialog.append(body);const footer=doc.createElement('div');footer.className='row actions';dialog.append(footer);const button=(name,fn,key)=>{const b=doc.createElement('button');b.type='button';b.className='btn';b.textContent=name;if(key)b.dataset[key]='';b.addEventListener('click',fn);footer.append(b);return b;};build(body,button);button('취소',()=>dialog.close(),'schemaCancel');dialog.addEventListener('close',()=>{dialog.remove();dialog=null;},{once:true});screen.append(dialog);dialog.showModal();}
    const note=(parent,text)=>{const p=doc.createElement('p');p.textContent=text;parent.append(p);return p;};
    function preview(parent,plans){parent.replaceChildren();for(const {grid,next}of plans){const h=doc.createElement('h4');h.textContent=grid.dataset.gridName;parent.append(h);for(const r of next)note(parent,r['필드 경로']+' / '+r['자료형']+' / '+r['동기화 안내']);}}
    function applyPlans(plans){for(const {grid,next}of plans){authoritative.set(grid,new Map(next.filter(r=>r['스키마 일치 여부']===true).map(r=>[r['필드 경로'],r])));const existing=new Map(allRows(grid).map(r=>[value(r,'필드 경로'),r]));for(const data of [...next].reverse()){let row=existing.get(data['필드 경로']);if(row){if(row.dataset.gridRowState==='D'&&data['스키마 일치 여부']===true){api.setRowState(row,'U');row.querySelectorAll('td.edit input:not([readonly]),td.edit select,td.edit textarea:not([readonly]),td.edit [data-config-switch]').forEach(e=>e.disabled=false);}for(const k of [...schemaStructural,'스키마 일치 여부','동기화 안내'])set(row,k,data[k]);api.markEdited(row);}else row=api.appendRow(grid,{...data,'개인·민감정보 여부':'미확인','검수 상태':'미확인'});}api.refreshGrid(grid);status(grid,'스키마 동기화 반영. 불일치 필드는 설정을 보존했습니다. 실제 저장은 선택 대상 전체 저장입니다.');}api.markEdited(master());}
    function editSchema(grid){if(!editable)return;open(grid.dataset.gridName+' 원본 편집',(body,button)=>{
      note(body,'상단 API 내용 마스터의 원본을 편집합니다. 필드는 자동 도출되고 사라진 경로는 불일치로 남습니다.');const input=doc.createElement('textarea');input.rows=11;input.style.width='100%';input.setAttribute('aria-label','JSON 스키마');input.value=JSON.stringify(sourceByArea[grid.dataset.gridName]??{type:'object',properties:{}},null,2);body.append(input);
      const none=doc.createElement('input');none.type='checkbox';none.checked=sourceByArea[grid.dataset.gridName]===null;const lab=doc.createElement('label');lab.append(none,doc.createTextNode('본문 없음'));body.append(lab);
      const sample=doc.createElement('textarea');sample.rows=3;sample.style.width='100%';sample.setAttribute('aria-label','JSON 샘플');sample.placeholder='선택: JSON 샘플로 구조 초안 만들기';body.append(sample);const message=note(body,''),diff=doc.createElement('div');body.append(diff);let pending=null;
      const apply=button('스키마 반영',()=>{if(!pending)return;applyPlans(pending.plans);sourceByArea[grid.dataset.gridName]=pending.source;screen.dataset.schemaEdited=JSON.stringify(sourceByArea);dialog.close();},'schemaApply');apply.disabled=true;
      const invalidate=()=>{pending=null;apply.disabled=true;message.textContent='원본이 바뀌었습니다. 다시 미리보기 하세요.';};input.addEventListener('input',invalidate);none.addEventListener('change',invalidate);
      button('샘플로 초안 생성',()=>{try{input.value=JSON.stringify(mockupSchemaFromSample(sample.value),null,2);sample.value='';none.checked=false;invalidate();}catch(e){message.textContent=e.message;}},'schemaSample');
      button('차이 미리보기',()=>{pending=null;apply.disabled=true;try{const source=none.checked?null:input.value,fields=mockupSchemaFields(source),plans=[{grid,next:mockupSchemaSync(allRows(grid).map(values),fields)}];preview(diff,plans);pending={source:none.checked?null:JSON.parse(input.value),plans};apply.disabled=false;message.textContent='현재 원천에 포함된 필드와 불일치 필드를 확인한 뒤 반영하세요.';}catch(e){message.textContent=e.message+' 기존 원본/필드/판정은 유지됩니다.';}},'schemaPreview');
    });}
    function reloadTool(){if(!editable)return;const bean=value(master(),'서비스 빈'),contract=JSON.parse(screen.dataset.toolContracts??'{}')[bean];if(!contract){status(grids()[0],'현재 빈 명세를 확인할 수 없어 기존 구조와 판정을 유지합니다.');return;}
      try{const plans=grids().map((grid,i)=>({grid,next:mockupSchemaSync(allRows(grid).map(values),contract[i?'output':'input'])}));open('서비스 빈 정보 리로드',(body,button)=>{note(body,bean+' / '+contract.version+' — 가상 현재 명세. 입력·응답을 함께 확인합니다.');const diff=doc.createElement('div');body.append(diff);preview(diff,plans);button('명세 반영',()=>{applyPlans(plans);set(master(),'계약 버전',contract.version);set(master(),'업무 유형',contract.operation);set(master(),'구현 상태','정상');observedBean=bean;toolReady=true;dialog.close();},'schemaApply');});}catch(e){status(grids()[0],e.message+' 기존 구조와 판정을 유지합니다.');}
    }
    function refs(grid,targets){const paths=new Set(targets.map(r=>value(r,'필드 경로'))),area=grid.dataset.gridName.startsWith('응답')?'응답':tool?'입력':'바디',found=[];
      for(const g of screen.querySelectorAll('[data-grid-name]'))if(!grids().includes(g))for(const r of rows(g)){const vals=values(r);if((vals['필드 구분']===area&&paths.has(vals['대상 필드']))||(area==='응답'&&(paths.has(vals['결과 필드 경로'])||paths.has(vals['응답 경로']))))found.push(g.dataset.gridName+' / '+(vals['대상 필드']||vals['결과 필드 경로']||vals['응답 경로']));}return found;
    }
    function purge(grid){if(!editable)return;if(tool&&!toolReady){status(grid,'선택한 서비스 빈의 현재 명세를 리로드한 뒤 삭제하세요.');return;}const targets=rows(grid).filter(r=>value(r,'스키마 일치 여부')==='false');if(!targets.length){status(grid,'삭제할 스키마 불일치 필드가 없습니다. 일치/미확인 필드는 삭제할 수 없습니다.');return;}const references=refs(grid,targets);open('스키마 불일치 필드 일괄 삭제',(body,button)=>{note(body,targets.length+'개: '+targets.map(r=>value(r,'필드 경로')).join(', '));if(references.length){note(body,'참조를 먼저 해소하세요: '+references.join(' · '));return;}note(body,'선택 탭 전체 불일치 필드만 로컬 삭제 표시합니다. 과거 이력은 보존합니다.');button('불일치 필드 삭제',()=>{if(targets.some(r=>value(r,'스키마 일치 여부')!=='false')||refs(grid,targets).length){status(grid,'판정/참조가 바뀌었습니다. 다시 확인하세요.');dialog.close();return;}for(const r of targets)api.setRowState(r,'D');api.refreshGrid(grid);api.markEdited(master());status(grid,targets.length+'개 불일치 필드를 삭제 표시했습니다. 전체 저장 전까지 현재 적용값은 유지됩니다.');dialog.close();},'schemaDelete');});}
    screen.addEventListener('mockup:row-restored',event=>{const row=event.target,grid=row.closest('[data-grid-view]');if(!authoritative.has(grid))return;const source=authoritative.get(grid).get(value(row,'필드 경로'));if(source)for(const k of schemaStructural)set(row,k,source[k]);set(row,'스키마 일치 여부',Boolean(source));set(row,'동기화 안내',source?'보충 설정 원복 — 현재 구조 재확인':'현재 원천에 없음 — 설정 보존');api.markEdited(row);});
    screen.addEventListener('click',event=>{
      const b=event.target.closest('[data-schema-action]');if(!b||b.disabled)return;const action=b.dataset.schemaAction,grid=b.closest('[data-grid-view]');
      if(action==='JSON 스키마 편집')return editSchema(grid);if(action==='서비스 빈 정보 리로드')return reloadTool();if(!grid||!grids().includes(grid))return;
      if(action==='스키마 불일치 필드 일괄 삭제')return purge(grid);
      const selected=rows(grid).find(r=>r.classList.contains('sel'));if(!selected){status(grid,'필드를 먼저 선택하세요.');return;}
      if(action==='필드 설정 복사'){clipboard={grid,source:values(selected),owner:master()};status(grid,'설정을 복사했습니다: '+clipboard.source['필드 경로']);return;}
      if(action==='필드 설정 붙여넣기'&&editable){if(!clipboard||clipboard.grid!==grid||clipboard.owner!==master()){status(grid,'같은 대상·같은 탭에서 설정을 복사하세요.');return;}try{const changes=mockupSchemaCopy(clipboard.source,values(selected));open('필드 설정 붙여넣기',(body,button)=>{note(body,clipboard.source['필드 경로']+' → '+value(selected,'필드 경로')+' · 경로/자료형/필수/ID/판정은 유지합니다.');for(const [key,v]of Object.entries(changes))note(body,key+': '+value(selected,key)+' → '+v);button('설정 덮어쓰기',()=>{for(const [k,v]of Object.entries(changes))set(selected,k,v);api.markEdited(selected);api.markEdited(master());status(grid,'보충 설정을 붙여넣었습니다. 의미 매핑/결과 판정 참조는 별도로 재연결하세요.');dialog.close();},'schemaPaste');});}catch(e){status(grid,e.message);}}
    });
    if(tool&&editable){const bean=master()?.querySelector('[data-row-field="서비스 빈"]');if(bean)new MutationObserver(()=>{const now=bean.dataset.rowValue;if(now!==observedBean){observedBean=now;toolReady=false;set(master(),'구현 상태','리로드 필요');for(const grid of grids())status(grid,'서비스 빈이 바뀌었습니다. 상단 서비스 빈 정보 리로드로 최신 명세를 확인하세요.');}}).observe(bean,{attributes:true,attributeFilter:['data-row-value']});}
  }
}
