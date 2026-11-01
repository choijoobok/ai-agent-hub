// DataGrid 크롬의 목업 표현. 저장 계약/권한은 각 화면 정본이 갖는다.
export const esc = value => String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const editableColumn = value => /가능|조건부|신규|^Y$/.test(value) && !/불가|읽기/.test(value);
export const switchColumn = mode => /^(스위치|설정 스위치|Y\/N|ynFlag|boolean)$/.test(mode);
export const badgeCell = value => `<span class="tag" data-grid-badge>${esc(value)}</span>`;
const switchPairs=[['true','false'],['Y','N'],['1','0'],['예','아니오'],['사용','미사용'],['켜짐','꺼짐']];
export const switchState = value => { const pair=switchPairs.find(p=>p.includes(String(value)));return pair?pair[0]===String(value):null; };
export function switchCell(value,label,{configurable=false,editable=false,locked=false,description=''}={}) {
  const state=switchState(value);
  const pair=switchPairs.find(p=>p.includes(String(value)));
  return state===null?'<span data-switch-unknown>확인 필요</span>':`<button type="button" role="switch" data-grid-switch data-switch-on="${esc(pair[0])}" data-switch-off="${esc(pair[1])}"${configurable?` data-config-switch data-switch-readonly="${!editable}"`:''} aria-label="${esc(label)}" aria-checked="${state}"${!configurable||!editable||locked?' disabled':''} title="${esc(description)}"><span></span></button>`;
}
export function chrome({title,count='',read,add='',save='',actions='',tools=''}) {
  return `<div class="tb" data-grid-toolbar style="flex-wrap:wrap;gap:6px"><span class="tb-meta"><span class="ttl">${esc(title)}</span><span class="cnt">${esc(count)}</span></span><span data-grid-actions style="margin-left:auto;display:flex;align-items:center;justify-content:flex-end;gap:6px;flex-wrap:wrap">${read}${add}${save}${actions}${tools}</span></div>`;
}
export function standardButton(name,action,locked=false,description='') {
  return `<button type="button" class="btn ghost sm" data-grid-${action}${locked?' disabled':''} title="${esc(description)}">${esc(name)}</button>`;
}
export function inlineEditor(value,control) {
  return `<details data-cell-editor data-cell-inline><summary title="더블 클릭으로 셀 편집">${esc(value||'—')}</summary>${control}</details>`;
}
const pinned=(side,offset,width)=>`data-grid-pinned="${side}" style="${side}:${offset}px;width:${width}px;min-width:${width}px;max-width:${width}px"`;
// columnDefs.tsx toColDefs: 번호 → 다중 선택 → 상태 → 업무 컬럼 → 삭제(오른쪽 고정).
export function stateHeads(editable,multiple=false) {
  return `<th data-grid-system="number" ${pinned('left',0,60)}>No</th>`
    +(multiple?`<th data-grid-system="select" ${pinned('left',60,44)}><input type="checkbox" data-grid-select-all aria-label="현재 표시 행 전체 선택"></th>`:'')
    +(editable?`<th data-grid-system="state" data-grid-state-column ${pinned('left',multiple?104:60,84)}>상태</th>`:'');
}
export function stateCells(editable,locked,multiple=false,index=0) {
  return `<td data-grid-system="number" data-grid-row-number ${pinned('left',0,60)}>${index+1}</td>`
    +(multiple?`<td data-grid-system="select" ${pinned('left',60,44)}><input type="checkbox" data-grid-select-row aria-label="${index+1}번 행 선택"${locked?' disabled':''}></td>`:'')
    +(editable?`<td data-grid-system="state" data-grid-state-cell ${pinned('left',multiple?104:60,84)}><span data-row-state></span><button type="button" class="btn ghost sm" data-revert-row hidden title="행의 값을 원본으로 되돌리기">↶</button></td>`:'');
}
export function deleteHead(visible) {
  return visible?`<th data-grid-system="delete" data-grid-delete-column ${pinned('right',0,84)}>삭제</th>`:'';
}
export function deleteCell(visible,locked,reason='') {
  if(!visible)return '';
  return `<td data-grid-system="delete" ${pinned('right',0,84)}>${locked||reason
    ?`<span data-grid-delete-blocked title="${esc(reason||'현재 상태에서는 삭제할 수 없습니다.')}">—</span>`
    :'<button type="button" class="btn ghost sm" data-grid-delete aria-label="행 삭제 표시" title="삭제 표시/해제. 실제 반영은 저장 시 수행"><svg aria-hidden="true" width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor"><path d="M2 4h12M6 4V2h4v2M4 4l1 10h6l1-10M7 6v6M9 6v6"/></svg></button>'}</td>`;
}
export const systemWidth=(editable,multiple,deletable)=>60+(multiple?44:0)+(editable?84:0)+(deletable?84:0);
