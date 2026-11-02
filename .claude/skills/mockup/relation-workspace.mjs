/** 관계 작업대의 선언을 검사한다. 업무 결정은 화면 요건서가 갖는다. */
export function relationWorkspace(table,model) {
 if(!table)return null;
 const values=Object.fromEntries(table.rows.map(row=>[row[0].trim(),row[1].trim()]));
 const keys={source:'데이터소스 필드',left:'왼쪽 객체 필드',right:'오른쪽 객체 필드',leftTables:'왼쪽 후보 그리드',rightTables:'오른쪽 후보 그리드',leftColumns:'왼쪽 컬럼 그리드',pairs:'매핑 그리드',rightColumns:'오른쪽 컬럼 그리드',knowledge:'연결 지식 그리드'};
 const rule=Object.fromEntries(Object.entries(keys).map(([key,label])=>{if(!values[label])throw new Error('관계 작업대 선언 누락: '+label);return[key,values[label]];}));
 const title=t=>t.heading.replace(/^\d+(?:-\d+)?\.?\s*/,'');
 for(const key of['leftTables','rightTables','leftColumns','pairs','rightColumns','knowledge'])if(!model.columns.some(t=>title(t)===rule[key]))throw new Error('관계 작업대 그리드 없음: '+rule[key]);
 for(const key of['source','left','right'])if(!model.columns[0].rows.some(row=>row[0]===rule[key]))throw new Error('관계 작업대 마스터 필드 없음: '+rule[key]);
 rule.master=title(model.columns[0]);return rule;
}

export function relationPicker(rule,renderGrid,badge) {
 if(!rule)return '';
 return `<dialog class="command-dialog" data-relation-picker aria-label="관계 테이블 선택" style="width:min(1320px,calc(100vw - 32px))"><h3>관계 테이블 선택${badge('관계 테이블 선택','선택한 데이터소스의 카탈로그에서 좌우 테이블을 각각 한 개 선택한다. 자기조인도 양쪽 역할을 별도로 선택한다.','relation:picker')}</h3><p data-relation-source></p><div style="display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:16px" data-relation-table-grids>${[rule.leftTables,rule.rightTables].map((name,index)=>`<section><div class="sf" style="display:flex"><label>테이블 검색${badge('테이블 검색','객체명·업무명으로 같은 데이터소스의 현재 허용 후보를 조회한다.','relation:search')}<input type="search" data-relation-filter="${index}" aria-label="${index?'오른쪽':'왼쪽'} 테이블 검색"></label></div>${renderGrid(name)}</section>`).join('')}</div><label data-relation-reset-label hidden><input type="checkbox" data-relation-reset>테이블을 변경하고 현재 관계의 기존 컬럼 매핑을 초기화합니다.</label><p role="status" data-relation-status>양쪽 테이블을 선택하세요.</p><div class="row actions"><button type="button" class="btn" data-relation-apply disabled>적용${badge('적용','두 선택과 현재 데이터소스·참조를 검증한 뒤 같은 편집 행에 함께 반영한다. 부분 선택/취소는 반영하지 않는다.','relation:apply')}</button><button type="button" class="btn ghost" data-relation-close>닫기</button></div></dialog>`;
}
