import {esc} from './grid-chrome.mjs';

/** 추가 후보는 화면 요건의 원천·부모/선택 키로 선언한다. 실제 조회 API는 호출하지 않는다. */
export function addChoiceFor(model, area, figure) {
 const table=model.addChoices,row=table?.rows.find(r=>r[0].trim()===area);
 if(!row)return null;
 const get=name=>String(row[table.header.indexOf(name)]??'').trim();
 const rule={mode:get('방식'),source:get('원천'),parent:get('상위 필드'),fixed:get('고정 필드'),key:get('선택 키'),active:get('활성 필드'),search:get('조회 조건').split('·'),columns:get('표시 컬럼').split('·'),records:JSON.parse(get('후보 예시(JSON)')),defaults:JSON.parse(get('기본값(JSON)')),state:figure.examples?.['추가 후보 상태']??'정상'};
 if(!['조회 팝업','기준정보 콤보'].includes(rule.mode)||![rule.source,rule.parent,rule.fixed,rule.key].every(Boolean)||!Array.isArray(rule.records))throw new Error('추가 방식의 원천·키·후보를 확인한다: '+area);
 const keys=new Set();
 for(const record of rule.records){const key=record[rule.key];if(typeof key!=='string'||!key||keys.has(key))throw new Error('추가 후보 키가 없거나 중복이다: '+area);keys.add(key);}
 return rule;
}

export function lookupAddDialog(rule, badge) {
 if(rule.mode!=='조회 팝업')return '';
 return `<dialog class="command-dialog" data-lookup-add aria-label="${esc(rule.source)} 조회" style="width:min(960px,calc(100vw - 32px))"><h3>${esc(rule.source)} 조회</h3><div class="sf">${rule.search.map(field=>`<label>${esc(field)}${badge(field,'현재 조회 권한 안의 후보를 부분 일치로 검색한다.','lookup:'+field)}<input type="search" data-lookup-filter="${esc(field)}" aria-label="${esc(field)}"></label>`).join('')}<button type="button" class="btn" data-lookup-search>조회</button></div><div class="grid" style="overflow:auto"><table><thead><tr><th>선택</th>${rule.columns.map(field=>`<th>${esc(field)}${badge(field,'원천 기준정보 조회 값. 소속 저장 컬럼을 추가하지 않는다.','lookup-column:'+field)}</th>`).join('')}<th>선택 안내</th></tr></thead><tbody data-lookup-results></tbody></table></div><p role="status" data-lookup-status>조회 후 사용자를 선택하세요.</p><div class="row actions"><button type="button" class="btn" data-lookup-apply disabled>선택</button><button type="button" class="btn ghost" data-lookup-close>닫기</button></div></dialog>`;
}
