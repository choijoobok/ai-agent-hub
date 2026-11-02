import{test}from'node:test';import assert from'node:assert/strict';import fs from'node:fs';import vm from'node:vm';
import{relationWorkspace}from'./relation-workspace.mjs';
const runtime=vm.createContext({});vm.runInContext(fs.readFileSync(new URL('../../../aidd-guide/artifact/site-kit/assets/mockup-relation.js',import.meta.url),'utf8'),runtime);
const left={catalogId:'A',dataSourceKey:'DS'},right={catalogId:'B',dataSourceKey:'DS'};
const col=(catalogId,columnId,typeGroup='integer')=>({catalogId,columnId,typeGroup});
test('두 테이블은 한 소스에서 원자 선택하며 신규 왼쪽 소유·기존 소유를 구분한다',()=>{
 const resolve=runtime.resolveMockupRelationTables;assert.equal(resolve('DS',left,right).ownerCatalogId,'A');assert.equal(resolve('DS',left,right,'B',true).ownerCatalogId,'B');assert.equal(resolve('DS',left,left).rightCatalogId,'A');
 for(const args of[['DS',left,null],['DS',left,{...right,dataSourceKey:'OTHER'}],['DS',{...left,selectable:false},right],['DS',left,right,'C',true]])assert.throws(()=>resolve(...args));
});
test('매핑 추가는 현재 양끝점 컬럼을 요구하고 반쪽·중복·다른 테이블·미확인 타입을 거부한다',()=>{
 const resolve=runtime.resolveMockupRelationPair,relation={leftCatalogId:'A',rightCatalogId:'B'},a=col('A','a'),b=col('B','b');
 const pair=resolve(relation,a,b,[]);assert.equal(pair.order,1);assert.equal(pair.leftColumnId,'a');assert.equal(resolve(relation,col('A','a2'),b,[pair]).order,2);
 for(const [l,r,pairs]of[[a,null,[]],[col('C','a'),b,[]],[a,col('B','b','text'),[]],[{...a,typeGroup:''},b,[]],[a,b,[pair]]])assert.throws(()=>resolve(relation,l,r,pairs));
 assert.equal(resolve({leftCatalogId:'A',rightCatalogId:'A'},col('A','id'),col('A','parent'),[]).rightColumnId,'parent');
});
test('순서는 숫자형 양의 정수·중복 없음으로 정렬하며 행 드래그 지원을 가정하지 않는다',()=>{
 const resolve=runtime.resolveMockupRelationOrder;assert.equal(resolve([{order:'10'},{order:'2'}])[0].order,2);
 for(const pairs of[[{order:0}],[{order:-1}],[{order:1.2}],[{order:'x'}],[{order:1},{order:'1'}]])assert.throws(()=>resolve(pairs));
});
test('연결 지식은 활성 직접/완전한 예제 경로의 합집합에 양끝점이 모두 필요하다',()=>{
 const resolve=runtime.resolveMockupRelatedKnowledge,mixed={direct:[{catalogId:'A',active:true}],examples:[{active:true,complete:true,catalogIds:['B']}]};
 assert.equal(resolve(mixed,'A','B'),true);assert.equal(resolve(mixed,'A','A'),true);
 assert.equal(resolve({...mixed,direct:[{catalogId:'A',active:false}]},'A','B'),false);
 for(const patch of[{active:false},{complete:false}])assert.equal(resolve({...mixed,examples:[{...mixed.examples[0],...patch}]},'A','B'),false);
 assert.equal(resolve({examples:[{active:true,complete:true,catalogIds:['A']},{active:true,complete:true,catalogIds:['B']}]},'A','B'),true);
 assert.equal(resolve({...mixed,selectable:false},'A','B'),false);assert.equal(resolve(mixed,'A','C'),false);
});
test('관계 작업대의 누락 그리드/마스터 필드를 거부한다',()=>{
 assert.equal(relationWorkspace(null,{}),null);assert.throws(()=>relationWorkspace({rows:[]},{columns:[]}));
});
