import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import vm from 'node:vm';import {createRequire} from 'node:module';
import {promptRuleWorkspace,promptRuleBrowserAssets,promptRuleMarkdownScripts} from './prompt-rule-workspace.mjs';import {declaredScreen,searchComboScript} from './declared-layout.mjs';
const frontend=createRequire(new URL('../../../sources/frontend/package.json',import.meta.url)),{JSDOM}=frontend('jsdom');
const source=readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-prompt-rule.js',import.meta.url),'utf8'),ctx=vm.createContext({});vm.runInContext(source,ctx);const plain=value=>JSON.parse(JSON.stringify(value));
const config={list:'규칙 목록',form:'규칙 편집',id:'규칙 식별자',title:'제목',enabled:'사용 여부',description:'규칙 설명',body:'본문'};
const records=[{'규칙 식별자':'402',제목:'둘째','사용 여부':true,'규칙 설명':'관리자 설명 둘째',본문:'둘째 본문'},{'규칙 식별자':'401',제목:'첫째','사용 여부':true,'규칙 설명':'관리자 설명 첫째',본문:'# 업무 근거\n- 확인한 근거로 답변합니다.\n- 불명확한 내용은 질문합니다.'},{'규칙 식별자':'403',제목:'미사용','사용 여부':false,'규칙 설명':'미사용 관리자 설명',본문:'제외할 본문'}];
const table=(heading,rows)=>({heading,header:['컬럼','입력방식','편집','검증','예시','기본 표시','그리드 타입'],rows});
function fixture(){return {interactive:true,vertical:false,promptRuleWorkspace:config,strictButtons:true,search:[],addRowTabs:[config.list],
 columns:[table(config.list,[[config.id,'조회','읽기전용','','401','선택 표시','bigint'],[config.title,'텍스트','가능','','첫째','기본 표시','text'],[config.enabled,'설정 스위치','가능','','true','기본 표시','ynFlag'],...['등록자','등록일시','수정자','수정일시'].map(name=>[name,'조회','읽기전용','','감사 예시','기본 표시','text'])]),table(config.form+' — 폼',[[config.description,'장문','가능','',''],[config.body,'장문','가능','','']])],
 actions:['조회','추가','저장','전체 규칙 검토','본문 크게 편집','Markdown 미리보기','반영','취소','검토 실행','닫기'].map(name=>[name,'','','','',name+' 설명']),
 surfaces:[{name:config.list,buttons:['조회','추가','저장','전체 규칙 검토']},{name:config.form,buttons:['본문 크게 편집','Markdown 미리보기']}],
 rowDefaults:{[config.list]:{'사용 여부':false,제목:''}},rowExamples:{rows:records.map(record=>[config.list,JSON.stringify(record)])},
 commandFields:{header:['명령 입력창','영역','필드','입력방식','필수','예시'],rows:[['저장','기본','변경 대상','조회','선택','프롬프트 규칙'],['저장','기본','변경 사유','장문','필수','—']]},saveTargets:{rows:[[config.list,'PROMPT_RULES','프롬프트 규칙']]}
};}
const figure={key:'main',caption:'기본',detail:config.form,notice:'검토 예시',buttons:['조회','추가','저장','전체 규칙 검토','본문 크게 편집','Markdown 미리보기'],editable:true,examples:{}};
const render=(model,editable=true)=>declaredScreen('프롬프트 규칙',model,{...figure,editable,buttons:editable?figure.buttons:['조회','Markdown 미리보기']},{plain:value=>String(value??''),searchControl:()=>'',manualKey:false,annotator:()=>({badge:()=>'<span data-test-badge></span>',render:()=>''})});
const tick=async()=>{await Promise.resolve();await Promise.resolve();await Promise.resolve();};
async function mount({shadow=false,editable=true,model=fixture()}={}){
 const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://example.test/',runScripts:'outside-only'}),{window}=dom,doc=window.document;
 window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};window.HTMLDialogElement.prototype.close=function(){this.open=false;};
 let root=doc;if(shadow){const host=doc.createElement('div');doc.body.append(host);root=host.attachShadow({mode:'open'});root.innerHTML=render(model,editable);}else doc.body.innerHTML=render(model,editable);
 const script=searchComboScript({promptRule:true}).replace(/^<script>|<\/script>$/g,'').split('bindDeclaredMockup(document);')[0];window.eval(script);window.bindDeclaredMockup(root);await tick();
 const board=root.querySelector('[data-prompt-board]'),grid=board.querySelector('[data-grid-view]'),rows=()=>[...grid.querySelectorAll('tbody tr[data-grid-row-state]')];
 const cell=(row,key)=>[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===key),click=async node=>{node.click();await tick();},input=async(field,text)=>{field.value=text;field.dispatchEvent(new window.Event('input',{bubbles:true}));await tick();};
 const edit=async(target,text)=>{const input=target.querySelector('[data-edit-value]');input.closest('details').open=true;input.value=text;input.dispatchEvent(new window.FocusEvent('focusout',{bubbles:true,relatedTarget:doc.body}));await tick();};
 const field=name=>board.querySelector(`[data-form-value="${name}"]`),dialog=name=>root.querySelector(`[data-prompt-dialog="${name}"]`);
 return {dom,root,window,board,grid,rows,cell,click,input,edit,field,dialog};
}
test('작업대 설정은 두 영역·일곱 키를 확인하며 브라우저 번들은 로컬 파일이다',()=>{
 assert.deepEqual(promptRuleWorkspace({rows:[['설정',JSON.stringify(config)]]},fixture()),config);assert.equal(promptRuleWorkspace(null,fixture()),null);
 assert.throws(()=>promptRuleWorkspace({rows:[['설정',JSON.stringify({...config,form:'없음'})]]},fixture()),/목록·폼/);
 assert.throws(()=>promptRuleWorkspace({rows:[['설정','{}']]},fixture()),/누락/);
 assert.ok(promptRuleBrowserAssets().every(asset=>readFileSync(asset.path,'utf8').length>1000));assert.ok(promptRuleMarkdownScripts().length>50000);assert.doesNotMatch(promptRuleMarkdownScripts(),/<\/script/i);
});
test('숫자 ID 정밀도·안정 순서를 유지하고 사용 본문·제목만 조합한다',()=>{
 const data=[...records,{'규칙 식별자':'9007199254740993',제목:'큰 ID','사용 여부':true,본문:'큰 본문'},{'규칙 식별자':'9007199254740992',제목:'직전 ID','사용 여부':true,본문:'직전 본문'},{'규칙 식별자':null,제목:'신규2','사용 여부':true,본문:'신규 둘',__clientOrder:2},{'규칙 식별자':null,제목:'신규1','사용 여부':true,본문:'신규 하나',__clientOrder:1}];
 assert.deepEqual(plain(ctx.mockupPromptOrder(data,config)).map(record=>record.제목),['첫째','둘째','미사용','직전 ID','큰 ID','신규1','신규2']);
 const text=ctx.mockupPromptReview(data,config);assert.ok(text.indexOf('## 첫째')<text.indexOf('## 둘째'));assert.doesNotMatch(text,/관리자 설명|제외할 본문|403|등록자/);assert.match(text,/## 신규1/);
 assert.equal(ctx.mockupPromptReview([{...records[0],__deleted:true}],config),'');
 const signature=ctx.mockupPromptFingerprint(data,config);assert.equal(ctx.mockupPromptFingerprint(data.map(record=>({...record,'규칙 설명':'다른 관리자 설명'})),config),signature);
 for(const key of ['제목','본문','사용 여부'])assert.notEqual(ctx.mockupPromptFingerprint(data.map((record,index)=>index?record:{...record,[key]:'변경'}),config),signature);
 assert.notEqual(ctx.mockupPromptFingerprint([...data,{제목:'새 규칙','사용 여부':false}],config),signature);
});
test('미사용 본문 포함으로 업무 규칙을 뒤집으면 제외 반례가 실패한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace('mockupPromptEnabled(record[config.enabled])','true'),mutant);assert.throws(()=>assert.doesNotMatch(mutant.mockupPromptReview(records,config),/제외할 본문/));
});
for(const shadow of [false,true])test(`행별 입력·신규·삭제·원복과 스플리터를 보존한다 (${shadow?'최종HTML':'독립문서'})`,async()=>{
 const ui=await mount({shadow});try{
 assert.equal(ui.root.querySelectorAll('[data-detail-tab]').length,0);assert.equal(ui.rows()[0].querySelector('[data-row-field="규칙 식별자"]').dataset.rowValue,'401');assert.equal(ui.cell(ui.rows()[0],config.id).hidden,true);assert.equal(ui.cell(ui.rows()[0],config.id).dataset.gridType,'bigint');
 const first=ui.rows()[0],second=ui.rows()[1];await ui.input(ui.field(config.body),'첫째 본문 변경');await ui.input(ui.field(config.description),'첫째 관리자 설명 변경');await ui.click(second);assert.equal(ui.field(config.body).value,'둘째 본문');await ui.input(ui.field(config.body),'둘째 변경');await ui.click(first);assert.equal(ui.field(config.body).value,'첫째 본문 변경');
 await ui.click(first.querySelector('[data-revert-row]'));assert.equal(ui.field(config.body).value,records[1].본문);await ui.click(second);assert.equal(ui.field(config.body).value,'둘째 변경');
 await ui.click(ui.grid.querySelector('[data-grid-add]'));const created=ui.rows().find(row=>row.dataset.gridRowState==='C');assert.equal(ui.cell(created,config.id).dataset.rowValue,'');assert.equal(ui.field(config.body).value,'');assert.equal(ui.cell(created,config.enabled).dataset.rowValue,'false');await ui.input(ui.field(config.body),'새 본문');await ui.click(first);await ui.click(created);assert.equal(ui.field(config.body).value,'새 본문');await ui.click(created.querySelector('[data-grid-delete]'));assert.ok(!created.isConnected);assert.notEqual(ui.field(config.body).value,'새 본문');
 const handle=ui.board.querySelector('[data-prompt-splitter]');handle.dispatchEvent(new ui.window.KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true}));assert.equal(handle.getAttribute('aria-valuenow'),'55');
 }finally{ui.dom.window.close();}
});
test('큰 본문 편집은 반영만 적용하고 취소·Escape·선택 변경은 원본을 보존한다',async()=>{
 const ui=await mount();try{
 const original=ui.field(config.body).value,open=ui.board.querySelector('[data-prompt-action="edit"]'),dialog=ui.dialog('edit'),buffer=dialog.querySelector('[data-prompt-buffer]');
 await ui.click(open);await ui.input(buffer,'취소 본문');assert.equal(ui.field(config.body).value,original);await ui.click(dialog.querySelector('[data-prompt-action="cancel"]'));assert.equal(ui.field(config.body).value,original);
 await ui.click(open);await ui.input(buffer,'ESC 본문');dialog.dispatchEvent(new ui.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await tick();assert.equal(dialog.open,false);assert.equal(ui.field(config.body).value,original);
 await ui.click(open);await ui.input(buffer,'반영 본문');await ui.click(dialog.querySelector('[data-prompt-action="apply"]'));assert.equal(ui.field(config.body).value,'반영 본문');
 await ui.click(open);await ui.input(buffer,'잘못된 대상');await ui.click(ui.rows()[1]);await ui.click(dialog.querySelector('[data-prompt-action="apply"]'));assert.match(dialog.querySelector('[data-prompt-modal-status]').textContent,/선택 또는 변경 권한/);assert.equal(ui.field(config.body).value,'둘째 본문');
 }finally{ui.dom.window.close();}
});
test('Markdown은 제목·표·목록·코드·링크를 표시하고 실행 가능한 HTML·링크·미디어를 만들지 않는다',async()=>{
 const ui=await mount();try{
 const markdown='# 제목\n\n- 목록\n\n```js\nalert(1)\n```\n\n| 열 | 값 |\n|---|---|\n| A | B |\n\n[정상](https://example.test) [위험](javascript:alert(1))\n\n![그림](https://remote.invalid/a.png)\n\n<script>window.INJECTED=true</script><img src=x onerror=alert(1)><iframe src="https://remote.invalid"></iframe>';
 await ui.input(ui.field(config.description),'노출되면 안 되는 관리자 설명');await ui.input(ui.field(config.body),markdown);await ui.click(ui.board.querySelector('[data-prompt-action="preview"]'));const preview=ui.dialog('preview').querySelector('[data-prompt-preview]');
 assert.ok(preview.querySelector('h1'));assert.ok(preview.querySelector('li'));assert.ok(preview.querySelector('pre code'));assert.ok(preview.querySelector('table'));assert.equal(preview.querySelector('a[href^="https:"]').textContent,'정상');assert.equal(preview.querySelector('script,img,iframe,video,audio,object,style,[onerror]'),null);assert.ok(![...preview.querySelectorAll('a')].some(link=>/^javascript:/i.test(link.getAttribute('href')||'')));assert.doesNotMatch(preview.textContent,/노출되면 안 되는/);assert.equal(ui.window.INJECTED,undefined);
 }finally{ui.dom.window.close();}
});
test('전체 검토는 숨긴 행도 포함하고 전송 제외 설명만 바뀌면 낡지 않는다',async()=>{
 const ui=await mount();try{
 ui.rows()[1].hidden=true;const open=ui.board.querySelector('[data-prompt-action="review"]'),dialog=ui.dialog('review');await ui.click(open);const prepared=dialog.querySelector('[data-prompt-review-input]');assert.equal(prepared.readOnly,true);assert.match(prepared.value,/둘째 본문/);assert.doesNotMatch(prepared.value,/관리자 설명|제외할 본문/);await ui.click(dialog.querySelector('[data-prompt-action="execute"]'));assert.equal(dialog.querySelector('[data-prompt-review-result]').textContent,'목업: 가공된 검토 요청, 실제 호출은 구현 후');await ui.click(dialog.querySelector('[data-prompt-action="close"]'));
 await ui.input(ui.field(config.description),'설명 변경');assert.match(ui.board.querySelector('[data-prompt-review-status]').textContent,/전송할 내용이 같습니다/);
 await ui.input(ui.field(config.body),'본문 변경');assert.match(ui.board.querySelector('[data-prompt-review-status]').textContent,/낡았습니다/);await ui.click(open);await ui.input(ui.field(config.body),'모달 열린 뒤 변경');await ui.click(dialog.querySelector('[data-prompt-action="execute"]'));assert.match(dialog.querySelector('[data-prompt-review-result]').textContent,/낡았습니다/);
 }finally{ui.dom.window.close();}
});
test('조회 전용은 본문·추가·사용스위치·큰 편집을 잠그고 미리보기는 유지한다',async()=>{
 const ui=await mount({editable:false});try{
 assert.equal(ui.field(config.body).readOnly,true);assert.equal(ui.field(config.description).readOnly,true);assert.equal(ui.board.querySelector('[data-prompt-action="edit"]').disabled,true);for(const button of ui.grid.querySelectorAll('[data-grid-add],[data-config-switch]'))assert.equal(button.disabled,true);
 await ui.click(ui.board.querySelector('[data-prompt-action="preview"]'));assert.equal(ui.dialog('preview').open,true);
 }finally{ui.dom.window.close();}
});
test('사용 ON 제목·본문 누락과 전체 빈 사용 목록은 검토 실행을 막는다',async()=>{
 const ui=await mount();try{
 const open=ui.board.querySelector('[data-prompt-action="review"]'),dialog=ui.dialog('review');await ui.input(ui.field(config.body),'   ');assert.match(ui.board.querySelector('[data-prompt-review-status]').textContent,/제목 또는 본문이 비어/);await ui.click(open);assert.equal(dialog.querySelector('[data-prompt-action="execute"]').disabled,true);assert.match(dialog.querySelector('[data-prompt-review-result]').textContent,/실행할 수 없습니다/);await ui.click(dialog.querySelector('[data-prompt-action="close"]'));
 for(const row of ui.rows())if(ui.cell(row,config.enabled).dataset.rowValue==='true')await ui.click(ui.cell(row,config.enabled).querySelector('[data-config-switch]'));await ui.click(open);assert.equal(dialog.querySelector('[data-prompt-review-input]').value,'');assert.equal(dialog.querySelector('[data-prompt-action="execute"]').disabled,true);assert.match(dialog.querySelector('[data-prompt-review-result]').textContent,/사용 ON인 규칙이 없습니다/);
 }finally{ui.dom.window.close();}
});
test('본문 타이핑 중 정렬된 목록 행을 다시 삽입하지 않는다',async()=>{
 const ui=await mount();try{
 const body=ui.grid.querySelector('tbody');let mutations=0;const observer=new ui.window.MutationObserver(records=>{mutations+=records.filter(record=>record.type==='childList'&&record.target===body).length;});observer.observe(body,{childList:true});await ui.input(ui.field(config.body),'포커스 유지');await tick();observer.disconnect();assert.equal(mutations,0);
 }finally{ui.dom.window.close();}
});
