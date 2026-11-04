import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const source=readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-api.js',import.meta.url),'utf8');
const context=vm.createContext({});vm.runInContext(source,context);
const fields=s=>JSON.parse(JSON.stringify(context.mockupJsonFields(s)));
test('배열 모든 항목과 구조·자료형을 보존하고 원값은 남기지 않는다',()=>{
 const rows=fields('{"items":[{"id":"001","qty":2},{"id":"002","price":1.25}],"phone":"01012345678"}');
 assert.deepEqual(rows.map(x=>x.path),['items[]','items[].id','items[].qty','items[].price','phone']);
 assert.equal(rows[0].type,'array');assert.equal(rows[0].itemType,'object');assert.equal(rows[3].type,'number');
 assert.ok(!JSON.stringify(rows).includes('01012345678'));
});
test('특수 키·루트 배열·중첩 배열·스칼라의 경로를 구별한다',()=>{
 assert.deepEqual(fields('[{"a.b":{"x[]":1}}]').map(x=>x.path),['[]','[]["a.b"]','[]["a.b"]["x[]"]']);
 assert.deepEqual(fields('{"a":[[1,2]]}').map(x=>x.path),['a[]','a[][]']);
 assert.equal(fields('false')[0].path,'$');assert.equal(fields('9007199254740993')[0].type,'integer');
 assert.deepEqual(fields('{"$":{"id":1},"id":2}').map(x=>x.path),['["$"]','["$"].id','id']);
});
test('빈 배열·null·혼합 타입은 자동 정상 판정하지 않는다',()=>{
 assert.equal(fields('{"a":null}')[0].type,'확인 필요');assert.equal(fields('{"a":[]}')[0].itemType,'');
 assert.equal(fields('{"a":[1,"x"]}')[0].conflict,true);
 assert.equal(fields('[{"id":1},{"id":"x"}]')[1].conflict,true);
 assert.equal(fields('[{"v":1},{"v":1.5}]')[1].type,'number');
});
test('중복 키·깨진 JSON·여분 입력을 거부한다',()=>{
 for(const s of ['{"x":1,"x":2}','{"x":1,}','[1,]','01','{}true','{"a":"\\q"}','{\u00a0"a":1}',''])assert.throws(()=>fields(s));
});
test('배열 합집합 규칙을 반전하면 검증 사례가 실패한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace('for(const item of node.items)','for(const item of node.items.slice(0,1))'),mutant);
 assert.throws(()=>assert.deepEqual(JSON.parse(JSON.stringify(mutant.mockupJsonFields('[{"a":1},{"b":2}]'))).map(x=>x.path),['[]','[].a','[].b']));
});
test('의미 코드 문자열의 앞자리 0과 숫자·불리언 타입을 구분한다',()=>{
 assert.equal(context.mockupCodeValue('001','string'),'001');
 assert.equal(context.mockupCodeValue('1','integer'),1);
 assert.equal(context.mockupCodeValue('false','boolean'),false);
 for(const [value,type] of [['001','integer'],['1.2','integer'],['TRUE','boolean'],['1e999','number'],['{}','object']])assert.throws(()=>context.mockupCodeValue(value,type));
});
test('코드 문자열을 숫자로 변환하는 반례는 검증에 실패한다',()=>{
 const mutant=vm.createContext({});vm.runInContext(source.replace("if(type==='string')return text;","if(type==='string')return Number(text);"),mutant);
 assert.throws(()=>assert.equal(mutant.mockupCodeValue('001','string'),'001'));
});
