/** 행 버튼은 선언한 조건과 현재 그림의 명시 허용을 함께 적용한다. 결과는 검토용 예시다. */
import path from 'node:path';
import {escapeHtml as esc,tableCell as cell} from './presentation.mjs';
export function rowActionCell(table,row,values,{buttons=[],disabledButtons=[],locked=false}={}){
  const label=cell(table,row,'컬럼'),rule=cell(table,row,'액션 조건');
  const conditions=rule&&rule!=='—'?rule.split('&&').map(term=>{
    const [field,...rest]=term.trim().split('=');
    if(!field||!rest.length)throw new Error('행 액션 조건은 필드=허용값 형식이다');
    return rest.join('=').split('·').includes(String(values[field]??''));
  }):[];
  const enabled=!locked&&buttons.includes(label)&&!disabledButtons.includes(label)&&conditions.length>0&&conditions.every(Boolean);
  const result=values[label+' 결과']??{};
  if(typeof result!=='object'||Array.isArray(result)||result===null)throw new Error('행 액션 결과 예시는 JSON 객체다');
  const reason=enabled?'검토용 예시 결과 보기 — 외부 요청을 전송하지 않습니다.':cell(table,row,'비활성 안내')||'현재 권한·설정·연결 유효성 조건을 충족하지 않습니다.';
  return `<button type="button" class="btn ghost sm" data-row-action="${esc(label)}" data-row-action-result="${esc(JSON.stringify(result))}"${enabled?'':' disabled'} title="${esc(reason)}">${esc(label)}</button>`;
}
export function rowNavigationCell(table,row,values,model){
  const label=cell(table,row,'컬럼'),href=String(values[label]??cell(table,row,'예시'));
  if(!href||href==='—')return '<span>이동 대상 없음</span>';
  if(!/^(?:\.\.\/)*[a-z0-9/-]+\/mockup\.html(?:\?[a-zA-Z0-9=&_-]+)?(?:#main)?$/.test(href))throw new Error('행 이동은 목업 상대 경로와 명시 문맥만 허용한다');
  const destination=path.posix.normalize(path.posix.join(model.screenPath??'',href.split('?')[0])).replace(/\/mockup\.html$/,'');
  const screenId=model.requestRoutes?.find(screen=>screen.route===`/${destination}`)?.id??String(values[label+' 화면']??'');
  if(screenId&&!/^SCR-\d+$/.test(screenId))throw new Error('행 이동의 화면 식별자가 잘못됐다');
  return `<a class="btn ghost sm" href="${esc(href)}" data-request-navigation data-request-screen="${esc(screenId)}">${esc(label)}</a>`;
}
