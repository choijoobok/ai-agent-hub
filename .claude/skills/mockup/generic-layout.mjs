/** 기존 화면도 같은 요건서 기반 설명 서식으로 표시한다. 미선언 CRUD를 추정하지 않는다. */
import {escapeHtml as esc, tableCell as cell, columnExplanation, actionExplanation, searchExplanation, surfaceOf} from './presentation.mjs';
import {chrome,standardButton,inlineEditor,stateHeads,stateCells,deleteHead,deleteCell,editableColumn,switchColumn,switchCell} from './grid-chrome.mjs';
export const areaTitle = table => table.heading.replace(/\*+/g,'').replace(/^\d+(?:-\d+[a-z]?)?\.?\s*/, '').replace(/^(?:마스터(?: 그리드)?|하단|그리드|좌|우|하|디테일 탭 \d+|정의 모달)\s*—\s*/, '').replace(/\s*—\s*(폼|그리드)$/, '').replace(/\s*\([^)]*\)\s*$/,'');
export function genericScreen(title, model, figure, {annotator, manualKey, compact=false, surfaceName}) {
  const n=annotator(figure.key);
  const badge=(description,key)=>n.badge(description,key);
  const action=(name,scope)=>{
    const table=model.actionTables.find(t=>t.rows.some(r=>cell(t,r,t.header[0])===name));
    const row=table?.rows.find(r=>cell(table,r,table.header[0])===name);
    const surface=surfaceOf(model,scope);
    const desc=row?actionExplanation(table,row,scope):`<b>${esc(scope)} › ${esc(name)}</b><p>${esc(surface?.workflow||'요건서의 버튼 동작을 따른다.')}</p>`;
    return `<button type="button" class="btn ghost sm">${esc(/추가$|^신규$/.test(name)?'추가':name)}${badge(desc+`<p>조회: ${esc(surface?.trigger||'화면 조회 조건')} / 저장: ${esc(surface?.save||'해당 없음')}</p>`,`action:${scope}:${name}`)}</button>`;
  };
  const renderArea=table=>{
    const name=areaTitle(table), surface=surfaceOf(model,name);
    const form=/폼|설정/.test(surface?.kind??'');
    const writable=!table.readonly&&table.rows.some(row=>editableColumn(cell(table,row,'편집')));
    const createDeclared=(surface?.buttons??[]).some(name=>/추가$|^신규$/.test(name));
    const deleteDeclared=(surface?.buttons??[]).some(name=>/삭제$|제거$/.test(name));
    const locked=['readonly','denied','saving','unknown','conflict'].includes(figure.key);
    const showState=writable&&!locked;
    const multiple=model.selectionModes?.[name]==='다중';
    const showDelete=showState&&deleteDeclared;
    const toolbar=(surface?.buttons??[]).filter(b=>!/^조회$|^저장$|추가$|삭제$|제거$|^신규$/.test(b)).map(b=>action(b,name)).join('');
    const controls=chrome({title:name,count:'1건',read:standardButton('조회','read',['saving','unknown','denied'].includes(figure.key)),add:writable&&createDeclared?standardButton('추가','add',locked):'',save:writable?standardButton('저장','save',locked):'',actions:toolbar});
    const columns=table.rows.map(row=>({row,label:cell(table,row,table.header[0]),note:badge(columnExplanation(table,row),`column:${name}:${cell(table,row,table.header[0])}`)}));
    if(form)return `${controls}<div class="form-fields">${columns.map(({row,label,note})=>{
      const mode=cell(table,row,'입력방식'),editable=cell(table,row,'편집');
      const ro=/읽기|불가|자동|—/.test(editable),value=cell(table,row,'예시');
      const input=switchColumn(mode)?switchCell(value,label,{configurable:mode!=='스위치',editable:writable&&!locked&&editableColumn(editable),description:cell(table,row,'설명')}):/선택|셀렉트|콤보/.test(mode)?`<select ${ro?'disabled':''}><option>${esc(value||'선택')}</option></select>`:/장문|텍스트영역/.test(mode)?`<textarea ${ro?'readonly':''}>${esc(value)}</textarea>`:`<input type="${/password|비밀번호/.test(mode)?'password':'text'}" value="${esc(value)}" ${ro?'readonly':''}>`;
      return `<div class="f${ro?' ro':''}"${switchColumn(mode)?` data-row-field="${esc(label)}" data-row-value="${esc(value)}"`:''}><label data-field-label="${esc(label)}">${esc(label)}${note}</label>${input}</div>`;
    }).join('')}</div>`;
    const renderRow=(isNew=false)=>`<tr data-grid-row-state="" data-row-context="[]">${stateCells(showState,['saving','unknown','denied'].includes(figure.key),multiple)}${columns.map(({row,label})=>{
      const value=cell(table,row,'예시')||'—',mode=cell(table,row,'편집'),edit=writable&&!locked&&editableColumn(mode)&&(!/신규/.test(mode)||isNew);
      const inputMode=cell(table,row,'입력방식');
      const content=switchColumn(inputMode)?switchCell(value,label,{configurable:inputMode!=='스위치',editable:edit,description:cell(table,row,'설명')}):edit?inlineEditor(value,`<input data-edit-value value="${esc(value)}" aria-label="${esc(label)}">`):esc(value);
      return `<td data-row-field="${esc(label)}" data-row-value="${esc(value)}">${content}</td>`;
    }).join('')}${deleteCell(showDelete,locked)}</tr>`;
    return `<div data-grid-view="${esc(figure.key+'-'+model.columns.indexOf(table))}"${table.auditPolicy?` data-grid-audit="${esc(table.auditPolicy.kind)}"`:''} data-grid-selection="${multiple?'multiple':'single'}" data-grid-role="${table===model.columns[0]?'master':'detail'}">${controls}${writable&&!locked&&createDeclared?`<template data-row-template>${renderRow(true)}</template>`:''}<div class="grid" style="overflow:auto"><table><thead><tr>${stateHeads(showState,multiple)}${columns.map(({label,note})=>`<th style="min-width:110px">${esc(label)}${note}</th>`).join('')}${deleteHead(showDelete)}</tr></thead><tbody>${renderRow()}</tbody></table></div><div class="pg">총 1건 · 1 페이지</div></div>`;
  };
  const first=model.columns[0];
  const firstSurface=surfaceOf(model,areaTitle(first||{heading:''}));
  const selected=surfaceName?model.columns.find(t=>areaTitle(t)===surfaceName):firstSurface?.kind.startsWith('탭')?first:model.columns.slice(1).find(t=>!/(모달|단계|폼|설정)/.test(surfaceOf(model,areaTitle(t))?.kind??''));
  const search=compact?'':model.search.map(r=>`<label>${esc(cell(model.searchTable,r,model.searchTable.header[0]))}${badge(searchExplanation(model.searchTable,r),`search:${r[0]}`)}</label>${/선택|셀렉트|코드/.test(cell(model.searchTable,r,'입력방식'))?'<select><option>전체</option></select>':'<input placeholder="조회 조건 입력">'}`).join('');
  const tabs=model.surfaces.filter(s=>/^탭(?: 폼)?$/.test(s.kind));
  const interactive=!compact&&tabs.length>0;
  const selectedTab=tabs.find(s=>s.name===areaTitle(selected||first||{heading:''}))?.name??tabs[0]?.name;
  const tabBar=tabs.length>1&&!compact?`<div class="tabs" role="tablist" style="flex-wrap:wrap">${tabs.map(s=>`<button type="button" role="tab" aria-selected="${s.name===selectedTab}" data-detail-tab="${esc(s.name)}" class="btn ghost sm ${s.name===selectedTab?'on':''}">${esc(s.name)}${badge(`<b>${esc(s.name)} 탭</b><p>${esc(s.purpose)}</p>`,`tab:${s.name}`)}</button>`).join('')}</div>`:'';
  const attached=table=>model.columns.filter(t=>areaTitle(t).startsWith(areaTitle(table)+' / ')).map(renderArea).join('');
  const secondary=selected&&selected!==first?[selected,...model.columns.slice(model.columns.indexOf(selected)+1).filter(t=>surfaceOf(model,areaTitle(t))?.kind==='영역')]:[];
  const panels=interactive?tabs.map(s=>{
    const table=model.columns.find(t=>areaTitle(t)===s.name);
    if(!table)throw new Error(`목업 탭 '${s.name}'의 컬럼 표가 §4에 없다`);
    return `<div data-detail-panel="${esc(s.name)}"${s.name===selectedTab?'':' hidden'}>${renderArea(table)}${attached(table)}</div>`;
  }).join(''):'';
  const extra=interactive?panels:secondary.map(t=>renderArea(t)+attached(t)).join('');
  const pair=model.vertical?`${renderArea(first||{heading:'',rows:[]})}${extra?`<div class="split"><span class="hnd">⋮⋮⋮</span></div>${tabBar}${extra}`:''}`:`<div class="splitrow"><div class="pane">${first?renderArea(first):''}</div>${extra?`<div class="split v"><span class="hnd">⋮</span></div><div class="pane">${tabBar}${extra}</div>`:''}</div>`;
  const content=compact?(selected?renderArea(selected)+attached(selected):''):first?firstSurface?.kind==='설정'?model.columns.map(renderArea).join(''):firstSurface?.kind.startsWith('탭')?tabBar+(interactive?panels:renderArea(first)+attached(first)):pair:`<p>${esc(model.overview||'요건서의 화면 구성을 확인한다.')}</p>`;
  const global=!first?model.actions.filter(r=>!/^\(/.test(r[0])).map(r=>action(r[0],'화면')).join(''):'';
  return `<div class="screen leaf" id="${esc(figure.key)}"${interactive?' data-interactive-tabs':''}${manualKey?` data-manual="${esc(figure.key)}"`:''}><div class="bar">${esc(title)} · ${esc(figure.caption)} · 검토용 예시</div><div class="body">${search?`<div class="sf">${search}<button type="button" class="btn ghost sm" data-search-reset${['saving','unknown','denied'].includes(figure.key)?' disabled':''}>초기화</button></div>`:''}${content}${global?`<div class="actions">${global}</div>`:""}</div></div>`;
}
