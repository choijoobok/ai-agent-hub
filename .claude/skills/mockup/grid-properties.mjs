/** 그리드 명세의 축을 분리한다. 업무 판정은 화면 요건, 어휘는 shared/grid/types.ts가 정본이다. */
const plain=v=>String(v??'').replace(/`/g,'').replace(/\*\*([^*]+)\*\*/g,'$1').replace(/\\\|/g,'|').trim();
const get=(t,r,...names)=>{for(const n of names){const i=t.header.findIndex(h=>plain(h)===n);if(i>=0&&r[i]!==undefined)return plain(r[i]);}return '';};
export const GRID_PROPERTIES=['값 자료형','그리드 타입','에디터','렌더러','뷰어','신규 필수','신규 편집','조회 필수','조회 편집','값 원천','생성·갱신','참조·계산식','저장 방식','추가 속성'];
export const GRID_TYPES=['text','number','bigint','popupRef','ynFlag','code','list','date','dateTime'];
export const GRID_EDITORS=['text','largeText','number','switch','select','popupRef','richSelect','listModal','textModal'];
export const GRID_RENDERERS=['badge','tags','link','duration','progress','icon'];
export const editablePolicy=v=>/가능|조건부|신규|^Y$|^true$/.test(plain(v))&&!/불가|읽기|해당 없음/.test(plain(v));
export function rowEditPolicy(table,row,isNew=false){if(table.readonly)return '읽기전용';const explicit=get(table,row,isNew?'신규 편집':'조회 편집');if(explicit)return explicit;const legacy=get(table,row,'편집');return !isNew&&/신규/.test(legacy)?'불가(신규만 입력)':legacy;}
export function canEditRow(table,row,isNew=false){return editablePolicy(rowEditPolicy(table,row,isNew));}
export const gridWritable=table=>!table.readonly&&table.rows.some(row=>canEditRow(table,row,true)||canEditRow(table,row,false));
export function propertyAttributes(table,row,isNew=false){
 const escape=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 return Object.entries({'grid-type':get(table,row,'그리드 타입'),'editor-type':get(table,row,'에디터'),'renderer-type':get(table,row,'렌더러'),'value-origin':get(table,row,'값 원천'),'storage-mode':get(table,row,'저장 방식'),'row-required':get(table,row,isNew?'신규 필수':'조회 필수'),'row-edit-policy':rowEditPolicy(table,row,isNew)}).map(([k,v])=>` data-${k}="${escape(v)}"`).join('');
}
export function inferredProperties(table,row,{canCreate=true}={}){
 const name=plain(row[0]),mode=get(table,row,'입력방식','에디터'),edit=table.readonly?'읽기전용':get(table,row,'편집'),required=get(table,row,'필수'),example=get(table,row,'예시');
 const field=get(table,row,'필드명'),source=get(table,row,'기본값/원천','선택 출처','원천'),description=get(table,row,'설명'),validation=get(table,row,'검증');
 const writable=editablePolicy(edit),newOnly=/신규/.test(edit),condition=get(table,row,'편집 조건');
 let type='text',value='string',editor='없음',renderer='없음',viewer='없음';
 if(/스위치|Y\/N|여부/.test(mode)||/^(true|false)$/.test(example)){type='ynFlag';value=/^(true|false)$/.test(example)||/Yn$/.test(field)?'boolean':'string';}
 else if(/숫자|정수|소수|금액/.test(mode)){type='number';value='number';}
 else if(/날짜|일시|일자|년월|date/i.test(mode)){type=/일시|dateTime|timestamp/i.test(mode)?'dateTime':'date';}
 else if(/팝업 선택/.test(mode)){type='popupRef';}
 else if(/다중|배열/.test(mode)){type='list';value='string[]';}
 else if(/선택|셀렉트|콤보/.test(mode)){type='code';}
 else if(/(^|\.)\w*(?:Id|No|Version)$/.test(field)&&/^\d+$/.test(example)&&!/순서|정렬|횟수|건수/.test(name)){type='bigint';}
 if(writable&&(!newOnly||canCreate)){
  editor=type==='ynFlag'?'없음':/검색콤보/.test(mode)?'richSelect':/팝업 선택/.test(mode)?'popupRef':/설정 팝업/.test(mode)?'textModal':/다중|배열/.test(mode)?'listModal':/선택|셀렉트/.test(mode)?'select':/장문|텍스트영역/.test(mode)?'largeText':type==='number'?'number':/^date/.test(type)?'자동(type)':'text';
 }
 if(/배지/.test(mode))renderer='badge';
 if(/화면 이동|버튼 액션/.test(mode))renderer='link';
 if(!writable&&/장문|뷰어|텍스트영역/.test(mode))viewer='largeText';
 const policy=!writable?'불가':/조건부/.test(edit)||condition&&condition!=='—'?`조건부 — ${condition&&condition!=='—'?condition:'§4-2의 권한·업무 상태 및 검증 조건'}`:'가능';
 const req=required&&!/^(?:—|선택|없음|입력 대상 아님)$/.test(required)?required:'선택';
 let origin=writable?'사용자 입력':/REG_|MOD_|등록자|등록일시|수정자|수정일시/.test(get(table,row,'DB 컬럼')+' '+name)?'서버 생성':/YAML|yml|URL 도출|기동/.test(source)?'YAML/설정 동기화':/계산|파생|집계|건수|합계|비율/.test(source+' '+description+' '+name)?'자동 계산/집계':'서버·참조 조회';
 const reference=writable?source||'직접 입력':source||description||'§3의 소유 조회 계약';
 const when=writable?'로컬 입력 후 전체 저장 성공 시':origin==='YAML/설정 동기화'?'기동/설정 동기화 후 조회 시':origin==='서버 생성'?'서버 저장 성공 시':'조회/대상 변경 시 원천에서 갱신';
 const extra={};if(/년월/.test(mode))extra.precision='month';
 return {'값 자료형':value,'그리드 타입':type,'에디터':editor,'렌더러':renderer,'뷰어':viewer,
  '신규 필수':!canCreate?'해당 없음(신규 미제공)':writable?req:'입력 대상 아님',
  '신규 편집':!canCreate?'해당 없음(신규 미제공)':policy,
  '조회 필수':writable&&!newOnly?req:'입력 대상 아님','조회 편집':newOnly?'불가(신규만 입력)':policy,
  '값 원천':origin,'생성·갱신':when,'참조·계산식':reference+(validation&&/계산|산식/.test(validation)?'; '+validation:''),
  '저장 방식':writable?'소유 구성에 저장':origin==='서버 생성'?'서버 저장값':origin==='YAML/설정 동기화'?'소유 계약에 따른 설정 투영/저장':origin==='자동 계산/집계'?'계산/집계 투영 — 소유 산정 결과 저장 여부는 §3·§6 계약':'소유 정본의 저장값/참조 투영 — §3 조회 계약',
  '추가 속성':Object.keys(extra).length?JSON.stringify(extra):'—'};
}
/** 감사 합성과 레거시 독립 화면의 기본값. admin 정본은 check에서 명시 헤더를 별도로 검사한다. */
export function withGridProperties(table,context={}){
 if(table.form||/—\s*폼$/.test(table.heading))return table;
 const declaredHeader=table.declaredHeader??table.header;
 const header=[...table.header];for(const key of GRID_PROPERTIES)if(!header.some(h=>plain(h)===key))header.push(key);
 return {...table,declaredHeader,header,rows:table.rows.map(old=>{const values=inferredProperties(table,old,context);return header.map((h,i)=>old[i]!==undefined&&plain(old[i])!==''?old[i]:values[plain(h)]??'');})};
}
export function gridPropertyIssues(table,{explicit=false}={}){
 if(table.form||/—\s*폼$/.test(table.heading))return [];
 const errors=[],declared=(table.declaredHeader??table.header).map(plain);
 if(explicit){
  for(const key of GRID_PROPERTIES)if(!declared.includes(key))errors.push(`${table.heading}: ${key} 선언 누락`);
  for(const row of table.declaredRows??[])for(const key of GRID_PROPERTIES){const at=declared.indexOf(key);if(at>=0&&!plain(row[at]))errors.push(`${table.heading}/${plain(row[0])}: ${key} 정본 값 누락`);}
 }
 for(const row of table.rows){
  const field=plain(row[0]);
  for(const key of GRID_PROPERTIES)if(!get(table,row,key))errors.push(`${table.heading}/${field}: ${key} 값 누락`);
  for(const [key,values] of [['그리드 타입',GRID_TYPES],['에디터',[...GRID_EDITORS,'없음','자동(type)']],['렌더러',[...GRID_RENDERERS,'없음']],['뷰어',['largeText','없음']]])if(!values.includes(get(table,row,key)))errors.push(`${table.heading}/${field}: 지원하지 않는 ${key} ${get(table,row,key)}`);
  if(get(table,row,'에디터')==='자동(type)'&&!/^date/.test(get(table,row,'그리드 타입')))errors.push(`${field}: 날짜 이외의 자동 에디터`);
  const extra=get(table,row,'추가 속성');if(extra&&extra!=='—')try{const value=JSON.parse(extra);if(!value||Array.isArray(value)||typeof value!=='object')throw Error();}catch{errors.push(`${field}: 추가 속성은 JSON 객체다`);}
  if(!canEditRow(table,row,true)&&!canEditRow(table,row,false)&&get(table,row,'에디터')!=='없음')errors.push(`${field}: 편집 불가 컬럼에 에디터 선언`);
 }
 return errors;
}
