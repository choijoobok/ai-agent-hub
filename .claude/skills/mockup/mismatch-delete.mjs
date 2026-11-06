import {esc} from './grid-chrome.mjs';
/** 정확한 정의 누락 정리의 선언이다. 표시용 집계 배지에서 삭제 후보를 추정하지 않는다. */
export function mismatchDeleteWorkspace(table,model){
 if(!table)return null;
 const setting=table.rows.find(row=>row[0].trim()==='설정');if(!setting)throw new Error('서버 설정 불일치 일괄 삭제 설정이 없다');
 const config=JSON.parse(setting[1]),title=table=>table.heading.replace(/^\d+(?:-\d+)?\.?\s*/,'').replace(/\s*—\s*(폼|그리드)$/,'');
 if(!config||typeof config.action!=='string'||!config.action||!Array.isArray(config.scopes)||!config.scopes.length)throw new Error('일괄 삭제의 액션·범위를 확인한다');
 const areas=new Set(),kinds=['SYSTEM','CONNECTION','DATASOURCE'],definitions=['MISSING','PRESENT','UNKNOWN','PROPERTY_MISMATCH','DBMS_MISMATCH','CONNECTION_FAILED'];
 for(const scope of config.scopes){
  const area=model.columns.find(table=>title(table)===scope.area);
  if(!area||areas.has(scope.area)||!kinds.includes(scope.kind)||![scope.key,scope.name].every(name=>area.rows.some(row=>row[0]===name)))throw new Error('일괄 삭제의 그리드·키·명칭이 잘못됐다');areas.add(scope.area);
  if(scope.parent!==null&&(typeof scope.parent!=='string'||!model.columns[0].rows.some(row=>row[0]===scope.parent)))throw new Error('일괄 삭제의 상위 고정 필드를 확인한다');
  for(const field of ['completeCurrent','safeDeployment','permission'])if(typeof scope[field]!=='boolean')throw new Error('일괄 삭제의 신뢰·권한 판정은 명시한 Boolean이다: '+field);
  for(const field of ['configurationVersion','diagnosisVersion','referenceVersion'])if(typeof scope[field]!=='string'||!scope[field])throw new Error('일괄 삭제의 근거 버전이 없다: '+field);
  if(!['정상','실패'].includes(scope.previewState)||!['정상','실패'].includes(scope.executionState)||!Array.isArray(scope.filters)||scope.filters.some(filter=>!filter.label||!filter.field||!['contains','eq'].includes(filter.match))||!Array.isArray(scope.records))throw new Error('일괄 삭제의 조회 범위·상태를 확인한다');
  const searches=scope.parent?(model.detailSearchTables??[]).filter(table=>title(table)===scope.area).flatMap(table=>table.rows):(model.search??[]);
  if(scope.filters.some(filter=>!searches.some(row=>row[0]===filter.label)))throw new Error('일괄 삭제의 조회조건 선언이 없다');
  const ids=new Set();for(const record of scope.records){
   const key=JSON.stringify([scope.kind,scope.parent?record.parent:null,record.id]);
   if(typeof record.id!=='string'||!record.id||ids.has(key)||typeof record.name!=='string'||record.kind!==scope.kind||!definitions.includes(record.definition)||typeof record.reason!=='string'||typeof record.version!=='string'||!record.version||typeof record.confirmed!=='boolean'||![true,false,null].includes(record.permission)||!(record.references===null||Array.isArray(record.references)&&record.references.every(reference=>typeof reference==='string'))||!(record.parent===null||typeof record.parent==='string')||(scope.parent&&!record.parent))throw new Error('일괄 삭제의 정확 대상·판정·참조를 확인한다');ids.add(key);
  }
 }
 return config;
}
export function mismatchDeleteMarkup(config,figure,{action,badge}){
 if(!config)return '';
 config={...config,scopes:config.scopes.map(scope=>({...scope,previewState:figure.examples?.['일괄 삭제 미리보기']??scope.previewState,executionState:figure.examples?.['일괄 삭제 실행']??scope.executionState}))};
 return `<div hidden data-mismatch-delete-config="${esc(JSON.stringify(config))}" data-mismatch-delete-editable="${figure.editable}" data-mismatch-delete-state="${esc(figure.stateKey??figure.key)}"></div><p class="hint" role="status" data-mismatch-delete-notice hidden></p><dialog class="command-dialog" data-mismatch-delete-dialog aria-label="${esc(config.action)}" style="width:min(1200px,94vw)"><form data-mismatch-delete-form><h3>${esc(config.action)}</h3><p>서버 미리보기를 흉내 낸 정적 예시입니다. 현재 조회조건 전체의 정확한 누락 정의만 확인하며 실제 API·DB 삭제나 정책 판정을 수행하지 않습니다. 과거 이력은 제거하지 않습니다.</p><p data-mismatch-delete-scope></p><p data-mismatch-delete-evidence></p><div class="grid" style="max-height:46vh;overflow:auto"><table><thead><tr>${['ID','명칭','종류','정의 판정','사유','현재 참조','삭제 판정'].map(label=>`<th>${esc(label)}${badge(label,'현재 조회 범위의 정적 미리보기 값. 페이지·선택행만으로 대상을 제한하지 않는다.','delete-preview:'+label)}</th>`).join('')}</tr></thead><tbody data-mismatch-delete-results></tbody></table></div><p role="status" data-mismatch-delete-status></p><label class="command-field"><span>삭제 사유${badge('삭제 사유','매번 새로 입력하는 공백 불가 사유. 취소는 원 목록과 미저장 입력을 유지한다.','delete:reason')}</span><textarea data-mismatch-delete-reason aria-label="삭제 사유" rows="3" required></textarea></label><label><input type="checkbox" data-mismatch-delete-confirm required>미리보기의 삭제 가능 대상 전체를 로컬 제거하는 시연임을 확인했습니다.${badge('삭제 확인','현재 참조·판정·권한과 조회 범위가 유지될 때만 전체 실행한다. 일부 성공으로 처리하지 않는다.','delete:confirm')}</label><div class="row actions">${action('일괄 삭제 실행','execute').replace('type="button"','type="submit"')}${action('취소','cancel')}</div></form></dialog>`;
}
