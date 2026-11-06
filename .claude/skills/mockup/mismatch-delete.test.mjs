import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import {createRequire} from 'node:module';import vm from 'node:vm';
import {mismatchDeleteWorkspace} from './mismatch-delete.mjs';import {declaredScreen,searchComboScript} from './declared-layout.mjs';
test('일괄 정리만 제공한 마스터·연결에 일반 행 삭제를 만들지 않는다',async()=>{
 const ui=await mount();try{assert.equal(ui.grid('대상 시스템 목록').querySelector('[data-grid-delete]'),null);assert.equal(ui.grid('연결 정보').querySelector('[data-grid-delete]'),null);}finally{ui.dom.window.close();}
});
test('같은 연결키라도 부모가 다르면 별도 후보이며 선택 부모만 정리한다',async()=>{
 const model=fixture(),scope=model.mismatchDeleteWorkspace.scopes[1],copy=record('ORDER-AUTH','다른 시스템의 같은 연결키','CONNECTION','MISSING',{parent:'SYS-ATTR',references:['다른 시스템 API']});
 scope.records.push(copy);model.rowExamples.rows.push(['연결 정보',JSON.stringify({'연결 식별자':copy.id,'연결명':copy.name,'문맥':copy.parent,'서버 설정 불일치':'불일치'})]);
 assert.doesNotThrow(()=>mismatchDeleteWorkspace({rows:[['설정',JSON.stringify(model.mismatchDeleteWorkspace)]]},model));
 const duplicate=structuredClone(model.mismatchDeleteWorkspace);duplicate.scopes[1].records.push(copy);assert.throws(()=>mismatchDeleteWorkspace({rows:[['설정',JSON.stringify(duplicate)]]},model));
 const ui=await mount({model});try{await ui.open(1);await ui.submit();const rows=ui.rows('연결 정보').filter(row=>ui.cell(row,'연결 식별자').dataset.rowValue==='ORDER-AUTH');assert.equal(rows.length,1);assert.deepEqual(JSON.parse(rows[0].dataset.rowContext),['SYS-ATTR']);const kept=JSON.parse(ui.marker.dataset.mismatchDeleteConfig).scopes[1].records.filter(r=>r.id==='ORDER-AUTH');assert.deepEqual(kept.map(r=>r.parent),['SYS-ATTR']);}finally{ui.dom.window.close();}
});
const frontend=createRequire(new URL('../../../sources/frontend/package.json',import.meta.url)),{JSDOM}=frontend('jsdom');
const source=readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-mismatch-delete.js',import.meta.url),'utf8'),ctx=vm.createContext({});vm.runInContext(source,ctx);const plain=value=>JSON.parse(JSON.stringify(value));
const action='서버 설정 불일치 건 일괄 삭제';
const record=(id,name,kind,definition='MISSING',extra={})=>({id,name,kind,definition,reason:definition==='MISSING'?'현재 서버 설정에 정확한 정의 없음':'정확한 누락 정의가 아님',mismatch:definition==='PRESENT'?'불일치':'불일치',parent:null,version:'2',confirmed:true,permission:true,references:[],...extra});
const scope=(area,kind,key,name,parent,records)=>({area,kind,key,name,parent,filters:[],completeCurrent:true,safeDeployment:true,permission:true,previewState:'정상',executionState:'정상',configurationVersion:'cfg-1',diagnosisVersion:'diag-1',referenceVersion:'ref-1',deploymentEvidence:'허용 구세대 없음·진행/대기 미사용·배출 확인 예시',records});
const table=(heading,rows)=>({heading,header:['컬럼','입력방식','편집','검증','예시'],rows});
function fixture(datasource=false){
 const systemRecords=[record('SYS-ORDER','주문 시스템','SYSTEM','PRESENT'),record('SYS-LEGACY','이전 시스템','SYSTEM'),record('SYS-OFFPAGE','페이지 밖 이전 시스템','SYSTEM'),record('SYS-UNKNOWN','확인 불가 시스템','SYSTEM','UNKNOWN',{confirmed:false,permission:null,references:null}),record('SYS-ATTR','속성 다른 시스템','SYSTEM','PROPERTY_MISMATCH')];
 const connectionRecords=[record('ORDER-PUBLIC','공개 연결','CONNECTION','PRESENT',{parent:'SYS-ORDER'}),record('ORDER-AUTH','인증 연결','CONNECTION','MISSING',{parent:'SYS-ORDER'}),record('ORDER-UNKNOWN','미확인 연결','CONNECTION','UNKNOWN',{parent:'SYS-ORDER',confirmed:false,references:null}),record('LEGACY-API','이전 연결','CONNECTION','MISSING',{parent:'SYS-LEGACY'}),record('OTHER-AUTH','다른 시스템 연결','CONNECTION','MISSING',{parent:'SYS-ATTR',references:['사용 API']})];
 const scopes=datasource?[scope('데이터소스 목록','DATASOURCE','데이터소스 키','별칭',null,[record('DS-MISSING','이전 DS','DATASOURCE'),record('DS-OFFPAGE','페이지 밖 DS','DATASOURCE'),record('DS-PRESENT','현재 DS','DATASOURCE','PRESENT'),record('DS-DBMS','DBMS 다른 DS','DATASOURCE','DBMS_MISMATCH'),record('DS-FAIL','접속 실패 DS','DATASOURCE','CONNECTION_FAILED')])]:[scope('대상 시스템 목록','SYSTEM','시스템 식별자','시스템명',null,systemRecords),scope('연결 정보','CONNECTION','연결 식별자','연결명','시스템 식별자',connectionRecords)];
 scopes[0].filters=[{label:datasource?'별칭':'시스템명',field:'name',match:'contains'},{label:'서버 설정 불일치',field:'mismatch',match:'eq'}];if(!datasource)scopes[1].filters=[{label:'연결명',field:'name',match:'contains'}];
 const columns=datasource?[table('데이터소스 목록',[['데이터소스 키','조회','읽기전용','','DS-MISSING'],['별칭','텍스트','가능','','이전 DS'],['설명','텍스트','가능','','보완값'],['서버 설정 불일치','배지','읽기전용','','불일치']])]:[table('대상 시스템 목록',[['시스템 식별자','조회','읽기전용','','SYS-ORDER'],['시스템명','텍스트','가능','','주문 시스템'],['설명','텍스트','가능','','보완값'],['서버 설정 불일치','배지','읽기전용','','불일치']]),table('연결 정보',[['연결 식별자','조회','읽기전용','','ORDER-PUBLIC'],['연결명','텍스트','가능','','공개 연결'],['설명','텍스트','가능','','보완값'],['서버 설정 불일치','배지','읽기전용','','불일치']]),table('사용 API 정보',[['API ID','조회','읽기전용','','A1'],['API명','조회','읽기전용','','공개 API']])];
 const rowExamples={rows:scopes.flatMap((scope,index)=>scope.records.filter(record=>!record.id.includes('OFFPAGE')).map(record=>[scope.area,JSON.stringify({[scope.key]:record.id,[scope.name]:record.name,설명:'보완값','서버 설정 불일치':record.mismatch,...(index?{문맥:record.parent}:{})})]))};
 if(!datasource)rowExamples.rows.push(['사용 API 정보',JSON.stringify({'API ID':'A1',API명:'공개 API',문맥:'SYS-ORDER'})],['사용 API 정보',JSON.stringify({'API ID':'A2',API명:'이전 API',문맥:'SYS-LEGACY'})]);
 const searchTable={heading:'조회조건',header:['조건','입력방식','선택 출처'],rows:[[datasource?'별칭':'시스템명','텍스트','직접 입력'],['서버 설정 불일치','선택','불일치·일치·확인 필요']]};
 return {mismatchDeleteWorkspace:{action,scopes},interactive:!datasource,vertical:true,masterLink:datasource?undefined:'시스템 식별자',strictButtons:true,searchTable,search:searchTable.rows,detailSearchTables:datasource?[]:[{heading:'연결 정보',header:['조건','입력방식'],rows:[['연결명','텍스트']]}],columns,rowExamples,surfaces:columns.map((table,index)=>({name:table.heading,buttons:index===2?['조회']:['조회','저장',action]})),actions:['조회','저장',action,'일괄 삭제 실행','취소'].map(name=>[name,'','','','',name+' 설명'])};
}
const figure={key:'main',caption:'기본',detail:'연결 정보',masterValue:'SYS-ORDER',notice:'정적 예시',buttons:['조회','저장',action],editable:true,examples:{}};
const render=(model,editable=true)=>declaredScreen('불일치 정리',model,{...figure,editable,detail:model.columns.length===1?'없음':figure.detail,buttons:editable?figure.buttons:['조회']},{plain:value=>String(value??''),searchControl:()=>'',manualKey:false,annotator:()=>({badge:()=>'',render:()=>''})});
const tick=async()=>{await Promise.resolve();await Promise.resolve();};
async function mount({datasource=false,shadow=false,editable=true,model=fixture(datasource)}={}){
 const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://example.test/',runScripts:'outside-only'}),{window}=dom,doc=window.document;
 window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};window.HTMLDialogElement.prototype.close=function(){this.open=false;};
 let root=doc;if(shadow){const host=doc.createElement('div');doc.body.append(host);root=host.attachShadow({mode:'open'});root.innerHTML=render(model,editable);}else doc.body.innerHTML=render(model,editable);
 window.eval(searchComboScript().replace(/^<script>|<\/script>$/g,'').split('bindDeclaredMockup(document);')[0]);window.bindDeclaredMockup(root);await tick();
 const marker=root.querySelector('[data-mismatch-delete-config]'),dialog=root.querySelector('[data-mismatch-delete-dialog]'),grids=()=>[...root.querySelectorAll('[data-grid-name]')],grid=name=>grids().find(grid=>grid.dataset.gridName===name),rows=name=>[...grid(name).querySelectorAll('tbody tr[data-grid-row-state]')],cell=(row,key)=>[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===key);
 const click=async node=>{node.click();await tick();},input=async(node,value)=>{node.value=value;node.dispatchEvent(new window.Event('input',{bubbles:true}));await tick();},change=async(node,value)=>{node.value=value;node.dispatchEvent(new window.Event('change',{bubbles:true}));await tick();};
 const open=async(index=0)=>click(root.querySelector(`[data-mismatch-delete-open="${index}"]`)),mutate=callback=>{const next=JSON.parse(marker.dataset.mismatchDeleteConfig);callback(next);marker.dataset.mismatchDeleteConfig=JSON.stringify(next);};
 const submit=async(reason='현재 서버 정의에서 제거된 잔재 정리',confirm=true)=>{dialog.querySelector('[data-mismatch-delete-reason]').value=reason;dialog.querySelector('[data-mismatch-delete-confirm]').checked=confirm;dialog.querySelector('form').dispatchEvent(new window.Event('submit',{bubbles:true,cancelable:true}));await tick();};
 return {dom,window,doc,root,marker,dialog,grid,rows,cell,click,input,change,open,mutate,submit};
}
test('선언은 정확한 키·종류·판정·참조·신뢰 근거를 요구한다',()=>{
 const model=fixture(),decl={rows:[['설정',JSON.stringify(model.mismatchDeleteWorkspace)]]};assert.deepEqual(mismatchDeleteWorkspace(decl,model),model.mismatchDeleteWorkspace);assert.equal(mismatchDeleteWorkspace(null,model),null);
 for(const modify of [config=>delete config.scopes[0].completeCurrent,config=>config.scopes[0].records[0].kind='CONNECTION',config=>config.scopes[0].records.push(config.scopes[0].records[0]),config=>config.scopes[0].records[0].references='미확인',config=>config.scopes[0].parent='없는 필드']){const config=structuredClone(model.mismatchDeleteWorkspace);modify(config);assert.throws(()=>mismatchDeleteWorkspace({rows:[['설정',JSON.stringify(config)]]},model));}
});
test('집계 불일치·UNKNOWN·속성/DBMS/접속 실패는 제외하고 정확 MISSING만 선택한다',()=>{
 const model=fixture(),scope=model.mismatchDeleteWorkspace.scopes[0],plan=plain(ctx.mockupMismatchDeletePlan(scope,null,{}));assert.deepEqual(plan.candidates.map(record=>record.id),['SYS-LEGACY','SYS-OFFPAGE']);assert.equal(plan.allowed,true);assert.ok(plan.excluded.some(record=>record.id==='SYS-ORDER'));assert.ok(plan.excluded.some(record=>record.definition==='UNKNOWN'));
 const ds=fixture(true).mismatchDeleteWorkspace.scopes[0];assert.deepEqual(plain(ctx.mockupMismatchDeletePlan(ds,null,{})).candidates.map(record=>record.id),['DS-MISSING','DS-OFFPAGE']);
 const child=model.mismatchDeleteWorkspace.scopes[1];assert.deepEqual(plain(ctx.mockupMismatchDeletePlan(child,'SYS-ORDER',{})).candidates.map(record=>record.id),['ORDER-AUTH']);assert.throws(()=>ctx.mockupMismatchDeletePlan(child,'',{}),/상위/);
});
test('한 후보의 참조·권한·판정 문제나 전역 근거 불확실성은 전체 실행을 막는다',()=>{
 const original=fixture().mismatchDeleteWorkspace.scopes[0];
 for(const modify of [scope=>scope.records[1].references=['업무 API 참조'],scope=>scope.records[1].references=null,scope=>scope.records[1].permission=false,scope=>scope.records[1].confirmed=false,scope=>scope.completeCurrent=false,scope=>scope.safeDeployment=false,scope=>scope.permission=false]){const scope=structuredClone(original);modify(scope);const plan=ctx.mockupMismatchDeletePlan(scope,null,{});assert.equal(plan.allowed,false);assert.ok(plan.blockers.length);assert.equal(plan.candidates.length,2);}
});
test('집계 배지만으로 후보를 고르는 반전은 정상 부모 삭제 반례를 통과하지 못한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace("record.definition==='MISSING'&&record.kind===scope.kind","record.mismatch==='불일치'&&record.kind===scope.kind"),mutant);assert.throws(()=>assert.ok(!mutant.mockupMismatchDeletePlan(fixture().mismatchDeleteWorkspace.scopes[0],null,{}).candidates.some(record=>record.id==='SYS-ORDER')));
});
for(const shadow of [false,true])test(`상단은 미선택·페이지 밖 후보까지 미리보고 취소/새사유를 보존한다 (${shadow?'최종HTML':'독립문서'})`,async()=>{
 const ui=await mount({shadow});try{
 ui.rows('대상 시스템 목록').forEach(row=>row.classList.remove('sel'));const before=ui.rows('대상 시스템 목록').map(row=>row.outerHTML);await ui.open();assert.equal(ui.dialog.open,true);assert.match(ui.dialog.querySelector('[data-mismatch-delete-results]').textContent,/SYS-OFFPAGE/);assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/후보 2건/);assert.match(ui.dialog.querySelector('[data-mismatch-delete-scope]').textContent,/선택행\/페이지와 무관/);
 await ui.input(ui.dialog.querySelector('[data-mismatch-delete-reason]'),'취소할 사유');await ui.click(ui.dialog.querySelector('[data-mismatch-delete-action="cancel"]'));assert.deepEqual(ui.rows('대상 시스템 목록').map(row=>row.outerHTML),before);await ui.open();assert.equal(ui.dialog.querySelector('[data-mismatch-delete-reason]').value,'');assert.equal(ui.dialog.querySelector('[data-mismatch-delete-confirm]').checked,false);
 }finally{ui.dom.window.close();}
});
test('조회조건 전체 범위를 적용하고 선택과 무관하게 시스템 및 하단 고아만 정리한다',async()=>{
 const ui=await mount();try{
 const history=ui.doc.createElement('p');history.dataset.historyFixture='';history.textContent='SYS-LEGACY 과거 이력';ui.doc.body.append(history);
 await ui.input(ui.root.querySelector('input[aria-label="시스템명"]'),'이전');await ui.open();assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/후보 2건/);await ui.submit('  ');assert.equal(ui.dialog.open,true);assert.match(ui.dialog.querySelector('[data-mismatch-delete-reason]').validationMessage,/공백/);await ui.submit('누락 잔재 정리',false);assert.equal(ui.rows('대상 시스템 목록').length,4);await ui.submit();
 assert.equal(ui.dialog.open,false);assert.ok(!ui.rows('대상 시스템 목록').some(row=>ui.cell(row,'시스템 식별자').dataset.rowValue==='SYS-LEGACY'));assert.ok(ui.rows('대상 시스템 목록').some(row=>ui.cell(row,'시스템 식별자').dataset.rowValue==='SYS-ORDER'));assert.ok(!ui.rows('연결 정보').some(row=>ui.cell(row,'연결 식별자').dataset.rowValue==='LEGACY-API'));assert.equal(ui.rows('사용 API 정보').length,1);assert.ok(history.isConnected);assert.match(ui.root.querySelector('[data-mismatch-delete-notice]').textContent,/2건.*실제 DB\/API 삭제/);assert.ok(ui.root.querySelector('[data-grid-role="master"] tr.sel'));
 }finally{ui.dom.window.close();}
});
test('연결 정리는 선택 부모의 정확 연결만 지우고 정상 연결과 다른 부모를 보존한다',async()=>{
 const ui=await mount();try{
 await ui.open(1);assert.match(ui.dialog.querySelector('[data-mismatch-delete-scope]').textContent,/SYS-ORDER/);assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/후보 1건/);assert.doesNotMatch(ui.dialog.querySelector('[data-mismatch-delete-results]').textContent,/OTHER-AUTH/);await ui.submit();assert.ok(!ui.rows('연결 정보').some(row=>ui.cell(row,'연결 식별자').dataset.rowValue==='ORDER-AUTH'));assert.ok(ui.rows('연결 정보').some(row=>ui.cell(row,'연결 식별자').dataset.rowValue==='ORDER-PUBLIC'));assert.ok(ui.rows('연결 정보').some(row=>ui.cell(row,'연결 식별자').dataset.rowValue==='OTHER-AUTH'));assert.equal(ui.rows('대상 시스템 목록').length,4);
 }finally{ui.dom.window.close();}
});
test('데이터소스 정리는 registry MISSING만 제거하며 DBMS·접속 실패는 보존한다',async()=>{
 const ui=await mount({datasource:true});try{
 await ui.open();assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/후보 2건/);await ui.submit();assert.deepEqual(ui.rows('데이터소스 목록').map(row=>ui.cell(row,'데이터소스 키').dataset.rowValue),['DS-PRESENT','DS-DBMS','DS-FAIL']);
 }finally{ui.dom.window.close();}
});
test('참조 차단은 일부 후보도 제거하지 않으며 조회 실패·0건·실행 실패를 구분한다',async()=>{
 for(const state of ['reference','preview','empty','execute']){
 const ui=await mount();try{
 ui.mutate(config=>{if(state==='reference')config.scopes[0].records[1].references=['현재 사용 API'];if(state==='preview')config.scopes[0].previewState='실패';if(state==='empty')config.scopes[0].records.forEach(record=>record.definition='PRESENT');if(state==='execute')config.scopes[0].executionState='실패';});const before=ui.rows('대상 시스템 목록').map(row=>row.outerHTML);await ui.open();await ui.submit();assert.deepEqual(ui.rows('대상 시스템 목록').map(row=>row.outerHTML),before);assert.equal(ui.dialog.open,true);
 if(state==='reference'){assert.equal(ui.dialog.querySelector('[data-mismatch-delete-action="execute"]').disabled,true);assert.match(ui.dialog.querySelector('[data-mismatch-delete-results]').textContent,/현재 사용 API|참조 해소/);}if(state==='preview')assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/미리보기/);if(state==='empty')assert.equal(ui.dialog.querySelector('[data-mismatch-delete-action="execute"]').disabled,true);if(state==='execute'){assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/삭제 요청 실패/);assert.ok(ui.dialog.querySelector('[data-mismatch-delete-reason]').value);}
 }finally{ui.dom.window.close();}}
});
test('미리보기 뒤 필터·부모·구성·대상·판정·버전·참조 변경은 낡음으로 전체 거부한다',async()=>{
 const cases=['filter','parent','configuration','target','diagnosis','version','reference'];
 for(const type of cases){const ui=await mount();try{
 const detail=type==='parent';await ui.open(detail?1:0);const before=ui.rows(detail?'연결 정보':'대상 시스템 목록').length;
 if(type==='filter')await ui.input(ui.root.querySelector('input[aria-label="시스템명"]'),'바뀐 조건');else if(type==='parent')await ui.click(ui.rows('대상 시스템 목록')[1]);else ui.mutate(config=>{if(type==='configuration')config.scopes[0].configurationVersion='cfg-2';if(type==='target')config.scopes[0].records[1].id='SYS-CHANGED';if(type==='diagnosis')config.scopes[0].diagnosisVersion='diag-2';if(type==='version')config.scopes[0].records[1].version='3';if(type==='reference')config.scopes[0].records[1].references=['새 참조'];});
 await ui.submit();assert.equal(ui.rows(detail?'연결 정보':'대상 시스템 목록').length,before);assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/낡았습니다/);
 }finally{ui.dom.window.close();}}
});
test('미저장 입력·저장 중·결과 불명은 시작을 막고 조회전용·권한없음은 잠근다',async()=>{
 for(const state of ['dirty','saving','unknown']){const ui=await mount();try{
 if(state==='dirty'){const cell=ui.cell(ui.rows('대상 시스템 목록')[0],'시스템명'),input=cell.querySelector('[data-edit-value]');input.closest('details').open=true;input.value='미저장';input.dispatchEvent(new ui.window.FocusEvent('focusout',{bubbles:true,relatedTarget:ui.doc.body}));await tick();}else ui.marker.dataset.mismatchDeleteState=state;
 await ui.open(1);assert.equal(ui.dialog.open,false);assert.match(ui.root.querySelector('[data-mismatch-delete-notice]').textContent,/미저장|저장 중|결과 불명/);
 }finally{ui.dom.window.close();}}
 for(const permission of [true,false]){const model=fixture();model.mismatchDeleteWorkspace.scopes[0].permission=permission;const ui=await mount({model,editable:!permission});try{assert.equal(ui.root.querySelector('[data-mismatch-delete-open="0"]').disabled,true);await ui.open();assert.equal(ui.dialog.open,false);}finally{ui.dom.window.close();}}
});
test('조회조건 입력 누락은 범위를 넓히지 않고 필터 원복도 낡은 미리보기를 재사용하지 않는다',async()=>{
 const ui=await mount();try{
 const filter=ui.root.querySelector('input[aria-label="시스템명"]');await ui.open();await ui.input(filter,'변경');await ui.input(filter,'');await ui.submit();assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/낡았습니다/);await ui.click(ui.dialog.querySelector('[data-mismatch-delete-action="cancel"]'));filter.remove();await ui.open();assert.match(ui.dialog.querySelector('[data-mismatch-delete-status]').textContent,/조회조건 입력을 확인할 수 없습니다/);assert.equal(ui.dialog.querySelector('[data-mismatch-delete-action="execute"]').disabled,true);
 }finally{ui.dom.window.close();}
});
test('마지막 시스템 삭제는 선택·하단을 비우며 다른 이력은 유지한다',async()=>{
 const model=fixture();model.rowExamples.rows=model.rowExamples.rows.filter(([area,json])=>area==='대상 시스템 목록'?JSON.parse(json)['시스템 식별자']==='SYS-LEGACY':JSON.parse(json).문맥==='SYS-LEGACY');model.mismatchDeleteWorkspace.scopes[0].records=model.mismatchDeleteWorkspace.scopes[0].records.filter(record=>record.id==='SYS-LEGACY');const ui=await mount({model});try{
 await ui.open();await ui.submit();assert.equal(ui.rows('대상 시스템 목록').length,0);assert.equal(ui.rows('연결 정보').length,0);assert.equal(ui.rows('사용 API 정보').length,0);assert.equal(ui.root.querySelector('.screen').dataset.activeMaster,'');assert.equal(ui.root.querySelector('[data-grid-role="master"] tr.sel'),null);assert.equal(ui.grid('연결 정보').querySelector('.cnt').textContent,'0건');
 }finally{ui.dom.window.close();}
});
