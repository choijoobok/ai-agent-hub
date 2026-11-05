import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import vm from 'node:vm';
const asset=name=>readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/'+name,import.meta.url),'utf8');
const source=asset('mockup-schema.js'),ctx=vm.createContext({});vm.runInContext(asset('mockup-api.js')+'\n'+source,ctx);
const plain=v=>JSON.parse(JSON.stringify(v));const fields=v=>plain(ctx.mockupSchemaFields(v));
test('원본 properties/items/required/null과 특수 경로를 정확하게 도출한다',()=>{
 const s={type:'object',properties:{items:{type:'array',items:{type:['object','null'],properties:{'a.b':{type:['string','null']}},required:['a.b']}},count:{type:'integer'}},required:['items']};
 const r=fields(s);assert.deepEqual(r.map(x=>x['필드 경로']),['items[]','items[]["a.b"]','count']);assert.equal(r[0]['필수 여부'],'필수');assert.equal(r[1]['NULL 허용'],'허용');assert.equal(r[2]['필수 여부'],'선택');
 assert.deepEqual(fields({type:'array',items:{type:'array',items:{type:'integer'}}}).map(x=>x['필드 경로']),['[]','[][]']);assert.equal(fields({type:'string'})[0]['필드 경로'],'$');
});
test('빈 명세와 오류/미지원 구조를 구분한다',()=>{
 assert.deepEqual(fields(null),[]);assert.deepEqual(fields({type:'object',properties:{}}),[]);
 for(const s of ['', '{', '{"type":"string","type":"number"}', {type:'array'}, {$ref:'https://example.invalid/schema'}, {type:'object',required:['missing']}, {type:['string','number']}, {type:'object',properties:[]}, {type:'array',items:{oneOf:[]}}])assert.throws(()=>fields(s));
});
test('삭제된 경로는 설정/ID를 보존하고 재등장하면 같은 ID를 쓴다',()=>{
 const old=[{'필드 경로':'old','자료형':'string',fieldId:'F1','설명':'설정 보존','스키마 일치 여부':true}];
 const r=plain(ctx.mockupSchemaSync(old,[{'필드 경로':'new','자료형':'string'}]));assert.equal(r[1].fieldId,'F1');assert.equal(r[1]['설명'],'설정 보존');assert.equal(r[1]['스키마 일치 여부'],false);assert.equal(r[0].fieldId,undefined);
 const again=plain(ctx.mockupSchemaSync(r,[{'필드 경로':'old','자료형':'string'}]));assert.equal(again[0].fieldId,'F1');assert.equal(again[0]['스키마 일치 여부'],true);
});
test('같은 경로 형 변경은 삭제 대상이 아닌 재검토다',()=>{
 const r=plain(ctx.mockupSchemaSync([{'필드 경로':'x','자료형':'string','설명':'보존'}],[{'필드 경로':'x','자료형':'integer'}]));assert.equal(r[0]['스키마 일치 여부'],true);assert.match(r[0]['동기화 안내'],/재확인/);assert.equal(r[0]['설명'],'보존');
});
test('붙여넣기는 호환 보충 설정만 복사하며 구조/판정/ID를 보호한다',()=>{
 const source={'필드 경로':'old','자료형':'string',fieldId:'OLD','스키마 일치 여부':false,'설명':'가이드','NULL 허용':'허용'};
 const target={'필드 경로':'new','자료형':'string',fieldId:'NEW','스키마 일치 여부':true,'설명':'기존','NULL 허용':'불가'};
 assert.deepEqual(plain(ctx.mockupSchemaCopy(source,target)),{'설명':'가이드'});assert.throws(()=>ctx.mockupSchemaCopy(source,{...target,'자료형':'integer'}));
 const lookup={...source,'값 출처':'API 조회','조회 API':'201',lookupResponseFieldId:'EXTERNAL-FIELD',lookupRevisionId:'OLD-REV'};
 assert.deepEqual(plain(ctx.mockupSchemaCopy(lookup,{...target,'조회 API':'','값 출처':'사용자 입력'})),{'설명':'가이드','조회 API':'201','값 출처':'API 조회'});
});
test('샘플에서 구조만 추출하고 필수와 실제 값은 저장 원본에 추정하지 않는다',()=>{
 const s=plain(ctx.mockupSchemaFromSample('{"rows":[{"id":"001","name":"홍길동"},{"quantity":2}]}'));assert.equal(s.properties.rows.items.properties.id.type,'string');assert.equal(s.properties.rows.items.properties.quantity.type,'integer');assert.ok(!JSON.stringify(s).includes('홍길동'));assert.equal(s.required,undefined);assert.throws(()=>ctx.mockupSchemaFromSample('[1,"x"]'));
});
test('사라진 필드 자동 제거·판정 뒤집기의 반례를 검출한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace("'스키마 일치 여부':false","'스키마 일치 여부':true"),mutant);
 assert.throws(()=>assert.equal(mutant.mockupSchemaSync([{'필드 경로':'old','자료형':'string'}],[])[0]['스키마 일치 여부'],false));
});
