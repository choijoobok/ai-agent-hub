/** 목업 전체에서 같은 요소는 같은 번호와 한 번의 상세 설명을 사용한다. */
export const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c]);
const plain = value => String(value ?? '').replace(/`([^`]+)`/g,'$1').replace(/\*\*([^*]+)\*\*/g,'$1').replace(/\\\|/g,'|').replace(/<\/?(?:b|strong|em|i|code|span|small|br|sub|sup|del|u)(?:\s[^<>]*)?\/?>/gi,'').replace(/\s+/g,' ').trim();
export const tableCell = (table,row,...names) => {
  for(const name of names){const index=table?.header.findIndex(h=>plain(h)===name);if(index>=0&&row[index]!==undefined)return plain(row[index]);}
  return '';
};
const facts = values => `<dl class="explanation-facts">${values.filter(([,v])=>v&&v!=='—').map(([k,v])=>`<dt>${escapeHtml(k)}</dt><dd>${escapeHtml(v)}</dd>`).join('')}</dl>`;
export function columnExplanation(table,row){
  const name=plain(row[0]);
  const editable=table.readonly?'읽기전용':tableCell(table,row,'편집').replace(/^불가$/,'읽기전용');
  return `<b>${escapeHtml(table.heading)} › ${escapeHtml(name)}</b>${facts([
    ['설명',tableCell(table,row,'설명','업무 설명','의미','무엇')],
    ['필수 입력',editable==='읽기전용'?'입력 대상 아님 (조회 전용)':(tableCell(table,row,'필수').replace(/^—$/,'선택') || '개별 필수 조건은 공통 규칙 참조')],
    ['입력 방식',tableCell(table,row,'입력방식')], ['편집 조건',editable],
    ['값 자료형',tableCell(table,row,'값 자료형')],['그리드 타입(type)',tableCell(table,row,'그리드 타입')],
    ['에디터(editor)',tableCell(table,row,'에디터')],['렌더러(renderer)',tableCell(table,row,'렌더러')],['뷰어(viewer)',tableCell(table,row,'뷰어')],
    ['신규 행 필수',tableCell(table,row,'신규 필수')],['신규 행 편집',tableCell(table,row,'신규 편집')],
    ['조회 행 필수',tableCell(table,row,'조회 필수')],['조회 행 편집',tableCell(table,row,'조회 편집')],
    ['값 원천',tableCell(table,row,'값 원천')],['생성·갱신 시점',tableCell(table,row,'생성·갱신')],
    ['참조 항목·계산식',tableCell(table,row,'참조·계산식')],['저장 방식',tableCell(table,row,'저장 방식')],['추가 구현 속성',tableCell(table,row,'추가 속성')],
    ['선택·값 출처',tableCell(table,row,'선택 출처','기본값/원천')],
    ['선택 코드',tableCell(table,row,'코드그룹','코드 그룹')],
    ['검증·거부 조건',tableCell(table,row,'검증').replace(/^—$/,'') || (editable==='읽기전용'?'직접 입력하지 않는다. 서버가 반환한 허용 범위의 값을 표시한다.':'아래 공통 검증·실행 규칙을 적용한다. 개별 길이/범위는 임의로 추정하지 않는다.')],
    ['기본값',tableCell(table,row,'기본값')],['표시 예시',tableCell(table,row,'예시')],
    ['연결 동작',tableCell(table,row,'이동 탭')],
  ])}${!tableCell(table,row,'검증')?'<p><a href="#shared-rules">공통 검증·실행 규칙 보기</a></p>':''}`;
}
export function actionExplanation(table,row,scope='화면'){
  return `<b>${escapeHtml(scope)} › ${escapeHtml(tableCell(table,row,'표시명')||plain(row[0]))} 버튼</b>${facts([
    ['하는 일',tableCell(table,row,'동작','무엇','비고')],
    ['사용 조건',tableCell(table,row,'활성 조건','사용 조건')],
    ['필요 권한',tableCell(table,row,'권한','액션')],
    ['저장·조회 범위',tableCell(table,row,'저장 범위','범위')],
    ['확인',tableCell(table,row,'확인 문구','확인 문구 키')],
  ])}`;
}
export function searchExplanation(table,row){
  return `<b>조회 조건 › ${escapeHtml(plain(row[0]))}</b>${facts([
    ['설명',tableCell(table,row,'설명')],['입력 방식',tableCell(table,row,'입력방식')],
    ['필수 입력',tableCell(table,row,'필수')||'선택'],['비교 방식',tableCell(table,row,'연산')],
    ['기본값',tableCell(table,row,'기본값')],['선택 출처',tableCell(table,row,'선택 출처')],
    ['검증',tableCell(table,row,'검증')],
  ])}`;
}
/** 초기 화면에서 hidden·template·닫힌 창/탭을 제외하고 보이는 번호의 DOM 순서를 읽는다. */
export function visibleNoteIds(html){
  const stack=[],ids=[];
  const voids=new Set(['input','br','hr','img','meta','link','source','area','base','embed','param','wbr']);
  for(const token of html.matchAll(/<(\/?)([a-z][\w:-]*)\b[^>]*>/gi)){
    const tag=token[2].toLowerCase(),text=token[0];
    if(token[1]){const index=stack.findLastIndex(node=>node.tag===tag);if(index>=0)stack.splice(index);continue;}
    const parent=stack.at(-1);
    const hidden=Boolean(parent?.hidden)||/\shidden(?:\s|=|\/?>)/.test(text)||tag==='template'
      ||(tag==='dialog'&&!/\sopen(?:\s|=|\/?>)/.test(text))
      ||Boolean(parent?.tag==='details'&&parent.closed&&tag!=='summary');
    const id=text.match(/\bdata-note="([^"]+)"/)?.[1];
    if(id&&!hidden&&!ids.includes(id))ids.push(id);
    if(!voids.has(tag)&&!text.endsWith('/>'))stack.push({tag,hidden,closed:tag==='details'&&!/\sopen(?:\s|=|\/?>)/.test(text)});
  }
  return ids;
}
export function annotationIssues(html){
  const issues=[],notes=new Map();
  for(const match of html.matchAll(/<li\b[^>]*id="note-([^"]+)"[^>]*>\s*<span\b[^>]*>(\d+)<\/span>/g))notes.set(match[1],match[2]);
  for(const match of html.matchAll(/<span\b([^>]*\bdata-note="([^"]+)"[^>]*)>(\d+)<\/span>/g)){
    const [,attrs,id,number]=match;
    if(!notes.has(id))issues.push(`화면 번호 ${number}의 상세 설명이 없다 (${id})`);
    else if(notes.get(id)!==number)issues.push(`화면 번호 ${number}와 상세 설명 번호 ${notes.get(id)}가 다르다 (${id})`);
    if(attrs.match(/aria-label="설명 (\d+) 보기"/)?.[1]!==number)issues.push(`화면 번호 ${number}의 접근성 설명 번호가 다르다`);
  }
  return issues;
}
export function annotationBook(){
  const notes=[];const byKey=new Map();let initialCount=null,sealed=false;
  return {
    badge(description,key=description){
      if(sealed)throw new Error('설명 번호를 확정한 뒤에는 새 배지를 생성할 수 없다');
      let note=byKey.get(key);
      if(!note){note={id:`item-${notes.length+1}`,number:notes.length+1,description};notes.push(note);byKey.set(key,note);}
      return `<span class="n anchor" data-note="${note.id}" role="link" tabindex="0" aria-label="설명 ${note.number} 보기">${note.number}</span>`;
    },
    prioritize(initialHtml){
      const ids=visibleNoteIds(initialHtml),old=new Map(notes.map(note=>[note.id,note]));
      const visible=ids.map(id=>old.get(id));
      if(visible.some(note=>!note))throw new Error('초기 화면의 번호가 설명 목록에 없다');
      const ordered=[...visible,...notes.filter(note=>!ids.includes(note.id))],mapping=new Map();
      ordered.forEach((note,index)=>{mapping.set(note.id,{id:`item-${index+1}`,number:index+1});});
      ordered.forEach(note=>Object.assign(note,mapping.get(note.id)));
      notes.splice(0,notes.length,...ordered);initialCount=visible.length;sealed=true;
      return html=>html.replace(/<span\b[^>]*\bdata-note="([^"]+)"[^>]*>\d+<\/span>/g,(span,id)=>{
        const next=mapping.get(id);if(!next)throw new Error('번호 재배정 대상의 설명이 없다');
        return span.replace(`data-note="${id}"`,`data-note="${next.id}"`)
          .replace(/aria-label="설명 \d+ 보기"/,`aria-label="설명 ${next.number} 보기"`)
          .replace(/>\d+<\/span>$/,`>${next.number}</span>`);
      });
    },
    render(){
      const count=initialCount??notes.length;
      const list=items=>`<ol>${items.map(n=>`<li id="note-${n.id}"><span class="n">${n.number}</span><div>${n.description}</div></li>`).join('')}</ol>`;
      const extra=notes.slice(count);
      return `<section class="annotation-catalog" id="element-descriptions" data-annotation-catalog><h2>화면 요소 상세 설명</h2><p>초기 화면에 보이는 요소부터 연속 번호를 부여했습니다. 같은 요소는 탭·상태가 달라도 같은 번호를 씁니다. 번호에 마우스를 올리거나 초점을 맞추면 설명이 나타나고, 누르면 해당 설명으로 이동합니다.</p><p>그리드 타입·에디터·렌더러·필수 조건은 개발 시 적용할 계약입니다. 목업은 입력과 상태 전환을 예시로 보여주며, 실제 공통 그리드의 모든 편집기와 서버 검증을 실행하지는 않습니다.</p>${list(notes.slice(0,count))}${extra.length?`<details data-extra-annotations><summary>추가 설명 ${extra.length}개 — 숨김 컬럼·다른 탭·편집/상태별 요소</summary>${list(extra)}</details>`:''}</section>`;
    },
  };
}
export function surfaceRows(table){
  return (table?.rows??[]).map(row=>({
    name:tableCell(table,row,'영역'), kind:tableCell(table,row,'종류')||'영역',
    purpose:tableCell(table,row,'관리 대상·목적'), workflow:tableCell(table,row,'작업 설명'),
    buttons:tableCell(table,row,'버튼').split('·').map(s=>s.trim()).filter(s=>s&&s!=='없음'),
    disabled:tableCell(table,row,'비활성 버튼').split('·').map(s=>s.trim()).filter(s=>s&&s!=='없음'),
    trigger:tableCell(table,row,'조회 방식'), save:tableCell(table,row,'저장 범위'),
  }));
}
export function surfaceIntroduction(surface){
  if(!surface)return '';
  return `<div class="surface-guide"><p><b>관리 대상:</b> ${escapeHtml(surface.purpose)}</p><p><b>작업:</b> ${escapeHtml(surface.workflow)}</p>${facts([['조회',surface.trigger],['저장 범위',surface.save]])}</div>`;
}
export const surfaceOf = (model,name) => model.surfaces?.find(s=>s.name===name);
