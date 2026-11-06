import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import {createRequire} from 'node:module';import vm from 'node:vm';
import {helpWorkspace} from './help-workspace.mjs';import {declaredScreen,searchComboScript} from './declared-layout.mjs';
const frontend=createRequire(new URL('../../../sources/frontend/package.json',import.meta.url)),{JSDOM}=frontend('jsdom');
const richSource=readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-rich-text.js',import.meta.url),'utf8'),ctx=vm.createContext({});vm.runInContext(richSource,ctx);
const config={form:'안내 콘텐츠',kind:'구분',format:'작성 형식',body:'본문',revision:'적용 버전',types:[{code:'USAGE_HELP',name:'사용 도움말',position:'Portal 도움말 영역',timing:'도움말 열기',format:'MARKDOWN',body:'# 사용 도움말\n- 업무를 질문하세요.',revision:'3'},{code:'CHAT_START',name:'새 대화 초기 안내',position:'새 대화 시작 영역',timing:'새 대화 시작',format:'HTML',body:'<p>처음 안내</p>',revision:'2'},{code:'INPUT_NOTICE',name:'개인정보·전송 주의사항',position:'입력창 주변',timing:'요청 제출 전',format:'TEXT',body:'개인정보를 확인하세요.\n필요한 정보만 전송하세요.',revision:'1'}]};
function fixture(){return {helpWorkspace:structuredClone(config),strictButtons:true,search:[],columns:[{heading:'안내 콘텐츠 — 폼',header:['컬럼','입력방식','편집','검증','예시'],rows:[['구분','선택','읽기전용','','USAGE_HELP'],['작성 형식','선택','가능','','MARKDOWN'],['본문','장문','가능','',''],['적용 버전','조회','읽기전용','','3']]}],actions:['조회','저장','본문 크게 편집','미리보기','반영','취소','닫기'].map(name=>[name,'','','','',name+' 설명']),surfaces:[{name:'안내 콘텐츠',buttons:['조회','저장','본문 크게 편집','미리보기']}],commandFields:{header:['명령 입력창','영역','필드','입력방식','필수','예시'],rows:[['저장','기본','변경 대상','조회','선택','채팅 도움말'],['저장','기본','변경 사유','장문','필수','—']]},saveTargets:{rows:[['안내 콘텐츠','CHAT_HELP','채팅 도움말']]}};}
const figure={key:'main',caption:'기본',detail:'안내 콘텐츠',notice:'미저장 콘텐츠 예시',buttons:['조회','저장','본문 크게 편집','미리보기'],editable:true,examples:{}};
const render=(model,editable=true,denied=false)=>declaredScreen('채팅 도움말·주의사항',model,{...figure,key:denied?'denied':'main',detail:denied?'없음':figure.detail,editable,buttons:denied?[]:editable?figure.buttons:['조회','미리보기']},{plain:value=>String(value??''),searchControl:()=>'',manualKey:false,annotator:()=>({badge:()=>'<span data-test-badge></span>',render:()=>''})});
const tick=async()=>{await Promise.resolve();await Promise.resolve();};
async function mount({shadow=false,editable=true,model=fixture()}={}){
 const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://example.test/',runScripts:'outside-only'}),{window}=dom,doc=window.document;
 window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};window.HTMLDialogElement.prototype.close=function(){this.open=false;};window.confirm=()=>false;
 let root=doc;if(shadow){const host=doc.createElement('div');doc.body.append(host);root=host.attachShadow({mode:'open'});root.innerHTML=render(model,editable);}else doc.body.innerHTML=render(model,editable);
 window.eval(searchComboScript({richText:true}).replace(/^<script>|<\/script>$/g,'').split('bindDeclaredMockup(document);')[0]);window.bindDeclaredMockup(root);await tick();
 const board=root.querySelector('[data-help-board]'),kind=board.querySelector('[data-help-kind]'),format=board.querySelector('[data-help-format]'),body=board.querySelector('[data-help-body]'),save=root.querySelector('[data-command="저장"]');
 const click=async node=>{node.click();await tick();},input=async(node,text)=>{node.value=text;node.dispatchEvent(new window.Event('input',{bubbles:true}));await tick();},change=async(node,value)=>{node.value=value;node.dispatchEvent(new window.Event('change',{bubbles:true}));await tick();};
 const action=name=>board.querySelector(`[data-help-action="${name}"]`),dialog=name=>board.querySelector(`[data-help-dialog="${name}"]`),submit=async()=>{save.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));await tick();};
 return {dom,window,doc,root,board,kind,format,body,save,click,input,change,action,dialog,submit};
}
test('선언은 고정 세 구분과 단일 네 필드를 검증하고 작성 형식은 Markdown을 기본으로 한다',()=>{
 const model=fixture(),declaration={rows:[['설정',JSON.stringify(config)]]};assert.deepEqual(helpWorkspace(declaration,model),config);assert.equal(helpWorkspace(null,model),null);
 const withoutFormat=structuredClone(config);delete withoutFormat.types[0].format;assert.equal(helpWorkspace({rows:[['설정',JSON.stringify(withoutFormat)]]},model).types[0].format,'MARKDOWN');
 for(const bad of [{...config,types:config.types.slice(0,2)},{...config,types:[config.types[0],config.types[0],config.types[2]]},{...config,types:config.types.map(type=>({...type,format:'SCRIPT'}))}])assert.throws(()=>helpWorkspace({rows:[['설정',JSON.stringify(bad)]]},model));
 assert.throws(()=>helpWorkspace(declaration,{...model,columns:[...model.columns,model.columns[0]]}),/단일 폼/);
});
test('HTML 스타일은 값과 범위를 제한하며 외부 로드·고정위치·동적 CSS를 거부한다',()=>{
 for(const [property,value]of [['color','red'],['background-color','rgba(0, 0, 0, 0.5)'],['font-size','24px'],['font-weight','700'],['white-space','pre-wrap']])assert.equal(ctx.mockupRichTextStyle(property,value),value);
 for(const [property,value]of [['position','fixed'],['background-image','url(https://remote.invalid)'],['color','var(--x)'],['color','expression(alert(1))'],['font-size','999px'],['font-size','7px'],['color','rgba(999,0,0,0.5)'],['width','100vw'],['__proto__','x']])assert.equal(ctx.mockupRichTextStyle(property,value),'');
});
for(const shadow of [false,true])test(`구분 왕복의 형식·본문을 보존하고 원문을 자동 변환하지 않는다 (${shadow?'최종HTML':'독립문서'})`,async()=>{
 const ui=await mount({shadow});try{
 assert.equal(ui.root.querySelectorAll('[data-grid-view],[data-detail-tab]').length,0);assert.equal(ui.kind.options.length,3);assert.equal(ui.format.value,'MARKDOWN');
 const text='<b>동일 원문</b>\n두 번째 줄';await ui.input(ui.body,text);await ui.change(ui.format,'HTML');await ui.change(ui.format,'TEXT');assert.equal(ui.body.value,text);await ui.change(ui.kind,'CHAT_START');assert.equal(ui.format.value,'HTML');assert.equal(ui.body.value,'<p>처음 안내</p>');await ui.input(ui.body,'둘째 편집');await ui.change(ui.kind,'USAGE_HELP');assert.equal(ui.format.value,'TEXT');assert.equal(ui.body.value,text);await ui.change(ui.kind,'CHAT_START');assert.equal(ui.body.value,'둘째 편집');assert.match(ui.board.querySelector('[data-help-placement]').textContent,/새 대화 시작/);assert.equal(ui.board.querySelector('[data-help-revision]').textContent,'2');
 }finally{ui.dom.window.close();}
});
test('큰 편집 반영만 원문을 바꾸고 취소·ESC·구분 변경은 원본을 보존한다',async()=>{
 const ui=await mount();try{
 const original=ui.body.value,dialog=ui.dialog('edit'),buffer=dialog.querySelector('[data-help-buffer]');assert.equal(dialog.style.resize,'both');assert.equal(dialog.style.overflow,'auto');
 await ui.click(ui.action('edit'));await ui.input(buffer,'취소');assert.equal(ui.body.value,original);await ui.click(dialog.querySelector('[data-help-action="cancel"]'));assert.equal(ui.body.value,original);
 await ui.click(ui.action('edit'));await ui.input(buffer,'ESC');dialog.dispatchEvent(new ui.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));assert.equal(dialog.open,false);assert.equal(ui.body.value,original);
 await ui.click(ui.action('edit'));await ui.input(buffer,'반영');await ui.click(dialog.querySelector('[data-help-action="apply"]'));assert.equal(ui.body.value,'반영');
 await ui.click(ui.action('edit'));await ui.input(buffer,'잘못된 덮기');await ui.change(ui.kind,'CHAT_START');await ui.click(dialog.querySelector('[data-help-action="apply"]'));assert.equal(ui.body.value,'<p>처음 안내</p>');assert.match(dialog.querySelector('[data-help-modal-status]').textContent,/구분 또는 원문/);
 }finally{ui.dom.window.close();}
});
test('Markdown·HTML·TEXT는 안전한 표시만 하고 외부 미디어·스크립트·위험링크를 제외한다',async()=>{
 const ui=await mount();try{
 const preview=ui.dialog('preview').querySelector('[data-rich-text-preview]'),html='<h2 style="color:red;font-size:24px;position:fixed;background-image:url(https://remote.invalid/x);width:100vw">안내</h2><p style="text-align:center;font-weight:700">본문</p><script>window.BAD=true</script><iframe src="https://remote.invalid"></iframe><img src="https://remote.invalid/x" onerror="window.BAD=true"><style>@import "https://remote.invalid/x.css";</style><svg onload="window.BAD=true"></svg><a href="javascript:alert(1)" onclick="window.BAD=true">위험</a><a href="https://example.test/next">정상</a>';
 await ui.input(ui.body,html);await ui.change(ui.format,'HTML');await ui.click(ui.action('preview'));assert.equal(preview.querySelector('h2').style.color,'red');assert.equal(preview.querySelector('h2').style.fontSize,'24px');assert.equal(preview.querySelector('h2').style.position,'');assert.equal(preview.querySelector('h2').style.backgroundImage,'');assert.equal(preview.querySelector('p').style.textAlign,'center');assert.equal(preview.querySelector('script,iframe,img,style,svg,[onclick],[onerror]'),null);assert.ok(![...preview.querySelectorAll('a')].some(link=>/^javascript:/i.test(link.getAttribute('href')||'')));assert.equal(ui.window.BAD,undefined);
 const link=preview.querySelector('a[href^="https:"]'),event=new ui.window.MouseEvent('click',{bubbles:true,cancelable:true});link.dispatchEvent(event);assert.equal(event.defaultPrevented,true);
 await ui.click(ui.dialog('preview').querySelector('[data-help-action="close"]'));await ui.change(ui.format,'MARKDOWN');await ui.input(ui.body,'# 제목\n- 항목\n\n<script>bad()</script>\n![외부](https://remote.invalid/image)');await ui.click(ui.action('preview'));assert.ok(preview.querySelector('h1'));assert.ok(preview.querySelector('li'));assert.equal(preview.querySelector('script,img'),null);assert.match(preview.textContent,/<script>/);
 await ui.click(ui.dialog('preview').querySelector('[data-help-action="close"]'));await ui.change(ui.format,'TEXT');await ui.input(ui.body,'<b>문자 그대로</b>\n줄바꿈');await ui.click(ui.action('preview'));assert.equal(preview.textContent,'<b>문자 그대로</b>\n줄바꿈');assert.equal(preview.querySelector('b'),null);assert.equal(preview.firstElementChild.style.whiteSpace,'pre-wrap');
 }finally{ui.dom.window.close();}
});
test('저장 사유는 선택 구분·기준 버전에 고정하며 다른 편집본과 적용 버전을 보존한다',async()=>{
 const ui=await mount();try{
 await ui.change(ui.kind,'CHAT_START');await ui.input(ui.body,'둘째 보존');await ui.change(ui.kind,'USAGE_HELP');await ui.input(ui.body,'첫째 저장 예시');await ui.click(ui.action('save'));assert.equal(ui.save.open,true);const context=JSON.parse(ui.save.dataset.helpSaveContext);assert.equal(context.code,'USAGE_HELP');assert.equal(context.body,'첫째 저장 예시');assert.equal(context.revision,'3');assert.match(ui.save.querySelector('[aria-label="변경 대상"]').value,/USAGE_HELP.*기준 버전 3/);
 await ui.input(ui.save.querySelector('[aria-label="변경 사유"]'),'콘텐츠 문구 보완');await ui.submit();assert.equal(ui.save.dataset.commandConfirmed,'true');assert.match(ui.save.querySelector('[data-command-result]').textContent,/실제 서버 저장이나 버전 발급은 수행하지/);assert.equal(ui.board.querySelector('[data-help-revision]').textContent,'3');await ui.change(ui.kind,'CHAT_START');assert.equal(ui.body.value,'둘째 보존');
 }finally{ui.dom.window.close();}
});
test('저장창 이후 구분·원문·형식 변경과 공백/제거 후 빈 HTML은 저장을 막는다',async()=>{
 const ui=await mount();try{
 const reason=ui.save.querySelector('[aria-label="변경 사유"]');await ui.click(ui.action('save'));await ui.input(reason,'충분한 사유');await ui.input(ui.body,'창 열린 뒤 변경');await ui.submit();assert.equal(ui.save.dataset.commandConfirmed,'false');assert.match(ui.save.querySelector('[data-command-result]').textContent,/낡았습니다/);await ui.click(ui.save.querySelector('[data-close-command]'));
 await ui.click(ui.action('save'));await ui.change(ui.kind,'CHAT_START');await ui.submit();assert.match(ui.save.querySelector('[data-command-result]').textContent,/낡았습니다/);await ui.click(ui.save.querySelector('[data-close-command]'));
 await ui.click(ui.action('save'));await ui.change(ui.format,'TEXT');await ui.submit();assert.match(ui.save.querySelector('[data-command-result]').textContent,/낡았습니다/);await ui.click(ui.save.querySelector('[data-close-command]'));
 await ui.input(ui.body,'  ');await ui.click(ui.action('save'));assert.equal(ui.save.open,false);assert.match(ui.board.querySelector('[data-help-status]').textContent,/공백/);
 await ui.change(ui.format,'HTML');await ui.input(ui.body,'<script>alert(1)</script><img src="https://remote.invalid">');await ui.click(ui.action('save'));assert.equal(ui.save.open,false);assert.match(ui.board.querySelector('[data-help-status]').textContent,/표시할 본문이 없습니다/);
 }finally{ui.dom.window.close();}
});
test('선택 구분 재조회는 취소 시 보존하고 확인 시 그 구분만 되돌린다',async()=>{
 const ui=await mount();try{
 await ui.input(ui.body,'첫째 변경');await ui.change(ui.format,'TEXT');await ui.change(ui.kind,'CHAT_START');await ui.input(ui.body,'둘째 변경');await ui.change(ui.kind,'USAGE_HELP');await ui.click(ui.action('read'));assert.equal(ui.body.value,'첫째 변경');assert.equal(ui.format.value,'TEXT');assert.match(ui.board.querySelector('[data-help-status]').textContent,/조회 취소/);
 ui.window.confirm=()=>true;await ui.click(ui.action('read'));assert.equal(ui.body.value,config.types[0].body);assert.equal(ui.format.value,'MARKDOWN');await ui.change(ui.kind,'CHAT_START');assert.equal(ui.body.value,'둘째 변경');
 }finally{ui.dom.window.close();}
});
test('조회 전용은 이동·미리보기를 허용하고 편집·저장을 잠그며 denied는 본문을 노출하지 않는다',async()=>{
 const ui=await mount({editable:false});try{
 assert.equal(ui.kind.disabled,false);assert.equal(ui.format.disabled,true);assert.equal(ui.body.readOnly,true);assert.equal(ui.action('save').disabled,true);assert.equal(ui.action('edit').disabled,true);await ui.change(ui.kind,'INPUT_NOTICE');assert.equal(ui.body.value,config.types[2].body);await ui.click(ui.action('preview'));assert.equal(ui.dialog('preview').open,true);
 const denied=render(fixture(),false,true);assert.doesNotMatch(denied,/data-help-board|사용 도움말|업무를 질문하세요|처음 안내|개인정보를 확인하세요/);
 }finally{ui.dom.window.close();}
});
test('스타일 범위 제한을 뒤집으면 안전 표시 반례가 실패한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(richSource.replace('Number(match[1])<=48','Number(match[1])<=9999'),mutant);assert.throws(()=>assert.equal(mutant.mockupRichTextStyle('font-size','999px'),''));
});
test('HTML 검증 도구가 없으면 원문을 보존하고 저장 확인을 막는다',async()=>{
 const ui=await mount();try{
 await ui.change(ui.kind,'CHAT_START');const original=ui.body.value;ui.window.DOMPurify=undefined;await ui.click(ui.action('save'));assert.equal(ui.save.open,false);assert.equal(ui.body.value,original);assert.match(ui.board.querySelector('[data-help-status]').textContent,/검증 도구를 불러오지 못했습니다/);
 }finally{ui.dom.window.close();}
});
