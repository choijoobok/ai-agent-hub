/** 요건서에 명시한 상태·상세 폼을 공통 템플릿의 어휘로 렌더링한다. */
import {snapshotView} from './snapshot-view.mjs';
import {addChoiceFor,lookupAddDialog} from './add-choice.mjs';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {columnExplanation, actionExplanation, searchExplanation, surfaceOf, surfaceIntroduction} from './presentation.mjs';
import {chrome,standardButton,inlineEditor,stateHeads,stateCells,deleteHead,deleteCell,systemWidth,editableColumn,switchColumn,switchState,switchCell} from './grid-chrome.mjs';
const controls = readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-controls.js', import.meta.url), 'utf8');
const math = readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-math.js', import.meta.url), 'utf8');
const escape = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const titleOf = (table) => table.heading.replace(/^\d+(?:-\d+)?\.?\s*/, '').replace(/\s*—\s*(폼|그리드)$/, '');
const tabOf = (table) => titleOf(table).split(' / ')[0];
const cell = (table, row, header) => row[table.header.findIndex((h) => h.replace(/\*/g, '') === header)] ?? '';

function selectControl(id,label,value,examples,referenceMap='') {
  const references=referenceMap&&referenceMap!=='—'?JSON.parse(referenceMap):{};
  const labels=[...new Set(examples.split('·').map(s=>s.trim()).filter(s=>s&&s!=='—'))];
  const options=['',...labels].map(text=>({text,value:String(references[text]??text)}));
  const selected=String(references[value]??value);
  const missing=selected&&!options.some(option=>option.value===selected);
  return `<select data-edit-value id="${id}" aria-label="${escape(label)}">${options.map(option=>`<option value="${escape(option.value)}"${option.value===selected?' selected':''}>${escape(option.text||'미지정')}</option>`).join('')}${missing?`<option value="${escape(selected)}" selected disabled>${escape(value)} (지원 확인 필요)</option>`:''}</select>`;
}

function searchCombo(id, label, value, examples, locked, parent = '', mapping = '', multiple = false, referenceMap = '') {
  const references=referenceMap&&referenceMap!=='—'?JSON.parse(referenceMap):{};
  const values = [...new Set(examples.split('·').map((s) => s.trim()).filter((s) => s && s !== '—'))];
  const options = ['', ...values].map((v) => `<option value="${escape(references[v]??v)}"${v === value || references[v] === value ? ' selected' : ''}>${escape(v || '미지정')}</option>`).join('');
  const missing = value && !values.includes(value) ? `<option value="${escape(value)}" selected disabled>${escape(value)} (지원 확인 필요)</option>` : '';
  return `<div data-search-combo${parent ? ` data-parent-field="${escape(parent)}" data-candidate-map="${escape(mapping)}"` : ''}><input type="search" aria-label="${escape(label)} 검색" placeholder="검색어로 후보 좁히기" data-combo-filter ${locked ? 'readonly' : ''}>
    <select id="${id}" aria-label="${escape(label)}" size="4" ${multiple?'multiple':''} data-combo-select data-edit-value data-field-name="${escape(label)}" ${locked ? 'disabled' : ''}>${options}${missing}</select>
    <div class="hint" data-combo-value>현재 선택: ${escape(value || '미지정')}</div><div class="hint" data-combo-empty hidden>일치하는 후보가 없습니다.</div></div>`;
}

/** 검색 문자열은 선택값을 변경하지 않으며 실제 선택 이벤트만 현재 값을 표시한다. */
export function searchComboScript() {
  return `<script>${math}\n${controls}\nbindDeclaredMockup(document);
function openDeclaredCell() {
  document.querySelectorAll('[data-auto-editor]').forEach(function(editor){editor.open=editor.closest('.screen').id===location.hash.slice(1);});
}
window.addEventListener('hashchange',openDeclaredCell);
openDeclaredCell();
</script>`;
}

export function declaredFigures(table, plain) {
  if (!table) return [];
  const figures = table.rows.map((r) => ({
    key: plain(cell(table, r, '그림 키')), caption: plain(cell(table, r, '상태')),
    detail: plain(cell(table, r, '상세 영역')), notice: plain(cell(table, r, '안내')),
    buttons: plain(cell(table, r, '허용 버튼')).split('·').filter((x) => x && x !== '없음'),
    disabledButtons: plain(cell(table, r, '비활성 버튼')).split('·').map(x=>x.trim()).filter(x=>x&&x!=='없음'),
    editable: plain(cell(table, r, '편집')) === '가능',
    examples: Object.fromEntries(plain(cell(table, r, '예시 변경')).split(';').filter((p) => p.includes('=')).map((p) => {
      const at = p.indexOf('=');
      return [p.slice(0, at).trim(), p.slice(at + 1).trim()];
    })),
    cellEditor: plain(cell(table, r, '셀 편집')),
    masterValue: plain(cell(table,r,'선택 대상')),
    emptyGrids: plain(cell(table,r,'빈 그리드')).split(';').map(x=>x.trim()).filter(x=>x&&x!=='없음'),
  }));
  if (figures.some((f) => !/^[a-z0-9][\w-]*$/.test(f.key)) || new Set(figures.map((f) => f.key)).size !== figures.length) {
    throw new Error('목업 상태의 그림 키는 중복 없는 영문·숫자·하이픈이어야 한다');
  }
  return figures;
}

export function declaredScreen(title, model, figure, { annotator, plain, searchControl, manualKey, compact = false }) {
  if ((figure.emptyGrids ?? []).some(name=>!model.columns.some(t=>titleOf(t)===name&&!/—\s*폼$/.test(t.heading)))) throw new Error('목업 상태의 빈 그리드 이름이 §4에 없다');
  const n = annotator(figure.key);
  const interactive = model.interactive && !compact && figure.detail !== '없음';
  const supportsValueMode = scope => {
    const required = scope==='코드 값'?'INLINE':['코드 조건','코드 바인딩','조회 코드 값'].includes(scope)?'CODE':null;
    if(!required)return true;
    const columns=(model.rowExamples?.rows??[]).filter(r=>plain(r[0])==='컬럼 정의').map(r=>JSON.parse(r[1]));
    return columns.some(r=>(figure.examples['컬럼명']===r['컬럼명']&&figure.examples['값 방식']?figure.examples['값 방식']:r['값 방식'])===required);
  };
  const badge = (label, detail = '', key = label) => n.badge(`<b>${escape(label)}</b> — ${escape(detail || label)}`, key);
  const button = (name, scope = '화면') => {
    if (name === '승인 요청' && figure.buttons.includes('저장') && !figure.buttons.includes('승인 요청')) name = '저장';
    const actionTable = model.actionTables?.find(t=>t.rows.some(r=>plain(r[0])===name));
    const row = actionTable?.rows.find(r=>plain(r[0])===name) ?? model.actions.find((r) => plain(r[0]) === name);
    if (!row) throw new Error(`목업 상태 ${figure.key}의 버튼 '${name}'이 §5에 없다`);
    const surface = surfaceOf(model,scope);
    const read = /조회|목록|열람|확인|상세|사용처|검증 결과|관리측 후보 열기/.test(name) || (actionTable && /READ/.test(plain(cell(actionTable,row,'액션'))));
    const locked = ['saving','unknown','conflict','denied'].includes(figure.key);
    const modeLocked=/추가|제거/.test(name)&&!supportsValueMode(scope);
    const localRequestEdit = model.requestScreen && figure.editable && /추가$|제거$/.test(name) && (model.addRowTabs??[]).includes(scope);
    const disabled = (model.strictButtons && !figure.buttons.includes(name) && !localRequestEdit) || figure.disabledButtons?.includes(name) || modeLocked || (locked && !figure.buttons.includes(name)) || (!figure.editable && !read && !figure.buttons.includes(name)) || surface?.disabled?.includes(name) || name === '수동 샘플';
    const add = !disabled && /추가$/.test(name) && (model.addRowTabs??[]).includes(scope);
    const remove = !disabled && /제거$/.test(name) && (model.addRowTabs??[]).includes(scope);
    const description = actionTable ? actionExplanation(actionTable,row,title) : `<b>${escape(name)}</b> — ${escape(plain(row[5]))}`;
    const locations=model.surfaces?.filter(s=>s.buttons.includes(name))??[];
    const saveScopes=[...new Set(locations.map(s=>s.save))];
    const note=n.badge(description+(locations.length?`<p>배치: ${escape(locations.map(s=>s.name).join(' · '))}</p><p>저장 범위: ${escape(saveScopes.join(' / '))}</p>`:''),`action:${name}`);
    const command=(model.commandFields?.rows??[]).some(r=>plain(r[0])===name);
    const destination=(model.figureNavigation?.rows??[]).find(r=>plain(r[0])===name);
    const displayName=/추가$|^신규$/.test(name)?'추가':actionTable ? plain(cell(actionTable,row,'표시명'))||name : name;
    const nav=model.screenNavigation?.rows.find(r=>plain(r[0])===name);
    if(nav&&!disabled){
      const href=plain(nav[1]);
      if(!/^(?:\.\.\/)*[a-z0-9/-]+\/mockup\.html(?:\?[a-zA-Z0-9=&_-]+)?(?:#main)?$/.test(href))throw new Error('화면 이동은 목업 상대 경로와 명시 문맥만 허용한다');
      const destination=path.posix.normalize(path.posix.join(model.screenPath??'',href.split('?')[0])).replace(/\/mockup\.html$/,'');
      const requestId=model.requestRoutes?.find(s=>s.route===`/${destination}`)?.id??'';
      return `<span class="control-annotation"><a class="btn ghost sm" href="${escape(href)}" data-request-navigation data-request-screen="${escape(requestId)}">${escape(displayName)}</a>${note}</span>`;
    }
    return `<span class="control-annotation"><button type="button" class="btn ghost sm"${disabled?' disabled':''}${!read?' data-edit-action':''}${model.savedActions?.includes(name)?' data-saved-action':''}${model.independentButtons?.includes(name)?' data-selection-independent':''}${modeLocked?' title="해당 값 방식의 등록 컬럼이 없어 사용할 수 없습니다"':''}${add?' data-add-demo-row':''}${remove?' data-remove-demo-row':''}${command?` data-open-command="${escape(name)}"`:''}${destination?` data-open-figure="${escape(plain(destination[1]))}"`:''}>${escape(displayName)}</button>${note}</span>`;
  };
  const buttons = () => compact ? '' : figure.buttons.filter((name) => name !== '엑셀 다운로드').map(name=>button(name)).join('');
  const details = model.columns.slice(1);
  const selected = details.filter((t) => tabOf(t) === figure.detail);
  if (figure.detail !== '없음' && !selected.length) throw new Error(`목업 상태 ${figure.key}의 상세 영역 '${figure.detail}'이 §4에 없다`);
  const singleGrid = model.columns.length === 1;
  const showData = singleGrid || figure.detail !== '없음';
  if (figure.detail === '없음' && figure.buttons.length === 0) {
    return `<div class="screen leaf" id="${escape(figure.key)}"${manualKey ? ` data-manual="${escape(figure.key)}"` : ''}><div class="bar">${escape(title)} · ${escape(figure.caption)}</div><div class="body"><div class="notes">${escape(figure.notice)}</div></div></div>\n${n.render()}`;
  }
  const example = (table, row) => Object.hasOwn(figure.examples, plain(row[0])) ? figure.examples[plain(row[0])] : plain(cell(table, row, '예시'));
  const configured = (table) => {
    const rows = (model.rowExamples?.rows ?? []).filter(r => plain(r[0]) === titleOf(table));
    const matches = rows.filter(r => plain(r[2]).split('·').includes(figure.stateKey ?? figure.key));
    return (matches.length ? matches : rows.filter(r => !plain(r[2]) || plain(r[2]) === '—')).map(r => JSON.parse(r[1]));
  };
  const exampleContext=record=>!model.masterLink||!figure.masterValue||String(record['문맥']??record[model.masterLink]??'').split(',').includes(figure.masterValue);
  const popup = (name, id, locked) => (model.popups?.rows ?? []).filter(r => plain(r[0]) === name).map((r, i) => {
    const label = plain(r[1]), mode = plain(r[2]), value = plain(r[3]), candidates = plain(r[4]);
    const parent = plain(r[5]) === '—' ? '' : plain(r[5]);
    if (parent) JSON.parse(r[6]);
    const control = /검색콤보|팝업 선택|선택/.test(mode) ? searchCombo(`${id}-popup-${i}`, label, value, candidates, locked, parent, r[6])
      : `<input data-edit-value aria-label="${escape(label)}" value="${escape(value)}" ${locked ? 'readonly' : ''}>`;
    return `<label>${escape(label)}${badge(label,`설정 창: ${name}. ${mode}. 선택 후보: ${candidates}. ${parent?parent+' 선택 후 그 대상의 후보만 선택한다.':'입력은 선택 적용 후 편집본에 반영한다.'}`,`popup:${name}:${label}`)}</label>${control}`;
  }).join('');
  const tableView = (table, empty = false) => {
    const label = titleOf(table);
    const contextEmpty = figure.emptyGrids?.includes(label);
    empty = empty || contextEmpty;
    const gridId = `${figure.key}-grid-${model.columns.indexOf(table)}`;
    const addChoice=addChoiceFor(model,label,figure);
    const writable=!table.readonly&&table.rows.some(r=>editableColumn(plain(cell(table,r,'편집'))));
    const showState=writable&&figure.editable;
    const multiple=model.selectionModes?.[label]==='다중';
    const deletionDeclared=(model.addRowTabs??[]).includes(label)||(surfaceOf(model,label)?.buttons??[]).some(name=>/삭제$|제거$/.test(name));
    const showDelete=showState&&deletionDeclared;
    const optional = (r) => plain(cell(table,r,'기본 표시')) === '선택 표시';
    const hidden = (r) => optional(r) ? ' hidden' : '';
    const width = r => Number.parseInt(cell(table, r, '폭/정렬'), 10) || 120;
    const lastVisible=table.rows.findLastIndex(r=>!optional(r));
    const heads = stateHeads(showState,multiple)+table.rows.map((r,i) => `<th data-grid-col="${i}" data-grid-base-width="${width(r)}"${hidden(r)} style="white-space:normal;width:${i===lastVisible?'auto':width(r)+'px'}">${escape(plain(r[0]))}${n.badge(columnExplanation(table,r),`column:${label}:${plain(r[0])}`)}</th>`).join('')+deleteHead(showDelete);
    const examples = configured(table).filter(record => !compact || exampleContext(record));
    if (compact && model.masterLink && figure.masterValue && examples.length === 0) empty = true;
    const rows = examples.length ? examples : [{}];
    const editableNow=(r,record,isNew=false)=>{
      if(!figure.editable||table.readonly||!editableColumn(plain(cell(table,r,'편집'))))return false;
      if(/신규/.test(plain(cell(table,r,'편집')))&&!isNew&&figure.key!=='new')return false;
      const condition=plain(cell(table,r,'편집 조건'));
      return !condition||condition==='—'||condition.split('&&').every(rule=>{
        const [field,...values]=rule.trim().split('=');
        if(!values.length)return field===(figure.stateKey??figure.key);
        const base=model.columns.flatMap(t=>t.rows.map(row=>({table:t,row}))).find(x=>plain(x.row[0])===field);
        const actual=(exampleContext(record)?figure.examples[field]:undefined)??record[field]??(base?example(base.table,base.row):'');
        return values.join('=').split('·').includes(String(actual));
      });
    };
    const editableHere=rows.some(record=>table.rows.some(r=>editableNow(r,record)));
    const renderRow = (record, rowIndex) => {
    const valueOf = r => exampleContext(record)&&Object.hasOwn(figure.examples,plain(r[0])) && (!figure.examples['컬럼명'] || figure.examples['컬럼명']===record['컬럼명']) ? figure.examples[plain(r[0])] : Object.hasOwn(record,plain(r[0])) ? String(record[plain(r[0])]) : exampleContext(record)?example(table,r):plain(cell(table,r,'예시'));
    const vals = table.rows.map((r,i) => {
      const editMode = plain(cell(table, r, '편집'));
      const condition=plain(cell(table,r,'편집 조건'));
      const rowRules = condition && condition !== '—' ? condition.split('&&').map(rule => {
        const [field,...values]=rule.trim().split('=');
        return values.length && table.rows.some(row=>plain(row[0])===field)
          ? {field, values:values.join('=').split('·')} : null;
      }) : [];
      const rowConditional = rowRules.length > 0 && rowRules.every(Boolean);
      const conditionLocked=condition && condition!=='—' && condition.split('&&').some(rule=>{
        const [field,...values]=rule.trim().split('=');
        if(!values.length)return field!==(figure.stateKey??figure.key);
        const base=model.columns.flatMap(t=>t.rows.map(row=>({table:t,row}))).find(x=>plain(x.row[0])===field);
        const actual=(exampleContext(record)?figure.examples[field]:undefined)??record[field]??(base?example(base.table,base.row):'');
        return !values.join('=').split('·').includes(String(actual));
      });
      const editable = (!conditionLocked || rowConditional) && !table.readonly && figure.editable && (/가능|조건부/.test(editMode) || (/신규/.test(editMode) && (figure.key==='new'||rowIndex>=rows.length)));
      const choiceCandidates=addChoice?.mode==='기준정보 콤보'&&plain(r[0])===addChoice.key?addChoice.records.filter(v=>v['선택 가능']!==false).map(v=>({label:addChoice.columns.map(k=>String(v[k]??'')).join(' / '),key:v[addChoice.key]})):null;
      const referenceMap=choiceCandidates?JSON.stringify(Object.fromEntries(choiceCandidates.map(v=>[v.label,v.key]))):plain(cell(table,r,'후보 값 매핑'));
      const references=referenceMap&&referenceMap!=='—'?JSON.parse(referenceMap):{};
      const value = Object.entries(references).find(([,stored])=>stored===valueOf(r))?.[0] || valueOf(r) || '—';
      const mode=plain(cell(table,r,'입력방식'));
      const parentField=plain(cell(table,r,'상위 선택'));
      const candidateMap=plain(cell(table,r,'후보 매핑'));
      const rowParent=parentField && parentField!=='—' ? parentField : '';
      if(rowParent && !table.rows.some(row=>plain(row[0])===rowParent))throw new Error('상위 선택 필드가 같은 그리드에 없다');
      const rowCandidates=choiceCandidates?choiceCandidates.map(v=>v.label).join('·'):rowParent ? (JSON.parse(candidateMap)[valueOf(table.rows.find(row=>plain(row[0])===rowParent))]??[]).join('·') : plain(cell(table,r,'후보 예시'));
      let content=escape(value),switchKnown=true;
      const isSnapshot=/스냅샷 뷰어/.test(mode);
      const isLargeText=/셀 위 텍스트영역/.test(mode);
      if(switchColumn(mode)){
        if(mode==='스위치'&&editable)throw new Error('조회 결과 스위치는 읽기 전용으로 선언한다');
        switchKnown=switchState(valueOf(r))!==null;
        content=switchCell(valueOf(r),plain(r[0]),{configurable:mode!=='스위치',editable,locked:conditionLocked,description:plain(cell(table,r,'설명'))});
      }else if(isLargeText){
        const controlId=`${gridId}-row-${rowIndex}-cell-${i}`;
        content=`<details data-cell-editor data-cell-large${editable?' data-cell-inline':' data-cell-large-view'}><summary title="${editable?'더블 클릭으로 셀 위 텍스트영역 편집':'전체 내용 보기'}">${escape(value)}</summary><textarea data-edit-value id="${controlId}" aria-label="${escape(plain(r[0]))}" rows="8" cols="60" maxlength="2000"${editable?'':' readonly'}>${escape(valueOf(r)||'')}</textarea></details>`;
      }else if(editable || /장문/.test(mode) || isSnapshot) {
        const controlId=`${gridId}-row-${rowIndex}-cell-${i}`;
        const labels=Object.fromEntries(model.columns.flatMap(t=>t.rows.map(row=>[plain(cell(t,row,'필드명')).replace(/^snapshot\s*의\s*|^snapshot\.|^values\./,'').replace(/\[\]$/,''),plain(row[0]).replace(/^반영 후 /,'')])).filter(([key])=>/^[a-zA-Z]\w*$/.test(key)));
        const formatRow=table.rows.find(row=>plain(cell(table,row,'필드명'))==='snapshotVersion');
        const control=isSnapshot?snapshotView(valueOf(r)||'',Number(formatRow?valueOf(formatRow):0),labels)
          : /설정 팝업/.test(mode) ? popup(plain(r[0]), controlId, !editable)
          : /검색콤보|팝업 선택/.test(mode) ? searchCombo(controlId,plain(r[0]),valueOf(r)||'',rowCandidates,!editable,rowParent,rowParent?candidateMap:'',/다중/.test(mode),referenceMap)
          : /장문/.test(mode) ? `<textarea data-edit-value id="${controlId}" rows="5" ${editable?'':'readonly'}>${escape(valueOf(r)||'')}</textarea>`
          : /선택|셀렉트/.test(mode) ? selectControl(controlId,plain(r[0]),valueOf(r)||'',rowCandidates,referenceMap)
          : `<input data-edit-value id="${controlId}" aria-label="${escape(plain(r[0]))}" value="${escape(valueOf(r)||'')}" ${editable?'':'readonly'}>`;
        const inline=editable&&!/장문|설정 팝업|검색콤보|팝업 선택|스냅샷/.test(mode);
        content=inline?inlineEditor(value,control):`<details data-cell-editor${editable?' data-cell-popup':''}${isSnapshot?' data-snapshot-viewer':''}${figure.cellEditor===plain(r[0])&&rowIndex===0?' data-auto-editor':''}><summary title="${editable?'더블 클릭으로 셀 편집':isSnapshot?'전체 스냅샷 보기':escape(value)}" style="overflow:hidden;text-overflow:ellipsis">${isSnapshot?'보기':escape(value)+(editable?' ✎':' 보기')}</summary><div class="modal" style="position:fixed;top:8%;left:50%;transform:translateX(-50%);width:min(${isSnapshot?'800':'520'}px,90vw);z-index:20;white-space:normal"><div class="mh">${escape(plain(r[0]))}<button type="button" class="btn ghost sm" data-close-editor>닫기${badge('닫기','셀 상세/설정 창을 닫는다. 후보 전체 저장은 수행하지 않는다.','common:close-editor')}</button></div><div class="mb${isSnapshot?'':' f'}" style="max-height:75vh;overflow:auto">${control}${editable?`<button type="button" class="btn sm" data-apply-cell>선택 적용${badge('선택 적용','설정 창의 입력을 로컬 후보 셀에 반영한다. 서버 저장은 별도 저장 버튼으로 수행한다.','common:apply-cell')}</button>`:''}</div></div></details>`;
      }
      const target = plain(cell(table, r, '이동 탭'));
      if (target && target !== '—' && value !== '미확인') {
        if (!details.some(t=>tabOf(t)===target)) throw new Error('이동 탭이 §4에 없다');
        const context = record['컬럼명'] || record['문맥'] || '';
        content = `<button type="button" class="btn ghost sm" data-open-tab="${escape(target)}" data-context="${escape(context)}">${escape(value)}</button>`;
      }
      if(model.requestScreen && plain(r[0])==='요청번호')content=`<span data-request-number>${content}</span>`;
      if(rowConditional && editable && !switchColumn(mode))content=`<span data-locked-value${conditionLocked?'':' hidden'}>—</span>${conditionLocked?content.replace('<details data-cell-editor','<details hidden data-cell-editor'):content}`;
      const storedValue=referenceMap&&referenceMap!=='—'?(JSON.parse(referenceMap)[valueOf(r)]??valueOf(r)):valueOf(r);
      return `<td data-grid-col="${i}" data-row-field="${escape(plain(r[0]))}" data-row-value="${escape(storedValue||'')}"${(model.calculations?.some(rule=>rule.area===label&&rule.target===plain(r[0]))||model.allocationValidity?.field===plain(r[0]))?' data-calculated-value':''}${rowConditional&&editable?` data-edit-rule="${escape(JSON.stringify(rowRules))}"`:''}${hidden(r)}${editable && !conditionLocked && switchKnown ? ' class="edit"' : ''} style="overflow:hidden;text-overflow:ellipsis" title="${isSnapshot?'전체 스냅샷 보기':escape(value)}">${content}</td>`;
    }).join('');
    const protection = model.removeGuardField ? String(record[model.removeGuardField]??'').trim() : '';
    const protectedRow = protection && !['—','없음','0'].includes(protection);
    const rowEditable=table.rows.some(r=>editableNow(r,record,rowIndex>=rows.length));
    const rowDeleteAllowed=(model.addRowTabs??[]).includes(label)||(surfaceOf(model,label)?.buttons??[]).some(name=>/삭제$|제거$/.test(name));
    const [lockField,lockValue]=(model.masterEditRule??'').split('=');
    return `<tr class="${!multiple&&rowIndex===0?'sel':''}" data-grid-row-state=""${table===model.columns[0]&&model.masterLink?` data-master-locked="${!figure.editable||Boolean(model.masterEditRule&&String(record[lockField])!==lockValue)}"`:''}${protectedRow?` data-remove-blocked="${escape(protection)}"`:''} data-row-context="${escape(JSON.stringify(String(record['문맥'] || record['컬럼명'] || '').split(',')))}">${stateCells(showState,['saving','unknown','denied'].includes(figure.key),multiple,rowIndex)}${vals}${deleteCell(showDelete,!rowEditable||!rowDeleteAllowed,protectedRow?model.removeGuardField+': '+protection+' — 참조/보존 조건으로 삭제 불가':'')}</tr>`;
    };
    const body = empty ? `<tr><td class="empty" colspan="${table.rows.length+1+(multiple?1:0)+(showState?1:0)+(showDelete?1:0)}">${escape(contextEmpty?'선택한 문맥에 해당하지 않는 목록입니다.':figure.notice)}</td></tr>` : rows.map(renderRow).join('');
    const excel=figure.buttons.includes('엑셀 다운로드')?`<details data-excel-menu><summary>엑셀 다운로드 ▾${badge('엑셀 다운로드','현재 페이지 또는 전체를 선택한다. 현재 표시한 열과 순서, 반출 권한을 적용하며 숨긴 열은 자동 포함하지 않는다.','common:excel')}</summary><span>현재 페이지 · 전체</span></details>`:'';
    const settings=`<details data-grid-settings><summary>그리드 설정${badge('그리드 설정','표시할 열을 선택한다. 선택 표시 열도 여기에서 켜며 저장 데이터 자체는 변경하지 않는다.','common:grid-settings')}</summary><div class="notes">${table.rows.map((r,i)=>`<label style="display:block"><input type="checkbox" data-grid-column="${i}" ${optional(r)?'':'checked'}>${escape(plain(r[0]))}</label>`).join('')}</div></details>`;
    const commandDeclared=name=>(model.commandFields?.rows??[]).some(row=>plain(row[0])===name);
    const rowAction=name=>(/추가$|제거$|^신규$/.test(name))&&!commandDeclared(name);
    const createDeclared=(model.addRowTabs??[]).includes(label)||(surfaceOf(model,label)?.buttons??[]).some(name=>/추가$/.test(name)&&rowAction(name))||(table===model.columns[0]&&figure.buttons.includes('신규'));
    const allowedAdd=!model.strictButtons||figure.buttons.some(name=>/추가$|^신규$/.test(name)&&(!surfaceOf(model,label)||surfaceOf(model,label).buttons.includes(name)))||(model.requestScreen&&figure.editable&&(model.addRowTabs??[]).includes(label));
    const canAdd=createDeclared&&allowedAdd&&(editableHere||table.rows.some(r=>editableNow(r,model.masterEditRule?Object.fromEntries([model.masterEditRule.split('=')]):{},true)))&&supportsValueMode(label)&&writable&&!figure.disabledButtons?.some(name=>/추가$/.test(name))&&(!addChoice||addChoice.mode==='조회 팝업'||(addChoice.state==='정상'&&addChoice.records.length>0));
    const surface=surfaceOf(model,label);
    const commandAdd=(surface?.buttons??[]).find(name=>/추가$/.test(name)&&commandDeclared(name));
    const surfaceButtons=(surface?.buttons??[]).filter(name=>!/^조회$|^저장$|^엑셀 다운로드$/.test(name)&&!rowAction(name)&&name!==commandAdd);
    const globalButtons=table===model.columns[0]&&!compact?figure.buttons.filter(name=>!/^조회$|^저장$|^엑셀 다운로드$/.test(name)&&!rowAction(name)&&!model.surfaces?.some(s=>s.buttons.includes(name))):[];
    const saveButtons=surface?.buttons??figure.buttons;
    const approvalSave=saveButtons.includes('승인 요청')&&!saveButtons.includes('저장')&&figure.buttons.includes('승인 요청');
    const actions=[...new Set([...surfaceButtons,...globalButtons])].filter(name=>name!=='승인 요청'||(figure.buttons.includes('승인 요청')&&!approvalSave)).map(name=>button(name,label)).join('');
    const approvalControl=approvalSave?'<span data-grid-save data-native-save>'+button('승인 요청',label)+'</span>':'';
    const newNavigation=table===model.columns[0]&&figure.buttons.includes('신규')&&model.figureNavigation?.rows.some(row=>plain(row[0])==='신규');
    const wantsAdd=(writable&&createDeclared)||Boolean(commandAdd);
    const addControl=!wantsAdd?'':commandAdd?button(commandAdd,label):newNavigation?button('신규',label):standardButton('추가','add',!canAdd,'같은 저장 단위에 신규 행 추가').replace('data-grid-add',table===model.columns[0]&&model.independentButtons?.includes('행 추가')?'data-grid-add data-selection-independent':'data-grid-add');
    const addRefRow=model.addReferences?.rows.find(row=>plain(row[0])===label);
    const addRef=addRefRow?{search:plain(addRefRow[1]),key:plain(addRefRow[2]),rate:plain(addRefRow[3]),values:JSON.parse(addRefRow[4])}:null;
    const header=chrome({title:label,count:`${empty?0:rows.length}건`,read:standardButton('조회','read',['saving','unknown','denied'].includes(figure.key)||figure.disabledButtons?.includes('조회'),'이 그리드의 현재 대상/조건을 조회한다. 미저장 입력 확인 후 조회하며 독립 저장 계약을 만들지 않는다.'),add:(writable&&createDeclared)||commandAdd?addControl:'' ,save:approvalControl||((writable||(table===model.columns[0]&&figure.buttons.includes('저장')))?standardButton('저장','save',(!model.masterLink&&!editableHere&&writable)||!figure.editable||!figure.buttons.includes('저장')||figure.disabledButtons?.includes('저장'),'요건서의 전체 저장 단위를 검증한다. 하위 탭 버튼도 소유 저장 범위를 유지한다.'):'') ,actions,tools:excel+settings});
    const [contextField,contextValue]=(model.masterEditRule??'').split('=');
    return `<div data-grid-view="${gridId}" data-grid-name="${escape(label)}"${table.auditPolicy?` data-grid-audit="${escape(table.auditPolicy.kind)}"`:''}${addChoice?` data-add-choice="${escape(JSON.stringify(addChoice))}"`:''}${addRef?` data-add-reference="${escape(JSON.stringify(addRef))}"`:''} data-grid-selection="${multiple?'multiple':'single'}" data-grid-role="${table===model.columns[0]?'master':'detail'}">${header}${canAdd?`<template data-row-template>${renderRow({...addChoice?.defaults,...(model.masterLink?{[contextField]:contextValue}:{})},rows.length)}</template>`:''}
      <div class="grid" style="overflow:auto"><table style="width:100%;table-layout:fixed;min-width:${table.rows.filter(r=>!optional(r)).reduce((n,r)=>n+width(r),systemWidth(showState,multiple,showDelete))}px"><thead><tr>${heads}</tr></thead><tbody>${body}</tbody></table></div>
      <div class="pg">총 ${empty ? '0' : rows.length}건 · 1 페이지</div>${addChoice?lookupAddDialog(addChoice,badge):''}</div>`;
  };
  const formView = (table) => table.rows.map((r, i) => {
    const label = plain(r[0]);
    const ro = table.readonly || !figure.editable || plain(cell(table, r, '편집')) === '읽기전용';
    const input = plain(cell(table, r, '입력방식'));
    const value = example(table, r) || '';
    const fieldId = `${figure.key}-table-${model.columns.indexOf(table)}-field-${i}`;
    const control = /검색콤보/.test(input)
      ? searchCombo(fieldId, label, value, plain(cell(table, r, '후보 예시')), ro)
      : /장문/.test(input)
      ? `<textarea id="${fieldId}" rows="2" ${ro ? 'readonly' : ''}>${escape(value)}</textarea>`
      : /선택|셀렉트/.test(input)
        ? `<select id="${fieldId}" ${ro ? 'disabled' : ''}><option>${escape(value || '미지정')}</option></select>`
        : `<input id="${fieldId}" value="${escape(value)}" ${ro ? 'readonly' : ''}>`;
    return `<div class="f${ro ? ' ro' : ''}"><label for="${fieldId}" data-field-label="${escape(label)}">${escape(label)}${n.badge(columnExplanation(table,r),`column:${titleOf(table)}:${label}`)}</label>${control}</div>`;
  }).join('');
  const tabNames = [...new Set(details.map(tabOf))];
  const tabs = () => tabNames.map((name) => {const note=badge(name,surfaceOf(model,name)?.purpose||'선택한 대상의 이 영역을 확인한다.',`tab:${name}`);return interactive ? `<button type="button" role="tab" aria-selected="${name === figure.detail}" data-detail-tab="${escape(name)}" class="btn ghost sm ${name === figure.detail ? 'on' : ''}">${escape(name)}${note}</button>` : `<span class="${name === figure.detail ? 'on' : ''}">${escape(name)}${note}</span>`;}).join('');
  const detailSearch = (name) => {
    const table = model.detailSearchTables?.find((t) => titleOf(t) === name);
    return table ? `<div class="sf" style="grid-template-columns:max-content minmax(0,1fr)">${table.rows.map((r) => `<label>${escape(plain(r[0]))}${badge(plain(r[0]),plain(cell(table,r,'기본값')))}</label><input aria-label="${escape(plain(r[0]))}" placeholder="${escape(plain(r[0]))}">`).join('')}</div>` : '';
  };
  const detailTable = (table) => /—\s*폼$/.test(table.heading) ? `<h3>${escape(titleOf(table))}</h3>${formView(table)}` : tableView(table);
  const detail = () => interactive && showData ? '' : selected.length ? detailSearch(figure.detail) + selected.map(detailTable).join('') : `<p>${escape(figure.notice)}</p>`;
  const search = compact ? '' : model.search.map((r) => {
    if (!model.searchTable) return searchControl(r, n);
    const label = plain(r[0]);
    const locked = !figure.buttons.includes('조회');
    const mode = plain(cell(model.searchTable, r, '입력방식'));
    const choices = plain(cell(model.searchTable, r, '선택 출처')).replace(/^상수\s*/, '').split('·');
    const control = /셀렉트|선택/.test(mode)
      ? `<select aria-label="${escape(label)}" ${locked ? 'disabled' : ''}><option>전체</option>${choices.map((x) => `<option>${escape(x.includes('=') ? x.slice(x.indexOf('=')+1) : x)}</option>`).join('')}</select>`
      : `<input aria-label="${escape(label)}" placeholder="${escape(label)}" ${locked ? 'readonly' : ''}>`;
    return `<label>${escape(label)}${n.badge(searchExplanation(model.searchTable,r),`search:${label}`)}</label>${control}`;
  }).join('');
  const master = compact ? '' : tableView(model.columns[0],!showData);
  const tabMarkup=compact||tabNames.length<2?'':tabs();
  const panels = interactive && showData ? tabNames.map(name=>`<div data-detail-panel="${escape(name)}"${name===figure.detail?'':' hidden'}>${surfaceIntroduction(surfaceOf(model,name))}<div class="hint" data-context-label hidden>현재 컬럼: <span></span> <button type="button" class="btn ghost sm" data-clear-context>전체 보기${badge('전체 보기','건수 셀에서 전달한 컬럼 문맥 필터를 해제하고 같은 대상의 전체 목록을 표시한다.','common:clear-context')}</button></div>${detailSearch(name)}${details.filter(t=>tabOf(t)===name).map(detailTable).join('')}</div>`).join('') : compact?'':detail();
  const lower = (tabMarkup?`<div class="tabs" style="flex-wrap:wrap"${interactive?' role="tablist"':''}>${tabMarkup}</div>`:'')+panels;
  const panes = singleGrid
    ? `<div data-layout="single-grid" style="margin-top:12px">${master}</div>`
    : model.vertical!==false
    ? `<div data-layout="top-bottom" style="margin-top:12px"><section data-pane="master">${master}</section><div class="split"><span class="hnd">⋮⋮⋮</span></div><section data-pane="detail">${lower}</section></div>`
    : `<div class="splitrow" data-layout="left-right" style="margin-top:12px"><div class="pane">${master}</div><div class="split v"><span class="hnd">⋮</span></div><div class="pane">${lower}</div></div>`;
  const requestNav=!compact&&showData&&model.requestTargets&&!['unknown','denied','readonly','partial-permission'].includes(figure.stateKey??figure.key)
    ? `<div class="tb" style="flex-wrap:wrap"><span class="ttl">복합 요청 이동 예시</span><span class="hint">같은 요청번호 · 도착 시 현재 권한 재검사</span><span class="sp"></span>${model.requestTargets.map(t=>`<a class="btn ghost sm" data-request-navigation data-request-screen="${escape(t.id)}" href="${escape(t.href)}">${escape(t.label)}</a>`).join('')}</div>`:'';
  const selectedLabel = model.columns[0].rows.filter(r=>/현재 객체명|현재 업무명/.test(plain(r[0]))).map(r=>example(model.columns[0],r)).join(' / ');
  const main = compact ? `<div class="hint">${escape(figure.detail)} · 같은 상위 행의 상세 영역</div><div class="notes" role="status">${escape(figure.notice)}</div><div data-detail-panel="${escape(figure.detail)}"><div class="hint" data-context-label hidden>현재 컬럼: <span></span> <button type="button" class="btn ghost sm" data-clear-context>전체 보기${badge('전체 보기','건수 셀에서 전달한 컬럼 문맥 필터를 해제하고 같은 대상의 전체 목록을 표시한다.','common:clear-context')}</button></div>${detail()}</div>` : `<div class="sf">${search}${search?`<button type="button" class="btn ghost sm" data-search-reset${['saving','unknown','denied'].includes(figure.key)?' disabled':''}>초기화</button>`:''}</div>${interactive&&showData&&selectedLabel?`<div class="hint">선택 테이블: ${escape(selectedLabel)}</div>`:''}
       <div class="notes" role="status">${escape(figure.notice)}</div>${requestNav}${panes}`;
  const commandTable=model.commandFields;
  const commands=[...new Set((commandTable?.rows??[]).map(r=>plain(r[0])))].map(name=>{
    const rows=commandTable.rows.filter(r=>plain(r[0])===name);
    const field=r=>{
      const value=h=>plain(cell(commandTable,r,h));
      const label=value('필드'), example=value('예시'), mode=value('입력방식');
      const required=value('필수')==='필수'?' required':'';
      const max=value('최대 길이');
      const note=badge(label,`${value('설명')} 필수: ${value('필수')}. 검증: ${value('검증')}. 메시지: ${value('메시지 키')}`,`command:${name}:${label}`);
      const control=mode==='선택'?`<select aria-label="${escape(label)}"${required}>${value('후보 예시').split('·').map(v=>`<option${v===example?' selected':''}>${escape(v)}</option>`).join('')}</select>`
        :mode==='장문'?`<textarea aria-label="${escape(label)}" rows="3"${required}>${escape(example)}</textarea>`
        :`<input aria-label="${escape(label)}" type="${mode==='일시'?'datetime-local':'text'}" value="${escape(example==='—'?'':example)}"${required}${mode==='조회'?' readonly':''}${/^\d+$/.test(max)?` maxlength="${max}"`:''}>`;
      return `<label class="command-field"><span>${escape(label)}${required?' *':''}${note}</span>${control}</label>`;
    };
    const targetRows=rows.filter(r=>plain(cell(commandTable,r,'영역'))==='관련 대상');
    const targets=targetRows.length?`<div data-command-targets><fieldset data-command-target><legend>관련 테이블·버전</legend>${targetRows.map(field).join('')}<button type="button" class="btn ghost sm" data-remove-command-target>대상 제거${badge('대상 제거','입력 중 대상 연결만 제거한다. 테이블이나 버전을 삭제하지 않는다.',`command:${name}:remove`)}</button></fieldset></div><button type="button" class="btn ghost sm" data-add-command-target>대상 추가${badge('대상 추가','같은 명령에 연결할 대상 입력 행을 추가한다.',`command:${name}:add`)}</button>`:'';
    return `<dialog class="command-dialog" data-command="${escape(name)}" aria-label="${escape(name)} 입력"><form data-command-form><h3>${escape(name)}</h3>${rows.filter(r=>plain(cell(commandTable,r,'영역'))!=='관련 대상').map(field).join('')}${targets}<p>검토용 입력 예시입니다. 실제 서버로 전송하지 않습니다.</p><p role="status" data-command-result></p><div class="row actions"><button type="submit" class="btn">접수${badge('접수','요건서의 필수 입력과 명령별 검증 조건을 확인해 접수한다. 목업에서는 입력 확인만 시연하며 실제 전송하지 않는다.',`command:${name}:submit`)}</button><button type="button" class="btn ghost" data-close-command>닫기${badge('닫기','입력창을 닫는다. 목업에서는 입력을 유지한다.',`command:${name}:close`)}</button></div></form></dialog>`;
  }).join('');
  return `<div class="screen leaf" id="${escape(figure.key)}"${interactive?' data-interactive-tabs':''}${model.masterLink?` data-master-link="${escape(model.masterLink)}" data-master-initial="${escape(figure.masterValue??'')}" data-calculation-rules="${escape(JSON.stringify(model.calculations??[]))}" data-calculation-guard="${escape(JSON.stringify(model.calculationGuardButtons??[]))}"${model.allocationValidity?` data-allocation-validity="${escape(JSON.stringify(model.allocationValidity))}"`:''}${figure.editable?'':' data-calculation-readonly'}`:''}${manualKey ? ` data-manual="${escape(figure.key)}"` : ''}>
    <div class="bar">${escape(title)} · ${escape(figure.caption)} · 검토용 예시</div><div class="body">${main}${model.masterLink?'<p class="notes" role="status" data-calculation-status hidden></p>':''}</div>${commands}</div>\n${n.render()}`;
}
