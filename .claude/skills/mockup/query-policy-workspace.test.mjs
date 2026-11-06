import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import vm from 'node:vm';import {createRequire} from 'node:module';
import {queryPolicyWorkspace} from './query-policy-workspace.mjs';import {declaredScreen,searchComboScript} from './declared-layout.mjs';
const requireFrontend=createRequire(new URL('../../../sources/frontend/package.json',import.meta.url)),{JSDOM}=requireFrontend('jsdom');
const source=readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-query-policy.js',import.meta.url),'utf8'),ctx=vm.createContext({});vm.runInContext(source,ctx);const json=value=>JSON.parse(JSON.stringify(value));
const config={scopeList:'스코프 설정 / 목록',scopeForm:'스코프 설정 / 조건식',parameters:'스코프 설정 / 파라미터',constraints:'필수 조건 제약',filters:'기본 필터 설정',master:'규칙 목록',catalogKey:'카탈로그 식별자',columns:[{'카탈로그 식별자':'901','컬럼명':'order_date','자료형':'날짜'},{'카탈로그 식별자':'901','컬럼명':'order_no','자료형':'문자'},{'카탈로그 식별자':'901','컬럼명':'amount','자료형':'숫자'},{'카탈로그 식별자':'902','컬럼명':'other','자료형':'문자'}]};
const table=(heading,rows)=>({heading,header:['컬럼','입력방식','편집','검증','예시'],rows});
function fixture(){return {
 interactive:true,masterLink:'규칙 ID',vertical:true,queryPolicyWorkspace:structuredClone(config),search:[],
 columns:[table('규칙 목록',[['규칙 ID','조회','읽기전용','','101'],['카탈로그 식별자','조회','읽기전용','','901'],['카탈로그명','조회','읽기전용','','주문'],['데이터소스','조회','읽기전용','','ERP'],['설명','텍스트','가능','','예시']]),table(config.scopeList,[['스코프명','텍스트','가능','','기본'],['사용 여부','설정 스위치','가능','','true'],['해제 허용','설정 스위치','가능','','false']]),table(config.scopeForm+' — 폼',[['조건식','장문','가능','','']]),table(config.parameters,[['파라미터명','조회','읽기전용','','x'],['값 출처','조회','읽기전용','','LLM'],['값 설명','조회','읽기전용','','수량']]),table(config.filters,[['필터명','텍스트','가능','','필터'],['사용 여부','설정 스위치','가능','','true'],['해제 허용','설정 스위치','가능','','false']]),table(config.constraints,[['컬럼명','선택','가능','','order_date'],['자료형','조회','읽기전용','','날짜'],['조건 유형','선택','가능','','범위'],['최대 차이','숫자','가능','','30'],['사용 여부','설정 스위치','가능','','true'],['해제 허용','설정 스위치','가능','','false']])],
 actions:['조회','저장','추가','파라미터 추출'].map(name=>[name,'','','','',name]),
 surfaces:[{name:'규칙 목록',buttons:['조회','추가','저장']},{name:config.scopeList,buttons:['추가']},{name:config.scopeForm,buttons:['파라미터 추출']},{name:config.parameters,buttons:[]},{name:config.filters,buttons:['추가']},{name:config.constraints,buttons:['추가']}],
 addRowTabs:['규칙 목록',config.scopeList,config.constraints,config.filters],
 addChoices:{header:['추가 방식','방식','원천','상위 필드','고정 필드','선택 키','활성 필드','조회 조건','표시 컬럼','후보 예시(JSON)','기본값(JSON)','참조 표시'],rows:[['규칙 목록','조회 팝업','카탈로그','없음','없음','카탈로그 식별자','없음','카탈로그명','카탈로그 식별자·카탈로그명·데이터소스',JSON.stringify([{'카탈로그 식별자':'902','카탈로그명':'재고','데이터소스':'WMS'}]),'{}','카탈로그명·데이터소스']]},
 rowExamples:{rows:[['규칙 목록',JSON.stringify({'규칙 ID':'101','카탈로그 식별자':'901','카탈로그명':'주문','데이터소스':'ERP'})],['규칙 목록',JSON.stringify({'규칙 ID':'102','카탈로그 식별자':'901','카탈로그명':'다른 정책','데이터소스':'ERP'})],
 [config.scopeList,JSON.stringify({'문맥':'101','스코프명':'첫째','사용 여부':true,'해제 허용':false,'조건식':'x=#{x} AND uid=#{userId}','파라미터':[{'파라미터명':'x','값 설명':'원래 설명'}]})],[config.scopeList,JSON.stringify({'문맥':'101','스코프명':'둘째','사용 여부':true,'해제 허용':false,'조건식':'y=#{y}'})],[config.scopeList,JSON.stringify({'문맥':'102','스코프명':'다른 부모','사용 여부':true,'해제 허용':false,'조건식':'z=#{z}'})],
 [config.constraints,JSON.stringify({'문맥':'101','컬럼명':'order_date','자료형':'날짜','조건 유형':'범위','최대 차이':30,'사용 여부':true,'해제 허용':false})],
 [config.filters,JSON.stringify({'문맥':'101','필터명':'기본 필터','사용 여부':true,'해제 허용':false})]]}
};}
const figure={key:'main',caption:'기본',detail:'스코프 설정',notice:'검토',buttons:['조회','추가','저장','파라미터 추출'],editable:true,examples:{}};
function render(model,editable=true){return declaredScreen('조회 정책',model,{...figure,editable},{plain:value=>String(value??''),searchControl:()=>'',manualKey:false,annotator:()=>({badge:()=>'',render:()=>''})});}
const tick=async()=>{await Promise.resolve();await Promise.resolve();await Promise.resolve();};
async function mount({editable=true,shadow=false,model=fixture()}={}){
 const dom=new JSDOM('<!doctype html><html><body></body></html>',{url:'https://example.test/',runScripts:'outside-only'}),window=dom.window,document=window.document;
 window.HTMLDialogElement.prototype.showModal=function(){this.open=true;};window.HTMLDialogElement.prototype.close=function(){this.open=false;};
 let root=document;if(shadow){const host=document.createElement('div');document.body.append(host);root=host.attachShadow({mode:'open'});root.innerHTML=render(model,editable);}else document.body.innerHTML=render(model,editable);
 const script=searchComboScript().replace(/^<script>|<\/script>$/g,'').split('bindDeclaredMockup(document);')[0];window.eval(script);window.bindDeclaredMockup(root);await tick();
 const grid=name=>[...root.querySelectorAll('[data-grid-name]')].find(grid=>grid.dataset.gridName===name),rows=name=>[...grid(name).querySelectorAll('tbody tr[data-grid-row-state]')],visible=name=>rows(name).filter(row=>!row.hidden&&row.dataset.gridRowState!=='D');
 const field=(row,key)=>[...row.querySelectorAll('[data-row-field]')].find(cell=>cell.dataset.rowField===key),click=async node=>{node.click();await tick();};
 const edit=async(target,text)=>{const input=target.querySelector('[data-edit-value]'),details=input.closest('details');details.open=true;input.value=text;input.dispatchEvent(new window.Event('input',{bubbles:true}));input.dispatchEvent(new window.FocusEvent('focusout',{bubbles:true,relatedTarget:document.body}));await tick();};
 return {dom,window,root,grid,rows,visible,field,click,edit};
}
test('설정 JSON은 명시 영역·컬럼 후보를 검사한다',()=>{
 const model=fixture(),t={rows:[['설정',JSON.stringify(config)]]};assert.deepEqual(queryPolicyWorkspace(t,model),config);assert.equal(queryPolicyWorkspace(null,model),null);
 assert.throws(()=>queryPolicyWorkspace({rows:[['설정',JSON.stringify({...config,master:'없음'})]]},model),/영역/);
 assert.throws(()=>queryPolicyWorkspace({rows:[['설정',JSON.stringify({...config,columns:[...config.columns,config.columns[0]]})]]},model),/중복/);
});
test('주석·문자열·식별자·dollar quote의 토큰을 무시하고 이름을 중복 제거한다',()=>{
 const sql="a=#{x} AND b=#{x} AND c=#{userId} -- #{ignored}\n/* #{bad} /* #{nested} */ */ '\\'#{str}' \"#{identifier}\" `#{tick}` [#{bracket}] $$#{dollar}$$ $tag$#{tagged}$tag$ AND d=#{langCd}";
 assert.deepEqual(json(ctx.mockupPolicyParameterNames(sql)),['x','userId','langCd']);
 const previous=[{'파라미터명':'x','값 설명':'보존'},{'파라미터명':'gone','값 설명':'제거'},{'파라미터명':'userId','값 출처':'LLM','값 설명':'악성 덮기'}];
 const params=json(ctx.mockupPolicyParameters(sql,previous));assert.equal(params[0]['값 설명'],'보존');assert.equal(params[1]['값 출처'],'세션');assert.match(params[1]['값 설명'],/서버 신원 매핑/);assert.ok(!params.some(param=>param['파라미터명']==='gone'));
});
test('자료형·카탈로그 소속과 범위의 최대 차이를 검증하며 동일값 전환은 값을 비운다',()=>{
 const columns=json(ctx.mockupPolicyColumns(config,'901'));assert.equal(columns.length,3);assert.equal(ctx.mockupPolicyColumns(config,'999').length,0);
 const check=(name,kind,max)=>json(ctx.mockupPolicyConstraint({'컬럼명':name,'조건 유형':kind,'최대 차이':max},columns));
 assert.deepEqual(check('order_no','범위','30').allowed,['동일 값 매칭']);assert.equal(check('order_no','범위','30').values['조건 유형'],'');assert.equal(check('order_no','범위','30').values['최대 차이'],'');
 for(const max of ['','-1','1.5','NaN'])assert.ok(check('order_date','범위',max).error);assert.equal(check('order_date','범위','0').error,'');assert.equal(check('amount','범위','1.5').error,'');assert.equal(check('order_date','동일 값 매칭','30').values['최대 차이'],'');
 assert.deepEqual(check('missing','범위','30').allowed,['동일 값 매칭']);
});
test('문자형 범위 허용으로 업무 규칙을 뒤집으면 반례가 실패한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace("range=type==='날짜'||type==='숫자'","range=true"),mutant);
 assert.throws(()=>assert.deepEqual(json(mutant.mockupPolicyConstraint({'컬럼명':'order_no','조건 유형':'범위'},config.columns)).allowed,['동일 값 매칭']));
});
for(const shadow of [false,true])test(`스코프·부모·탭 왕복과 추출·한 행 원복을 보존한다 (${shadow?'최종 HTML ShadowRoot':'독립 문서'})`,async()=>{
 const ui=await mount({shadow});try{
 const {root,grid,visible,field,click,window}=ui,expression=root.querySelector('[data-form-value="조건식"]');assert.equal(root.querySelectorAll('[data-detail-tab]').length,3);assert.equal(expression.value,'x=#{x} AND uid=#{userId}',JSON.stringify({connected:ui.rows(config.scopeList).map(row=>row.isConnected),hidden:ui.rows(config.scopeList).map(row=>row.hidden),active:root.querySelector('.screen').dataset.activeMaster,master:ui.rows(config.master).map(row=>row.className),status:root.querySelector('[data-policy-status]').textContent}));
 const first=visible(config.scopeList)[0],second=visible(config.scopeList)[1];expression.value='x=#{x} AND n=#{newName} AND uid=#{userId}';expression.dispatchEvent(new window.Event('input',{bubbles:true}));assert.match(root.querySelector('[data-policy-status]').textContent,/낡았습니다/);
 await click(second);assert.equal(expression.value,'y=#{y}');await click(first);assert.match(expression.value,/#{newName}/);
 await click(root.querySelector('[data-detail-tab="기본 필터 설정"]'));await click(root.querySelector('[data-detail-tab="스코프 설정"]'));assert.match(expression.value,/#{newName}/);
 await click(root.querySelector('[data-demo-action="파라미터 추출"]'));assert.equal(grid(config.parameters).querySelector('[data-policy-parameter="x"]').value,'원래 설명');assert.equal(grid(config.parameters).querySelector('[data-policy-parameter="userId"]'),null);assert.match(grid(config.parameters).textContent,/세션/);
 const description=grid(config.parameters).querySelector('[data-policy-parameter="newName"]');description.value='신규 설명';description.dispatchEvent(new window.Event('input',{bubbles:true}));
 await click(ui.rows(config.master)[1]);assert.equal(expression.value,'z=#{z}');await click(ui.rows(config.master)[0]);assert.match(expression.value,/#{newName}/);assert.equal(grid(config.parameters).querySelector('[data-policy-parameter="newName"]').value,'신규 설명');
 await click(first.querySelector('[data-revert-row]'));assert.equal(expression.value,'x=#{x} AND uid=#{userId}');assert.equal(grid(config.parameters).querySelector('[data-policy-parameter="newName"]'),null);
 }finally{ui.dom.window.close();}
});
test('카탈로그 선택 신규 규칙은 미발급 ID와 기존 하단을 분리하고 직접 추가 스위치를 보존한다',async()=>{
 const ui=await mount();try{
 const {root,grid,rows,visible,field,click}=ui;await click(grid(config.master).querySelector('[data-grid-add]'));const dialog=grid(config.master).querySelector('dialog');await click(dialog.querySelector('[data-lookup-search]'));await click(dialog.querySelector('[data-lookup-choice]'));await click(dialog.querySelector('[data-lookup-apply]'));
 const created=rows(config.master)[0];assert.equal(field(created,'규칙 ID').dataset.rowValue,'');assert.equal(field(created,'카탈로그 식별자').dataset.rowValue,'902');assert.equal(field(created,'카탈로그명').textContent,'재고');assert.match(created.dataset.masterClientKey,/LOCAL/);assert.equal(visible(config.scopeList).length,0);assert.equal(visible(config.constraints).length,0);
 await click(grid(config.scopeList).querySelector('[data-grid-add]'));const scope=visible(config.scopeList)[0];assert.equal(field(scope,'사용 여부').dataset.rowValue,'false');await click(field(scope,'사용 여부').querySelector('[data-config-switch]'));assert.equal(field(scope,'사용 여부').dataset.rowValue,'true');
 await click(grid(config.constraints).querySelector('[data-grid-add]'));const constraint=visible(config.constraints)[0];assert.deepEqual([...field(constraint,'컬럼명').querySelector('select').options].map(option=>option.value),['','other']);assert.equal(field(constraint,'해제 허용').dataset.rowValue,'false');
 await click(rows(config.master)[1]);assert.equal(visible(config.scopeList)[0].querySelector('[data-row-field="스코프명"]').dataset.rowValue,'첫째');await click(created);assert.equal(visible(config.scopeList)[0],scope);
 }finally{ui.dom.window.close();}
});
test('제약 컬럼 변경은 읽기 전용 자료형·유형 후보·최대 차이를 갱신한다',async()=>{
 const ui=await mount();try{
 const row=ui.visible(config.constraints)[0];await ui.edit(ui.field(row,'컬럼명'),'order_no');assert.equal(ui.field(row,'자료형').dataset.rowValue,'문자');assert.equal(ui.field(row,'조건 유형').dataset.rowValue,'');assert.equal(ui.field(row,'최대 차이').dataset.rowValue,'');assert.deepEqual([...ui.field(row,'조건 유형').querySelector('select').options].map(option=>option.value),['','동일 값 매칭']);
 await ui.edit(ui.field(row,'컬럼명'),'order_date');await ui.edit(ui.field(row,'조건 유형'),'범위');const input=ui.field(row,'최대 차이').querySelector('input');assert.equal(input.required,true);assert.equal(input.min,'0');assert.equal(input.step,'1');input.value='1.5';assert.equal(input.checkValidity(),false);
 }finally{ui.dom.window.close();}
});
test('스코프 추가·삭제는 다른 행 입력을 유지하고 추출 낡음·빈 최대 차이 저장을 막는다',async()=>{
 const ui=await mount();try{
 const expression=ui.root.querySelector('[data-form-value="조건식"]'),first=ui.visible(config.scopeList)[0];expression.value='x=#{x} AND changed=#{changed}';expression.dispatchEvent(new ui.window.Event('input',{bubbles:true}));
 await ui.click(ui.grid(config.master).querySelector('[data-grid-save]'));assert.match(ui.grid(config.master).querySelector('.pg').textContent,/다시 추출/);
 await ui.click(ui.grid(config.scopeList).querySelector('[data-grid-add]'));const created=ui.visible(config.scopeList).find(row=>row.dataset.gridRowState==='C');await ui.click(created);expression.value='new=#{newParam}';expression.dispatchEvent(new ui.window.Event('input',{bubbles:true}));await ui.click(created.querySelector('[data-grid-delete]'));await ui.click(first);assert.match(expression.value,/#{changed}/);
 await ui.click(ui.root.querySelector('[data-demo-action="파라미터 추출"]'));const constraint=ui.visible(config.constraints)[0];await ui.edit(ui.field(constraint,'조건 유형'),'동일 값 매칭');await ui.edit(ui.field(constraint,'조건 유형'),'범위');
 await ui.click(ui.grid(config.master).querySelector('[data-grid-save]'));assert.match(ui.grid(config.master).querySelector('.pg').textContent,/최대 차이/);
 }finally{ui.dom.window.close();}
});
test('조회 전용에서 조건식·파라미터·추가·스위치를 잠근다',async()=>{
 const ui=await mount({editable:false});try{
 assert.equal(ui.root.querySelector('[data-form-value="조건식"]').readOnly,true);assert.equal(ui.root.querySelector('[data-demo-action="파라미터 추출"]').disabled,true);assert.equal(ui.grid(config.parameters).querySelector('[data-policy-parameter]'),null);assert.equal(ui.grid(config.constraints).querySelector('[data-cell-editor]'),null);
 for(const button of ui.root.querySelectorAll('[data-grid-add],[data-config-switch]'))assert.equal(button.disabled,true);
 }finally{ui.dom.window.close();}
});
test('다중 체크 선택의 취소·조회 실패·검증 실패는 행을 보존하고 성공은 전체를 추가한다',async()=>{
 const model=fixture(),choice=model.addChoices;choice.header.push('선택 방식');choice.rows[0].push('다중');choice.rows[0][9]=JSON.stringify([{'카탈로그 식별자':'902','카탈로그명':'재고','데이터소스':'WMS'},{'카탈로그 식별자':'903','카탈로그명':'결제','데이터소스':'ERP'}]);
 const ui=await mount({model});try{
 const grid=ui.grid(config.master),dialog=grid.querySelector('dialog'),add=grid.querySelector('[data-grid-add]'),apply=dialog.querySelector('[data-lookup-apply]'),search=dialog.querySelector('[data-lookup-search]');const initial=ui.rows(config.master).length;
 await ui.click(add);await ui.click(search);assert.ok([...dialog.querySelectorAll('[data-lookup-choice]')].every(input=>input.type==='checkbox'));await ui.click(dialog.querySelector('[data-lookup-choice]'));await ui.click(dialog.querySelector('[data-lookup-close]'));assert.equal(ui.rows(config.master).length,initial);
 await ui.click(add);const rule=JSON.parse(grid.dataset.addChoice);grid.dataset.addChoice=JSON.stringify({...rule,state:'실패'});await ui.click(search);assert.match(dialog.querySelector('[data-lookup-status]').textContent,/조회 실패/);assert.equal(ui.rows(config.master).length,initial);
 grid.dataset.addChoice=JSON.stringify(rule);await ui.click(search);for(const input of dialog.querySelectorAll('[data-lookup-choice]'))await ui.click(input);assert.equal(apply.disabled,false);await ui.click(dialog.querySelector('[data-lookup-choice]'));assert.equal(apply.disabled,false);await ui.click(dialog.querySelector('[data-lookup-choice]'));
 const changed=structuredClone(rule);changed.records[1]['선택 가능']=false;grid.dataset.addChoice=JSON.stringify(changed);await ui.click(apply);assert.match(dialog.querySelector('[data-lookup-status]').textContent,/선택 가능한/);assert.equal(ui.rows(config.master).length,initial);
 grid.dataset.addChoice=JSON.stringify(rule);await ui.click(apply);assert.equal(ui.rows(config.master).length,initial+2);const created=ui.rows(config.master).filter(row=>row.dataset.gridRowState==='C');assert.equal(new Set(created.map(row=>row.dataset.masterClientKey)).size,2);assert.ok(created.every(row=>ui.field(row,'규칙 ID').dataset.rowValue===''));
 await ui.click(add);await ui.click(search);assert.ok([...dialog.querySelectorAll('[data-lookup-choice]')].every(input=>input.disabled));assert.equal(apply.disabled,true);
 }finally{ui.dom.window.close();}
});
test('팝업 열린 뒤 부모나 권한이 바뀌면 다중 추가 전체를 거부한다',async()=>{
 const model=fixture(),target=model.columns.find(table=>table.heading===config.filters);target.rows.unshift(['규칙 ID','조회','읽기전용','','101']);model.addChoices.header.push('선택 방식');model.addChoices.rows[0].push('단일');model.addChoices.rows.push([config.filters,'조회 팝업','필터 후보','규칙 ID','규칙 ID','필터명','없음','필터명','필터명','[{"필터명":"추가A"},{"필터명":"추가B"}]','{}','—','다중']);
 const ui=await mount({model});try{
 const grid=ui.grid(config.filters),dialog=grid.querySelector('dialog'),add=grid.querySelector('[data-grid-add]'),initial=ui.rows(config.filters).length;
 await ui.click(add);await ui.click(dialog.querySelector('[data-lookup-search]'));for(const input of dialog.querySelectorAll('[data-lookup-choice]'))await ui.click(input);await ui.click(ui.rows(config.master)[1]);await ui.click(dialog.querySelector('[data-lookup-apply]'));assert.match(dialog.querySelector('[data-lookup-status]').textContent,/상위 대상이 변경/);assert.equal(ui.rows(config.filters).length,initial);
 await ui.click(dialog.querySelector('[data-lookup-close]'));await ui.click(add);await ui.click(dialog.querySelector('[data-lookup-search]'));for(const input of dialog.querySelectorAll('[data-lookup-choice]'))await ui.click(input);add.disabled=true;await ui.click(dialog.querySelector('[data-lookup-apply]'));assert.match(dialog.querySelector('[data-lookup-status]').textContent,/추가 권한/);assert.equal(ui.rows(config.filters).length,initial);
 }finally{ui.dom.window.close();}
});

test('스코프 상세는 오른쪽 조건식 아래 파라미터와 추출 버튼을 둔다',async()=>{const ui=await mount();try{const editor=ui.root.querySelector('[data-policy-editor]');assert.ok(editor.querySelector('[data-form-value="조건식"]'));assert.ok(editor.querySelector('[data-grid-name="스코프 설정 / 파라미터"]'));assert.equal(editor.querySelectorAll('[data-demo-action="파라미터 추출"]').length,1);await ui.click(ui.rows(config.scopeList)[0]);assert.equal(ui.grid(config.parameters).querySelector('.cnt').textContent,'2건');for(const row of editor.querySelectorAll('[data-grid-name="스코프 설정 / 파라미터"] tbody tr'))assert.equal(row.hidden,false);}finally{ui.dom.window.close();}});

test('부모 식별자가 업무 컬럼에 없어도 같은 문맥의 기존 매핑 중복을 차단한다',async()=>{
 const model=fixture();model.addChoices.rows.push([config.filters,'조회 팝업','필터 후보','규칙 ID','규칙 ID','필터명','없음','필터명','필터명','[{"필터명":"기본 필터"},{"필터명":"추가"}]','{}','—']);
 const ui=await mount({model});try{const grid=ui.grid(config.filters),dialog=grid.querySelector('dialog');assert.equal(grid.querySelector('[data-row-field="규칙 ID"]'),null);await ui.click(grid.querySelector('[data-grid-add]'));await ui.click(dialog.querySelector('[data-lookup-search]'));const choices=[...dialog.querySelectorAll('[data-lookup-choice]')];assert.equal(choices[0].disabled,true);assert.equal(choices[1].disabled,false);}finally{ui.dom.window.close();}
});
