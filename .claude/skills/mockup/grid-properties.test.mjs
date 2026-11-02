import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {withGridProperties,gridPropertyIssues,canEditRow,gridWritable,GRID_PROPERTIES,GRID_TYPES,GRID_EDITORS,GRID_RENDERERS} from './grid-properties.mjs';
import {columnExplanation} from './presentation.mjs';
const table={heading:'목록',header:['컬럼','필드명','입력방식','편집','필수','예시'],rows:[['업무 키','businessKey','텍스트','신규행만','저장 시','KEY'],['건수','count','숫자','읽기전용','입력 대상 아님','3'],['설명','description','장문 셀','가능','선택','설명']]};
test('신규/조회 편집과 필수·읽기/편집 축 분리',()=>{
 const t=withGridProperties(table);assert.equal(canEditRow(t,t.rows[0],true),true);assert.equal(canEditRow(t,t.rows[0],false),false);
 assert.equal(canEditRow(t,t.rows[1],true),false);assert.equal(canEditRow(t,t.rows[2],false),true);
 assert.equal(canEditRow(table,table.rows[0],false),false);assert.deepEqual(gridPropertyIssues(t),[]);
 assert.match(columnExplanation(t,t.rows[0]),/신규 행 필수/);assert.match(columnExplanation(t,t.rows[0]),/생성·갱신 시점/);
 const formula=structuredClone(t);formula.rows[0][formula.header.indexOf('참조·계산식')]='`price * quantity \\|\\| 0; now<from; now>to`';
 assert.match(columnExplanation(formula,formula.rows[0]),/price \* quantity \|\| 0/);
 assert.match(columnExplanation(formula,formula.rows[0]),/now&lt;from; now&gt;to/);
 assert.equal(gridPropertyIssues(t,{explicit:true}).length,GRID_PROPERTIES.length);
 const locked=withGridProperties({...table,readonly:true});assert.ok(locked.rows.every(r=>!canEditRow(locked,r,true)&&!canEditRow(locked,r,false)));
});
test('미지원 enum·누락·잘못된 보조 속성을 거부',()=>{
 const t=withGridProperties(table);const at=k=>t.header.indexOf(k);const bad=structuredClone(t);bad.rows[0][at('그리드 타입')]='boolean';bad.rows[0][at('추가 속성')]='{bad}';bad.rows[0][at('값 원천')]='';
 assert.ok(gridPropertyIssues(bad).some(s=>s.includes('지원하지 않는')));assert.ok(gridPropertyIssues(bad).some(s=>s.includes('JSON 객체')));assert.ok(gridPropertyIssues(bad).some(s=>s.includes('값 원천 값 누락')));
});
test('분리 편집이 레거시 요약보다 우선하며 원본 누락을 기본값으로 숨기지 않는다',()=>{
 const t={heading:'신규 전용',header:['컬럼','편집','신규 편집','조회 편집'],rows:[['키','읽기전용','가능','불가']]};
 assert.equal(gridWritable(t),true);assert.equal(canEditRow(t,t.rows[0],true),true);assert.equal(canEditRow(t,t.rows[0],false),false);
 const full=withGridProperties(table),raw=structuredClone(full.rows);raw[0][full.header.indexOf('값 원천')]='';
 assert.ok(gridPropertyIssues({...full,declaredHeader:full.header,declaredRows:raw},{explicit:true}).some(s=>s.includes('값 원천 정본 값 누락')));
});
test('정본 그리드 소스와 type/editor/renderer 어휘 일치',()=>{
 const source=readFileSync(new URL('../../../sources/frontend/src/shared/grid/types.ts',import.meta.url),'utf8').replace(/\/\*[\s\S]*?\*\//g,'');
 const values=name=>[...source.match(new RegExp('export type '+name+'\\s*=([\\s\\S]*?);'))[1].matchAll(/'([^']+)'/g)].map(m=>m[1]);
 assert.deepEqual(values('ColumnType'),GRID_TYPES);assert.deepEqual(values('EditorType'),GRID_EDITORS);assert.deepEqual(values('RendererType'),GRID_RENDERERS);
});
